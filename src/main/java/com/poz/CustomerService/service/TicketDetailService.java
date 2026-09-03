package com.poz.CustomerService.service;

import com.poz.CustomerService.dto.ticket.TicketCommentResponse;
import com.poz.CustomerService.dto.ticket.TicketDetailResponse;
import com.poz.CustomerService.entity.TicketComments;
import com.poz.CustomerService.entity.Tickets;
import com.poz.CustomerService.exception.ApiException;
import com.poz.CustomerService.repository.AgentsRepository;
import com.poz.CustomerService.repository.TicketCommentsRepository;
import com.poz.CustomerService.repository.TicketsRepository;
import com.poz.CustomerService.security.CurrentAgentProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工單詳情頁的 business logic：詳情、狀態變更、轉派、新增處理記錄。
 * <p>
 * 跟 {@link TicketService} 的分界是「<b>要先點進某一張工單才做得到的事</b>」。
 * 首頁列表與建單不必點進任何一張單，留在 {@link TicketService}。
 * <p>
 * 這裡同時碰 {@code tickets} 和 {@code ticket_comments} 兩張表，不是分層沒切乾淨：
 * 詳情頁上的每個動作（改狀態、轉派、留言）都是「改完工單就要補一筆處理記錄」，
 * 兩件事必須在同一個交易裡成立，拆開反而會讓交易邊界變得難看。
 * <p>
 * 三支 {@code write...} 開成 public，是給 {@link TicketService#create} 用的——
 * 建單當下也要寫 2～4 筆記錄，但建單並不需要點進詳情頁。
 * <p>
 * 方法一覽：
 * <ul>
 *   <li>{@link #detail} —— 工單全欄位 + 處理記錄 timeline</li>
 *   <li>{@link #changeStatus} —— 改狀態，過狀態機檢查，並補一筆系統記錄</li>
 *   <li>{@link #assign} —— 轉派給別的客服，並補一筆轉派記錄</li>
 *   <li>{@link #submitContent} —— 以登入身分新增一筆處理記錄</li>
 *   <li>{@link #writeComment} —— 寫一筆記錄，agentId 傳 null 就是系統事件</li>
 *   <li>{@link #writeAssignComment} —— 寫一筆轉派記錄</li>
 *   <li>{@link #writeStatusSetComment} —— 寫一筆「狀態設定為某某」</li>
 *   <li>{@code findTicket}（private）—— 用工單編號撈工單，查不到丟 404</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TicketDetailService {

    private final TicketsRepository ticketsRepository;
    private final TicketCommentsRepository ticketCommentsRepository;
    private final AgentsRepository agentsRepository;
    private final CurrentAgentProvider currentAgentProvider;

    // ------------------------------------------------------------------
    // 常數
    // ------------------------------------------------------------------

    /** 工單狀態白名單與對應的中文標籤，三個值必須與 {@code CK_tickets_status} 一致。 */
    private static final Map<String, String> STATUS_LABEL;
    static {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("IN_PROGRESS", "處理中");
        labels.put("PENDING", "待客戶回覆");
        labels.put("RESOLVED", "已解決");
        STATUS_LABEL = Collections.unmodifiableMap(labels);
    }

    /**
     * 狀態機：目前狀態 → 允許轉換成哪些狀態。
     * 每個 list 都不含自己，所以「改成跟現在一樣的狀態」也會被擋下來。
     */
    private static final Map<String, List<String>> TRANSITIONS;
    static {
        Map<String, List<String>> transitions = new LinkedHashMap<>();
        transitions.put("IN_PROGRESS", List.of("PENDING", "RESOLVED"));
        transitions.put("PENDING", List.of("IN_PROGRESS", "RESOLVED"));
        transitions.put("RESOLVED", List.of("IN_PROGRESS"));
        TRANSITIONS = Collections.unmodifiableMap(transitions);
    }

    // ------------------------------------------------------------------
    // 對外的方法
    // ------------------------------------------------------------------

    /**
     * 工單詳情：工單本身與處理記錄 timeline。
     *
     * @param ticketNo 工單編號，格式 TK-XXXXXX
     * @return 工單全欄位 + timeline
     * @throws ApiException 404 / {@code TICKET_NOT_FOUND}——查無此單號
     */
    @Transactional(readOnly = true)
    public TicketDetailResponse detail(String ticketNo) {
        Tickets ticket = findTicket(ticketNo);

        List<TicketComments> comments = ticketCommentsRepository
                .findByTicketIdOrderByCreatedAtAscCommentIdAsc(ticket.getTicketId());

        List<TicketCommentResponse> timeline = comments.stream()
                .map(TicketCommentResponse::from)
                .toList();

        return TicketDetailResponse.from(ticket, timeline);
    }

    /**
     * 變更工單狀態，並寫一筆系統處理記錄。
     *
     * @param ticketNo 工單編號，格式 TK-XXXXXX
     * @param status   要改成的新狀態，IN_PROGRESS / PENDING / RESOLVED
     * @return 改完的工單詳情（含剛寫入的狀態變更記錄）
     * @throws ApiException 404 / {@code TICKET_NOT_FOUND}——查無此單號；
     *                      400 / {@code INVALID_STATUS_TRANSITION}——不允許的狀態轉換
     */
    @Transactional
    public TicketDetailResponse changeStatus(String ticketNo, String status) {
        Tickets ticket = findTicket(ticketNo);
        String oldStatus = ticket.getStatus();

        if (!TRANSITIONS.getOrDefault(oldStatus, List.of()).contains(status)) {
            throw ApiException.badRequest("INVALID_STATUS_TRANSITION",
                    "無法從「" + STATUS_LABEL.getOrDefault(oldStatus, oldStatus)
                            + "」變更為「" + STATUS_LABEL.getOrDefault(status, status) + "」");
        }

        ticket.setStatus(status);
        writeComment(ticket, null, "狀態由「" + STATUS_LABEL.get(oldStatus)
                + "」變更為「" + STATUS_LABEL.get(status) + "」");
        return detail(ticketNo);
    }

    /**
     * 把工單轉派給別的客服，並寫一筆轉派記錄。
     *
     * @param ticketNo 工單編號，格式 TK-XXXXXX
     * @param assignID 要轉派給誰的客服代號
     * @return 改完的工單詳情
     * @throws ApiException 404 / {@code TICKET_NOT_FOUND}——查無此單號；
     *                      404 / {@code AGENT_NOT_FOUND}——客服代號不存在
     */
    @Transactional
    public TicketDetailResponse assign(String ticketNo, String assignID) {
        Tickets ticket = findTicket(ticketNo);
        if (!agentsRepository.existsById(assignID)) {
            throw ApiException.notFound("AGENT_NOT_FOUND", "找不到客服：" + assignID);
        }
        String me = currentAgentProvider.currentAgentId();
        ticket.setAssigneeId(assignID);
        writeAssignComment(ticket, me, assignID);
        return detail(ticketNo);
    }

    /**
     * 以目前登入的客服身分，對工單新增一筆處理記錄。
     *
     * @param ticketNo 工單編號，格式 TK-XXXXXX
     * @param content  留言內容
     * @return 改完的工單詳情
     * @throws ApiException 404 / {@code TICKET_NOT_FOUND}——查無此單號
     */
    @Transactional
    public TicketDetailResponse submitContent(String ticketNo, String content) {
        Tickets ticket = findTicket(ticketNo);
        String me = currentAgentProvider.currentAgentId();
        writeComment(ticket, me, content);
        return detail(ticketNo);
    }

    // ------------------------------------------------------------------
    // 寫處理記錄：詳情頁自己用，建單時由 TicketService 呼叫
    // ------------------------------------------------------------------

    /**
     * 寫一筆處理記錄。
     * <p>
     * 呼叫端的交易會傳遞進來（{@code @Transactional} 預設是 REQUIRED），
     * 所以在 {@link TicketService#create} 裡呼叫時，建單失敗這筆記錄一樣會回滾。
     *
     * @param ticket  已經 save 過的工單
     * @param agentId 留言者的客服代號；<b>傳 null 代表系統事件</b>，畫面上顯示為「系統」
     * @param content 留言內容或系統事件描述，不可為 null
     */
    @Transactional
    public void writeComment(Tickets ticket, String agentId, String content) {
        ticketCommentsRepository.save(TicketComments.builder()
                .ticketId(ticket.getTicketId())
                .agentId(agentId)
                .content(content)
                .build());
    }

    /**
     * 寫一筆轉派記錄，建單時指定別人與事後轉派共用。
     *
     * @param ticket        已經 save 過的工單
     * @param me            執行轉派的客服代號，會成為這筆記錄的留言者
     * @param targetAgentId 被轉派到的客服代號
     */
    @Transactional
    public void writeAssignComment(Tickets ticket, String me, String targetAgentId) {
        writeComment(ticket, me, "由 " + me + " 轉派給 " + targetAgentId);
    }

    /**
     * 寫一筆「狀態設定為某某」的系統記錄，建單當下用。
     * <p>
     * 中文標籤只有這個類別知道，所以組字串的工作留在這裡，
     * {@link TicketService} 不必為了一句話再抄一份 {@code STATUS_LABEL}。
     *
     * @param ticket 已經 save 過的工單
     * @param status 建單時設定的狀態，IN_PROGRESS / PENDING / RESOLVED
     */
    @Transactional
    public void writeStatusSetComment(Tickets ticket, String status) {
        writeComment(ticket, null, "狀態設定為「" + STATUS_LABEL.get(status) + "」");
    }

    // ------------------------------------------------------------------
    // 內部小工具
    // ------------------------------------------------------------------

    /**
     * 用工單編號撈工單。
     *
     * @param ticketNo 工單編號，格式 TK-XXXXXX
     * @return 查到的工單，不會是 null
     * @throws ApiException 404 / {@code TICKET_NOT_FOUND}——查無此單號
     */
    private Tickets findTicket(String ticketNo) {
        return ticketsRepository.findByTicketNo(ticketNo)
                .orElseThrow(() -> ApiException.notFound(
                        "TICKET_NOT_FOUND", "找不到工單：" + ticketNo));
    }
}
