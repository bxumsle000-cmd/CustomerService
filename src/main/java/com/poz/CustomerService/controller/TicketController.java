package com.poz.CustomerService.controller;

import com.poz.CustomerService.dto.TicketPageResponse;
import com.poz.CustomerService.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工單與處理記錄的端點，對應 docs/api.md「三、工單列表」與「四、工單詳情」。
 * <p>
 * Controller 只做三件事：收參數、叫 Service、回結果。business logic 一律放
 * {@link TicketService}，這裡不寫判斷也不碰資料庫。
 * 也看不到 try-catch——Service 丟的 ApiException 由
 * {@link com.poz.CustomerService.exception.GlobalExceptionHandler} 統一轉成 JSON。
 */
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    /**
     * 工單列表，GET /api/tickets。首頁那張表格。
     * <p>
     * <b>目前是最簡版：只吃分頁參數，六個篩選條件還沒接。</b>
     * 接的時候把這兩個參數換成 {@code @Valid @ModelAttribute TicketSearchRequest}，
     * 電話、單號、姓名那些就都是它的欄位。
     * <p>
     * 用 {@code @RequestParam} 而不是 {@code @RequestBody}：GET 沒有 request body，
     * 值要放在網址的 {@code ?} 後面，例如 {@code /api/tickets?page=2&size=20}。
     * <p>
     * 另外 {@code @RequestParam} 只綁得了字串、數字這類單純的值，
     * 不能拿來接 {@code Tickets} 這種物件——Spring 不知道要怎麼把一個網址參數
     * 變成一整個 entity。要一次收多個查詢條件時，用的是 {@code @ModelAttribute}。
     *
     * @param page {@code int}——頁碼，從 1 開始。沒帶就是第 1 頁
     * @param size {@code int}——每頁筆數，上限 50。沒帶就是 10 筆
     * @return {@link TicketPageResponse}——200，這一頁的工單 + 分頁資訊 + 四個 tab 的件數。
     *         沒有符合條件的資料時 content 是空陣列（仍然是 200，不是 404）；
     *         page / size 超出範圍回 400 / {@code VALIDATION_ERROR}
     */
    @GetMapping
    public TicketPageResponse search(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ticketService.search(page, size);
    }

    /*
     * ==================================================================
     * 還沒寫的五支。規格如下，一支一支加，加完就打一次確認。
     * ==================================================================
     *
     * ── 2. GET /api/tickets/{ticketNo} ───────────────────────────────
     *    工單詳情，含 timeline。
     *
     *    參數：@PathVariable String ticketNo
     *    回傳：TicketDetailResponse
     *    狀態碼：200；查不到 404 / TICKET_NOT_FOUND
     *
     *    注意路徑吃的是 ticketNo（TK-084215）不是 ticketId，
     *    理由見 api.md「一、共通約定」的識別碼那段。
     *
     *
     * ── 3. POST /api/tickets ─────────────────────────────────────────
     *    建立工單。Service 已經寫好了（TicketService.create），這裡只要接上。
     *
     *    參數：@Valid @RequestBody CreateTicketRequest
     *    狀態碼：**201 不是 200**
     *
     *    兩個要處理的落差：
     *
     *    (a) 回傳型別。api.md 說「回傳 201 與建立好的工單，格式同
     *        GET /api/tickets/{ticketNo}」，也就是 TicketDetailResponse。
     *        但現在 TicketService.create() 回的是 TicketListItemResponse。
     *        要嘛改 Service，要嘛改規格——你決定，並把決定寫成註解。
     *
     *    (b) 201 怎麼回。上面那支是直接回 DTO（Spring 預設 200），
     *        要回 201 得換寫法。查一下 @ResponseStatus 這個註解，
     *        或改回傳 ResponseEntity。兩種都可以，挑一個。
     *
     *
     * ── 4. PATCH /api/tickets/{ticketNo}/status ──────────────────────
     *    變更狀態。
     *
     *    參數：@PathVariable String ticketNo + @Valid @RequestBody UpdateTicketStatusRequest
     *    回傳：TicketDetailResponse（讓前端直接重畫詳情頁，不用再打一次 GET）
     *    狀態碼：200；非法轉換 400 / INVALID_STATUS_TRANSITION；查不到 404
     *
     *    用 PATCH 不用 PUT：只改一個欄位、其他不動。理由同 AgentController 那支。
     *
     *
     * ── 5. PATCH /api/tickets/{ticketNo}/assignee ────────────────────
     *    轉派。
     *
     *    參數：@PathVariable String ticketNo + @Valid @RequestBody UpdateTicketAssigneeRequest
     *    回傳：TicketDetailResponse
     *    狀態碼：200；客服不存在 404 / AGENT_NOT_FOUND；工單不存在 404 / TICKET_NOT_FOUND
     *
     *
     * ── 6. POST /api/tickets/{ticketNo}/comments ─────────────────────
     *    新增處理記錄。
     *
     *    參數：@PathVariable String ticketNo + @Valid @RequestBody CreateCommentRequest
     *    回傳：見 CreateCommentRequest 裡「要自己決定的事」第 2 點
     *    狀態碼：201
     *
     *    留言者是誰**不從參數來**，Service 自己去問 CurrentAgentProvider。
     *
     * ------------------------------------------------------------------
     * 【重點】@RequestBody 前面一定要加 @Valid
     * ------------------------------------------------------------------
     * 不加的話 DTO 上的 @NotBlank / @Size / @Pattern **完全不會生效**。
     * 這是最常見的無聲失敗：程式跑得好好的，驗證卻整組沒作用。
     *
     * ------------------------------------------------------------------
     * 【重點】路徑順序的坑
     * ------------------------------------------------------------------
     * 之後做行事曆時會加 GET /api/tickets/follow-ups 和 GET /api/tickets/followable，
     * 它們跟 GET /api/tickets/{ticketNo} 長得一樣（都是 /api/tickets/某個字串）。
     * 照 Spring 的比對規則字面路徑優先於變數路徑，所以應該不會打架，
     * 但這點我沒實測過。真的撞到時，解法是讓 {ticketNo} 帶上格式限制
     * （@PathVariable 的路徑可以寫正規表示式），或把那兩支換個路徑。
     *
     * ------------------------------------------------------------------
     * 完成檢查表
     * ------------------------------------------------------------------
     *   [ ] 六支都在，HTTP 動詞和路徑跟 api.md 一致
     *   [ ] 每個 @RequestBody 前面都有 @Valid
     *   [ ] 兩支 POST 回 201
     *   [ ] Controller 裡沒有任何 if、沒有 repository、沒有 try-catch
     *   [ ] 用 Swagger UI 或 curl 實際打過每一支（含失敗情境：查不到、非法轉換、驗證失敗）
     * ==================================================================
     */
}
