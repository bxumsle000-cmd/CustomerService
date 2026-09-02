package com.poz.CustomerService.service;

import com.poz.CustomerService.dto.ticket.CreateTicketRequest;
import com.poz.CustomerService.dto.ticket.TicketCommentResponse;
import com.poz.CustomerService.dto.ticket.TicketDetailResponse;
import com.poz.CustomerService.dto.ticket.TicketListItemResponse;
import com.poz.CustomerService.dto.ticket.TicketPageResponse;
import com.poz.CustomerService.entity.TicketComments;
import com.poz.CustomerService.entity.Tickets;
import com.poz.CustomerService.exception.ApiException;
import com.poz.CustomerService.repository.AgentsRepository;
import com.poz.CustomerService.repository.TicketCommentsRepository;
import com.poz.CustomerService.repository.TicketsRepository;
import com.poz.CustomerService.security.CurrentAgentProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工單相關的 business logic：列表、建立、詳情、狀態變更、轉派、新增留言。
 */
@Service
@RequiredArgsConstructor
public class TicketService {

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

    /** 派單來源：客服從「＋ 新增派件」手動建立。 */
    private static final String CHANNEL_AGENT = "Agent";

    /** 每頁筆數上限。 */
    private static final int MAX_PAGE_SIZE = 50;

    /** 列表預設排序：依更新時間由新到舊。 */
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "updatedAt");

    // ------------------------------------------------------------------
    // 對外的方法
    // ------------------------------------------------------------------

    /**
     * 工單列表，目前不吃篩選條件，一律回全部工單。
     *
     * @param page 頁碼，從 1 開始
     * @param size 每頁筆數，1 到 {@value #MAX_PAGE_SIZE}
     * @return 這一頁的工單與分頁資訊；查無資料時 content 是空 list
     * @throws ApiException 400 / {@code VALIDATION_ERROR}——page 或 size 超出範圍
     */
    @Transactional(readOnly = true)
    public TicketPageResponse search(int page, int size) {
        if (page < 1) {
            throw ApiException.badRequest("VALIDATION_ERROR", "頁碼不可小於 1");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw ApiException.badRequest("VALIDATION_ERROR",
                    "每頁筆數必須介於 1 到 " + MAX_PAGE_SIZE);
        }

        // PageRequest 的頁碼從 0 開始，所以這裡減 1
        Page<Tickets> result =
                ticketsRepository.findAll(PageRequest.of(page - 1, size, DEFAULT_SORT));

        return TicketPageResponse.from(result);
    }

    /**
     * 建立工單，同時寫入建單當下的處理記錄（同一個交易，失敗一起回滾）。
     *
     * @param request 表單內容，不可為 null；ticketNo 與建立者都不由前端指定
     * @return 建立好的工單，含資料庫算出來的 ticketNo
     * @throws ApiException 404 / {@code AGENT_NOT_FOUND}——指定的轉派對象不存在
     */
    @Transactional
    public TicketListItemResponse create(CreateTicketRequest request) {
        String status = request.status();
        String channel = request.channel();

        String title = request.title().trim();
        String category = request.category().trim();

        String me = currentAgentProvider.currentAgentId();
        String assigneeId = resolveAssignee(request.assigneeId(), me);
        String description = request.description();

        // ticketNo 不填：資料庫計算欄位，INSERT 後由 Hibernate 讀回
        Tickets ticket = ticketsRepository.save(Tickets.builder()
                .title(title)
                .customerName(request.customerName())
                .contactPhone(request.contactPhone().trim())
                .category(category)
                .description(description)
                .status(status)
                .channel(channel)
                .assigneeId(assigneeId)
                .build());

        writeComment(ticket, null, channel.equals("PHONE")
                ? "工單經電話進線建立"
                : "工單以新增派件建立");
        writeComment(ticket, me, description);

        writeComment(ticket, null, "狀態設定為「" + STATUS_LABEL.get(status) + "」");
        if (!assigneeId.equals(me)) {
            writeAssignComment(ticket, me, assigneeId);
        }

        return TicketListItemResponse.from(ticket);
    }

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
    public  TicketDetailResponse assign(String ticketNo, String assignID){
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
    public  TicketDetailResponse submitContent(String ticketNo, String content){
        Tickets ticket = findTicket(ticketNo);
        String me = currentAgentProvider.currentAgentId();
        writeComment(ticket, me, content);
        return detail(ticketNo);
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

    /**
     * 決定這張工單要指派給誰，沒指定轉派對象就是自己。
     *
     * @param requested 前端送來的轉派對象，可為 null 或空白
     * @param me        目前登入的客服代號
     * @return 最終的負責客服代號，不會是 null
     * @throws ApiException 404 / {@code AGENT_NOT_FOUND}——代號不存在
     */
    private String resolveAssignee(String requested, String me) {
        if (requested == null || requested.isBlank()) {
            return me;
        }
        String assigneeId = requested.trim();
        if (!agentsRepository.existsById(assigneeId)) {
            throw ApiException.notFound("AGENT_NOT_FOUND", "找不到客服：" + assigneeId);
        }
        return assigneeId;
    }

    /**
     * 寫一筆轉派記錄，建單時指定別人與事後轉派共用。
     *
     * @param ticket        已經 save 過的工單
     * @param me            執行轉派的客服代號，會成為這筆記錄的留言者
     * @param targetAgentId 被轉派到的客服代號
     */
    private void writeAssignComment(Tickets ticket, String me, String targetAgentId) {
        writeComment(ticket, me, "由 " + me + " 轉派給 " + targetAgentId);
    }

    /**
     * 寫一筆處理記錄。
     *
     * @param ticket  已經 save 過的工單
     * @param agentId 留言者的客服代號；傳 null 代表系統事件
     * @param content 留言內容或系統事件描述，不可為 null
     */
    private void writeComment(Tickets ticket, String agentId, String content) {
        ticketCommentsRepository.save(TicketComments.builder()
                .ticketId(ticket.getTicketId())
                .agentId(agentId)
                .content(content)
                .build());
    }

}
