package com.poz.CustomerService.service;

import com.poz.CustomerService.dto.CreateTicketRequest;
import com.poz.CustomerService.dto.TicketListItemResponse;
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

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 工單相關的 business logic。
 *
 * <h2>目前有哪些方法可用</h2>
 * 對外開放（Controller 呼叫的）：
 * <ul>
 *   <li>{@link #create(CreateTicketRequest)} → {@link TicketListItemResponse}
 *       ——建立工單，並寫入建單當下的處理記錄</li>
 * </ul>
 * 內部小工具（private，Controller 叫不到）：
 * <ul>
 *   <li>{@link #resolveAssignee(String, String)}——決定指派給誰，沒指定就是自己</li>
 *   <li>{@link #generateTicketNo()}——產生對外的工單編號 TK-XXXXXX</li>
 *   <li>{@link #writeComment(Tickets, String, String)}——寫一筆處理記錄</li>
 * </ul>
 * <b>還沒有的</b>：列表查詢、工單詳情、狀態變更、轉派、新增留言。
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

    /**
     * 工單狀態白名單，順便存中文標籤——系統留言「狀態設定為「處理中」」要用。
     * 三個值必須跟 {@code CK_tickets_status} 一致，否則存進去會被資料庫擋成 500。
     */
    private static final Map<String, String> STATUS_LABEL = Map.of(
            "IN_PROGRESS", "處理中",
            "PENDING", "待客戶回覆",
            "RESOLVED", "已解決");

    /** 通話工作台在通話中建立。 */
    private static final String CHANNEL_PHONE = "PHONE";

    /** 客服自己從「＋ 新增派件」手動建立。首字大寫是照 {@code CK_tickets_channel} 的值。 */
    private static final String CHANNEL_AGENT = "Agent";

    /** {@code tickets.title} 是 NVARCHAR(50)（V5 縮下來的），超過就存不進去。 */
    private static final int TITLE_MAX_LENGTH = 50;

    private static final String TICKET_NO_PREFIX = "TK-";

    // ------------------------------------------------------------------
    // 對外的方法
    // ------------------------------------------------------------------

    /**
     * 建立工單。通話工作台的「✓ 建立工單並結束通話」與首頁的「＋ 新增派件」共用這一支，
     * 差別只在 {@code channel}。
     * <p>
     * 工單本身和底下的處理記錄都在<b>同一個交易</b>裡寫入：中途任何一步失敗，
     * 整批一起回滾，不會留下一張沒有任何記錄的孤兒工單。
     *
     * @param request {@link CreateTicketRequest}——表單內容，不可為 null。
     *                {@code ticketNo} 不在裡面，由後端產生；「誰建立的」也不由前端指定
     * @return {@link TicketListItemResponse}——建立好的工單，含後端發的 {@code ticketNo}。
     *         前端拿到之後就能導到工單詳情頁
     * @throws ApiException 400 / {@code INVALID_TICKET_STATUS}——狀態不在白名單內；
     *                      400 / {@code VALIDATION_ERROR}——主旨超過 50 字；
     *                      404 / {@code AGENT_NOT_FOUND}——指定的轉派對象不存在。
     *                      必填欄位空白不在這裡擋，由 DTO 的 {@code @NotBlank} 負責
     */
    @Transactional
    public TicketListItemResponse create(CreateTicketRequest request) {
        // 「必填」不在這裡檢查——交給 CreateTicketRequest 的 @NotBlank 搭配 Controller 的
        // @Valid。不合格的請求會被 Spring 擋在 Controller，根本進不到這裡，
        // 錯誤文案也只有 DTO 那一份，不會兩邊各寫一句然後改到不一致。
        //
        // 代價寫清楚：這支方法假設呼叫端已經驗過。從 Controller 以外的地方呼叫
        // （測試、排程、其他 Service）而且沒驗，title / category 是 null 時這裡會 NPE。
        String status = request.status();
        if (!STATUS_LABEL.containsKey(status)) {
            throw ApiException.badRequest("INVALID_TICKET_STATUS", "不支援的工單狀態：" + status);
        }

        String channel = request.channel();

        String title = request.title().trim();
        if (title.length() > TITLE_MAX_LENGTH) {
            throw ApiException.badRequest("VALIDATION_ERROR",
                    "主旨長度不可超過 " + TITLE_MAX_LENGTH);
        }
        String category = request.category().trim();

        String me = currentAgentProvider.currentAgentId();
        String assigneeId = resolveAssignee(request.assigneeId(), me);
        String description = request.description();

        Tickets ticket = ticketsRepository.save(Tickets.builder()
                .ticketNo(generateTicketNo())
                .title(title)
                .customerName(request.customerName())
                // 只有電話去頭尾空白。使用者常從別處複製貼上，多一個空白會讓
                // 「用進線號碼查歷史工單」查不到——那是等值比對，走
                // IX_tickets_contact_phone 這條索引，而且畫面上看起來一模一樣，很難查。
                .contactPhone(request.contactPhone().trim())
                .category(category)
                .description(description)
                .status(status)
                .channel(channel)
                .assigneeId(assigneeId)
                .build());
        // createdAt / updatedAt 不用自己填，Tickets 的 @PrePersist 會補上同一個時間，
        // 剛好符合前端「剛建立的工單兩個時間對齊」的顯示規則（更新時間顯示為「—」）。

        // 處理記錄，順序照 index.html 的 createFromCall()
        writeComment(ticket, null, CHANNEL_PHONE.equals(channel)
                ? "工單經電話進線建立"
                : "工單以新增派件建立");
        writeComment(ticket, me, description);

        writeComment(ticket, null, "狀態設定為「" + STATUS_LABEL.get(status) + "」");
        if (!assigneeId.equals(me)) {
            writeComment(ticket, null, "由 " + me + " 轉派給 " + assigneeId);
        }

        return TicketListItemResponse.from(ticket);
    }

    // ------------------------------------------------------------------
    // 內部小工具
    // ------------------------------------------------------------------

    /**
     * 決定這張工單要指派給誰。
     * <p>
     * 前端只有勾了「轉派給其他客服」才會送代號，沒送就是自己處理。
     * 代號是<b>使用者手打</b>的（見 index.html 的 c-assignee-code 輸入框），
     * 打錯的話直接存下去會撞上外鍵 {@code FK_tickets_agents} 變成 500，
     * 所以這裡先查一次，讓使用者看到看得懂的 404。
     *
     * @param requested {@code String}——前端送來的轉派對象，可為 null 或空白
     * @param me        {@code String}——目前登入的客服代號
     * @return {@code String}——最終的負責客服代號，不會是 null
     * @throws ApiException 404 / {@code AGENT_NOT_FOUND}——代號不存在
     */
    private String resolveAssignee(String requested, String me) {
        String assigneeId = requested;
        if (assigneeId == null) {
            return me;
        }
        if (!agentsRepository.existsById(assigneeId)) {
            throw ApiException.notFound("AGENT_NOT_FOUND", "找不到客服：" + assigneeId);
        }
        return assigneeId;
    }

    /**
     * 產生對外的工單編號，格式 {@code TK-} + 六位數字（例：TK-084215），共 9 個字，
     * 塞得進 {@code ticket_no} 的 NVARCHAR(10)。
     * <p>
     * <b>已知限制</b>：這裡<b>沒有</b>檢查編號是否已被用過，撞號時會由資料表上的
     * {@code UQ_tickets_ticket_no} 擋下來，使用者看到的是 500。
     * 九十萬個號碼隨機撞上的機率很低，但不是零。
     * 要補這一關，{@code TicketsRepository} 需要一支
     * {@code boolean existsByTicketNo(String ticketNo)}，
     * 再把下面改成「產生 → 檢查 → 重試最多 N 次」的迴圈。
     *
     * @return {@code String}——工單編號，例如 TK-084215
     */
    private String generateTicketNo() {
        return TICKET_NO_PREFIX + ThreadLocalRandom.current().nextInt(100_000, 1_000_000);
    }

    /**
     * 寫一筆處理記錄。
     *
     * @param ticket  {@link Tickets}——已經 save 過的工單，這裡要拿它被資料庫發號的 ticketId
     * @param agentId {@code String}——留言者的客服代號；<b>傳 null 代表系統事件</b>
     *                （建單、狀態設定、轉派），前端會顯示成「系統」
     * @param content {@code String}——留言內容或系統事件描述，不可為 null
     */
    private void writeComment(Tickets ticket, String agentId, String content) {
        ticketCommentsRepository.save(TicketComments.builder()
                .ticketId(ticket.getTicketId())
                .agentId(agentId)
                .content(content)
                .build());
        // createdAt 由 TicketComments 的 @PrePersist 補。
        // 注意：時間只精確到秒，同一次建單的幾筆記錄時間會一樣，
        // 之後撈 timeline 排序時要再帶上 commentId 才不會亂序。
    }

}
