package com.poz.CustomerService.controller;

import com.poz.CustomerService.dto.ticket.AddCommentRequest;
import com.poz.CustomerService.dto.ticket.ChangeStatusRequest;
import com.poz.CustomerService.dto.ticket.CreateTicketRequest;
import com.poz.CustomerService.dto.ticket.TicketDetailResponse;
import com.poz.CustomerService.dto.ticket.TicketListItemResponse;
import com.poz.CustomerService.dto.ticket.TicketPageResponse;
import com.poz.CustomerService.dto.ticket.TicketSearchRequest;
import com.poz.CustomerService.service.TicketDetailService;
import com.poz.CustomerService.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

/**
 * 工單與處理記錄的端點：列表、建立、詳情、狀態變更、轉派、新增處理記錄。
 * <p>
 * business logic 分成兩個 Service，界線是「要不要先點進某一張工單」：
 * 列表與建立在 {@link TicketService}，其餘（詳情頁上的動作）在
 * {@link TicketDetailService}。回電安排雖然也掛在 {@code /api/tickets} 底下，
 * 但另外放在 {@link FollowUpController}。
 * <p>
 * 路徑的原則：動作能用 HTTP method 表達的就不寫進路徑（列表、建立、詳情），
 * 只有 CRUD 講不出來的動作（改狀態、轉派）才在後面接一段動詞。
 */
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final TicketDetailService ticketDetailService;

    /**
     * 工單列表，GET /api/tickets。所有篩選條件全部選填，沒帶就不篩。
     * <p>
     * 篩選條件統一收在 {@link TicketSearchRequest}：query 參數名稱對上欄位名稱就會
     * 自動填進去（{@code ?status=PENDING&contactPhone=0912000111}），沒帶的欄位是 null。
     * 不管前端要用哪幾個條件組合查詢，都是打這一支。
     * <p>
     * 除了 createdFrom / createdTo 之外都是精確比對，篩選欄要打完整的值。
     *
     * @param filter 篩選條件；{@code @ModelAttribute} 表示「從 query 參數組物件」而不是讀 body，
     *               {@code @ParameterObject} 則是讓 Swagger UI 把欄位攤開成一個個參數欄
     * @param page   頁碼，從 1 開始，沒帶就是第 1 頁
     * @param size   每頁筆數，上限 50，沒帶就是 10 筆
     * @return 200，這一頁的工單 + 分頁資訊；
     *         page / size 超出範圍或 status 不合法回 400 / {@code VALIDATION_ERROR}
     */
    @GetMapping
    public TicketPageResponse search(
            @ParameterObject @ModelAttribute TicketSearchRequest filter,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ticketService.search(filter, page, size);
    }

    /**
     * 建立工單，POST /api/tickets。
     *
     * @param request 表單內容；ticketNo 與建立者由後端決定，不收前端指定
     * @return 200，建好的工單（含後端發的 ticketNo）；欄位不合法回 400
     */
    @PostMapping
    public TicketListItemResponse create(
            @Valid @RequestBody CreateTicketRequest request
            ){
        return  ticketService.create(request);
    }


    /**
     * 工單詳情，GET /api/tickets/{ticketNo}。
     *
     * @param ticketNo 路徑上的工單編號，格式 TK-XXXXXX
     * @return 200，工單全欄位 + 處理記錄 timeline；
     *         單號不存在回 404 / {@code TICKET_NOT_FOUND}
     */
    @GetMapping("/{ticketNo}")
    public TicketDetailResponse detail(@PathVariable String ticketNo) {
        return ticketDetailService.detail(ticketNo);
    }

    /**
     * 變更工單狀態，PATCH /api/tickets/{ticketNo}/changeStatus。
     *
     * @param ticketNo 路徑上的工單編號，格式 TK-XXXXXX
     * @param request  要改成的新狀態
     * @return 200，改完的工單詳情；非法的狀態轉換回 400、單號不存在回 404
     */
    @PatchMapping("/{ticketNo}/changeStatus")
    public  TicketDetailResponse changeStatus(@PathVariable String ticketNo,
                                             @Valid @RequestBody ChangeStatusRequest request){
        return  ticketDetailService.changeStatus(ticketNo, request.status());
    }


    /**
     * 轉派工單，PATCH /api/tickets/{ticketNo}/assign。
     *
     * @param ticketNo   路徑上的工單編號，格式 TK-XXXXXX
     * @param assigneeId 要轉派給誰的客服代號
     * @return 200，改完的工單詳情；單號或客服代號不存在回 404
     */
    @PatchMapping("/{ticketNo}/assign")
    public  TicketDetailResponse assign(@PathVariable String ticketNo,@RequestParam String assigneeId){
        return  ticketDetailService.assign(ticketNo, assigneeId);
    }

    /**
     * 新增處理記錄，POST /api/tickets/{ticketNo}/comments。
     * <p>
     * 是「新增一筆記錄」而不是「改工單」，所以用 POST；內容走 request body，
     * 換行與符號都不必擔心。
     *
     * @param ticketNo 路徑上的工單編號，格式 TK-XXXXXX
     * @param request  留言內容；留言者取自登入身分，不收前端指定
     * @return 200，改完的工單詳情；單號不存在回 404
     */
    @PostMapping("/{ticketNo}/comments")
    public TicketDetailResponse addComment(@PathVariable String ticketNo,
                                           @Valid @RequestBody AddCommentRequest request){
        return  ticketDetailService.submitContent(ticketNo, request.content());
    }
}
