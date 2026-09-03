package com.poz.CustomerService.service;

import com.poz.CustomerService.dto.ticket.CreateTicketRequest;
import com.poz.CustomerService.dto.ticket.TicketListItemResponse;
import com.poz.CustomerService.dto.ticket.TicketPageResponse;
import com.poz.CustomerService.entity.Tickets;
import com.poz.CustomerService.exception.ApiException;
import com.poz.CustomerService.repository.AgentsRepository;
import com.poz.CustomerService.repository.TicketsRepository;
import com.poz.CustomerService.security.CurrentAgentProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工單的 business logic：首頁列表與建立工單。
 * <p>
 * 跟 {@link TicketDetailService} 的分界是「<b>要不要先點進某一張工單</b>」。
 * 這兩支都不必：列表是還沒選任何一張單的畫面，建單則是從通話工作台或
 * 「＋ 新增派件」直接開一張新的。詳情、改狀態、轉派、留言全在
 * {@link TicketDetailService}。
 * <p>
 * 方法一覽：
 * <ul>
 *   <li>{@link #search} —— 首頁列表，分頁與排序</li>
 *   <li>{@link #create} —— 建立工單，順便寫進建單當下的處理記錄</li>
 *   <li>{@code resolveAssignee}（private）—— 決定負責客服，沒指定就是自己</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketsRepository ticketsRepository;
    private final AgentsRepository agentsRepository;
    private final CurrentAgentProvider currentAgentProvider;

    /** 建單當下要寫的那幾筆處理記錄，交給詳情頁那邊統一寫，格式才不會兩套。 */
    private final TicketDetailService ticketDetailService;

    // ------------------------------------------------------------------
    // 常數
    // ------------------------------------------------------------------

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
     * <p>
     * 記錄是呼叫 {@link TicketDetailService} 寫的。跨 Service 呼叫不會另開交易——
     * {@code @Transactional} 預設是 REQUIRED，這裡開的交易會直接傳遞過去，
     * 所以「工單存好了但記錄沒寫進去」不會發生。
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

        ticketDetailService.writeComment(ticket, null, channel.equals("PHONE")
                ? "工單經電話進線建立"
                : "工單以新增派件建立");
        ticketDetailService.writeComment(ticket, me, description);

        ticketDetailService.writeStatusSetComment(ticket, status);
        if (!assigneeId.equals(me)) {
            ticketDetailService.writeAssignComment(ticket, me, assigneeId);
        }

        return TicketListItemResponse.from(ticket);
    }

    // ------------------------------------------------------------------
    // 內部小工具
    // ------------------------------------------------------------------

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
}
