package com.poz.CustomerService.controller;

import com.poz.CustomerService.dto.ticket.ChangeStatusRequest;
import com.poz.CustomerService.dto.ticket.CreateTicketRequest;
import com.poz.CustomerService.dto.ticket.TicketDetailResponse;
import com.poz.CustomerService.dto.ticket.TicketListItemResponse;
import com.poz.CustomerService.dto.ticket.TicketPageResponse;
import com.poz.CustomerService.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 工單與處理記錄的端點：列表、建立、詳情、狀態變更、轉派、新增留言。
 * business logic 一律放 {@link TicketService}。
 */
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    /**
     * 工單列表，GET /api/tickets/search。目前只吃分頁參數。
     *
     * @param page 頁碼，從 1 開始，沒帶就是第 1 頁
     * @param size 每頁筆數，上限 50，沒帶就是 10 筆
     * @return 200，這一頁的工單 + 分頁資訊；page / size 超出範圍回 400
     */
    @GetMapping("/search")
    public TicketPageResponse search(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ticketService.search(page, size);
    }

    /**
     * 建立工單，POST /api/tickets/create。
     *
     * @param request 表單內容；ticketNo 與建立者由後端決定，不收前端指定
     * @return 200，建好的工單（含後端發的 ticketNo）；欄位不合法回 400
     */
    @PostMapping("/create")
    public TicketListItemResponse create(
            @Valid @RequestBody CreateTicketRequest request
            ){
        return  ticketService.create(request);
    }


    /**
     * 工單詳情，GET /api/tickets/{ticketNo}/detail。
     *
     * @param ticketNo 路徑上的工單編號，格式 TK-XXXXXX
     * @return 200，工單全欄位 + 處理記錄 timeline + 自己的回電安排；
     *         單號不存在回 404 / {@code TICKET_NOT_FOUND}
     */
    @GetMapping("/{ticketNo}/detail")
    public TicketDetailResponse detail(@PathVariable String ticketNo) {
        return ticketService.detail(ticketNo);
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
        return  ticketService.changeStatus(ticketNo, request.status());
    }


    /**
     * 轉派工單，PATCH /api/tickets/{ticketNo}/assign。
     *
     * @param ticketNo 路徑上的工單編號，格式 TK-XXXXXX
     * @param assignID 要轉派給誰的客服代號
     * @return 200，改完的工單詳情；單號或客服代號不存在回 404
     */
    @PatchMapping("/{ticketNo}/assign")
    public  TicketDetailResponse assign(@PathVariable String ticketNo,@RequestParam String assignID){
        return  ticketService.assign(ticketNo, assignID);
    }

    /**
     * 新增處理記錄，PATCH /api/tickets/{ticketNo}/submit。
     *
     * @param ticketNo 路徑上的工單編號，格式 TK-XXXXXX
     * @param content  留言內容；留言者取自登入身分，不收前端指定
     * @return 200，改完的工單詳情；單號不存在回 404
     */
    @PatchMapping("/{ticketNo}/submit")
    public TicketDetailResponse submit(@PathVariable String ticketNo,@RequestParam String content){
        return  ticketService.submitContent(ticketNo,content);
    }
}