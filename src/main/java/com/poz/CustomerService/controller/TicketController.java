package com.poz.CustomerService.controller;

import com.poz.CustomerService.dto.CreateTicketRequest;
import com.poz.CustomerService.dto.TicketListItemResponse;
import com.poz.CustomerService.dto.TicketPageResponse;
import com.poz.CustomerService.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 工單與處理記錄的端點：首頁的工單列表與工單詳情。
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
    @GetMapping("/search")
    public TicketPageResponse search(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ticketService.search(page, size);
    }

    // ==================================================================
    // 以下還沒寫，一支一支補上來
    // ==================================================================

    /*
     * TODO POST /api/tickets —— 建立工單
     * 收表單內容，回 201 與建好的工單（含後端發的 ticketNo）。
     * ticketNo 與「誰建立的」都由後端決定，不收前端指定。
     * Service 的 create() 已經寫好，這裡只要接上 @Valid @RequestBody。
     */
    @PostMapping("/create")
    public TicketListItemResponse create(
            @Valid @RequestBody CreateTicketRequest request
            ){
        return  ticketService.create(request);
    }


    /*
     * TODO GET /api/tickets/{ticketNo} —— 工單詳情
     * 回單張工單的完整欄位、處理記錄 timeline，以及後端算好的 allowedTransitions。
     * 單號不存在回 404。
     */



    /*
     * TODO PATCH /api/tickets/{ticketNo}/status —— 變更狀態
     * 收新狀態，回改完的工單。
     * 非法的狀態轉換回 400。
     */



    /*
     * TODO PATCH /api/tickets/{ticketNo}/assignee —— 轉派
     * 收客服代號，回改完的工單。
     * 代號不存在回 404；與目前負責人相同就不做事，直接回成功。
     */



    /*
     * TODO POST /api/tickets/{ticketNo}/comments —— 新增處理記錄
     * 收留言內容，回新增的那一筆。
     * 留言者取自登入身分，不收前端指定。
     */



    /*
     * TODO GET /api/tickets/follow-ups?year=&month= —— 行事曆月檢視
     * 回該月每一天的跟進件數與明細，只含目前登入客服自己的工單。
     */



    /*
     * TODO GET /api/tickets/followable —— 可排入行事曆的案件
     * 回自己的、狀態為 IN_PROGRESS 或 PENDING 的工單，供右側下拉選單用。
     */



    /*
     * TODO PATCH /api/tickets/{ticketNo}/follow-up —— 設定/移除跟進時間
     * 收跟進時間，回改完的工單；送 null 代表移除。
     */

}
