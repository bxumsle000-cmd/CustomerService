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

import java.time.LocalDateTime;
import java.util.Set;

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

    /**
     * 列表預設排序：依更新時間由新到舊。
     * <p>
     * 後面再接 {@code ticketId} 是為了讓排序穩定。{@code updated_at} 是 DATETIME2(0)、
     * 只存到秒，同一秒更新的多筆工單前後順序不固定，翻頁時同一筆可能出現兩次、
     * 或整個被跳過。補一個唯一且不會變的欄位當第二順位就沒這個問題。
     */
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "updatedAt")
            .and(Sort.by(Sort.Direction.DESC, "ticketId"));

    /** 可以拿來篩選的狀態，必須與 {@code CK_tickets_status} 一致。 */
    private static final Set<String> ALLOWED_STATUS = Set.of("IN_PROGRESS", "PENDING", "RESOLVED");

    // ------------------------------------------------------------------
    // 對外的方法
    // ------------------------------------------------------------------

    /**
     * 工單列表，七個篩選條件全部選填，沒帶的就不篩。
     * <p>
     * 除了 {@code createdFrom} / {@code createdTo} 之外都是<b>精確比對</b>：篩選欄要打完整的值。
     * 姓名要連稱謂一起打（資料庫存的是「王小明先生」這種完整字串），
     * 電話和單號也不能只打一半。
     *
     * @param ticketNo     工單編號，完整的 TK-XXXXXX；null 表示不篩
     * @param customerName 客戶姓名；null 表示不篩
     * @param contactPhone 聯絡電話；null 表示不篩
     * @param assigneeId   負責客服代號；null 表示不篩
     * @param status       處理狀態，IN_PROGRESS / PENDING / RESOLVED；null 表示不篩
     * @param createdFrom  區間起點，建立時間 &gt;= 這個時間點；null 表示不限起點。
     *                     「近 7 天」那種相對區間由前端自己換算成絕對時間再送過來
     * @param createdTo    區間終點，建立時間 &lt;= 這個時間點（<b>含</b>邊界）；null 表示不限終點。
     *                     想查整個 9/30 就送 {@code 2026-09-30T23:59:59}。
     *                     起點晚於終點<b>不會</b>丟 400，就是回 0 筆
     * @param page         頁碼，從 1 開始
     * @param size         每頁筆數，1 到 {@value #MAX_PAGE_SIZE}
     * @return 這一頁的工單與分頁資訊；查無資料時 content 是空 list
     * @throws ApiException 400 / {@code VALIDATION_ERROR}——page、size 超出範圍，
     *                      或 status 不是那三個值之一
     */
    @Transactional(readOnly = true)
    public TicketPageResponse search(String ticketNo,
                                     String customerName,
                                     String contactPhone,
                                     String assigneeId,
                                     String status,
                                     LocalDateTime createdFrom,
                                     LocalDateTime createdTo,
                                     int page,
                                     int size) {
        if (page < 1) {
            throw ApiException.badRequest("VALIDATION_ERROR", "頁碼不可小於 1");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw ApiException.badRequest("VALIDATION_ERROR",
                    "每頁筆數必須介於 1 到 " + MAX_PAGE_SIZE);
        }

        // 不擋的話，打錯的狀態會安靜地回 0 筆，看不出來是自己拼錯還是真的沒資料
        if (status != null && !ALLOWED_STATUS.contains(status)) {
            throw ApiException.badRequest("VALIDATION_ERROR",
                    "狀態只能是 IN_PROGRESS / PENDING / RESOLVED");
        }

        // PageRequest 的頁碼從 0 開始，所以這裡減 1
        Page<Tickets> result = ticketsRepository.search(
                ticketNo,
                customerName,
                contactPhone,
                assigneeId,
                status,
                createdFrom,
                createdTo,
                PageRequest.of(page - 1, size, DEFAULT_SORT));

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
