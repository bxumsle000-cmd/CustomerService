package com.poz.CustomerService.controller;

import com.poz.CustomerService.dto.calendar.CalendarEventResponse;
import com.poz.CustomerService.dto.calendar.FollowUpRequest;
import com.poz.CustomerService.service.CalendarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 回電安排的端點：新增、改期、取消。
 * <p>
 * 路徑全部掛在工單底下（{@code /api/tickets/{ticketNo}/followUps}），因為一筆安排
 * 本來就是某張工單的附屬資料。改期與取消雖然靠 {@code followUpId} 就能定位，
 * 路徑上還是帶著 ticketNo：Service 會比對這筆安排是不是真的屬於這張工單，
 * 拿別張工單的號碼湊上來會被當成查無此安排。
 * <p>
 * 查詢那一側（某個月排了哪些）在 {@link CalendarController}。
 * business logic 跟它共用 {@link CalendarService}。
 */
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class FollowUpController {

    private final CalendarService calendarService;

    /**
     * 對某張工單新增一筆自己的回電安排，POST /api/tickets/{ticketNo}/followUps。
     *
     * @param ticketNo 路徑上的工單編號，格式 TK-XXXXXX
     * @param request  回電時間與備註，時間必填、備註選填
     * @return 201，剛排好的那一格事件，含改期／取消要用的 followUpId。
     *         單號不存在回 404 / {@code TICKET_NOT_FOUND}；
     *         這個時間點已經排過回 409 / {@code FOLLOW_UP_ALREADY_EXISTS}
     */
    @PostMapping("/{ticketNo}/followUps")
    @ResponseStatus(HttpStatus.CREATED)
    public CalendarEventResponse createFollowUp(
            @PathVariable String ticketNo,
            @Valid @RequestBody FollowUpRequest request) {
        return calendarService.createFollowUp(ticketNo, request);
    }

    /**
     * 改自己某一筆回電安排的時間或備註，
     * PATCH /api/tickets/{ticketNo}/followUps/{followUpId}。
     *
     * @param ticketNo   路徑上的工單編號，格式 TK-XXXXXX
     * @param followUpId 路徑上的安排流水號，必須是自己的、且掛在這張工單底下
     * @param request    要改成的回電時間與備註，兩個欄位都會被覆蓋
     * @return 200，改完的那一格事件。
     *         單號不存在回 404 / {@code TICKET_NOT_FOUND}；
     *         號碼不存在、不是自己的、或不屬於這張工單回 404 / {@code FOLLOW_UP_NOT_FOUND}；
     *         那個時間點已經有另外一筆回 409 / {@code FOLLOW_UP_ALREADY_EXISTS}
     */
    @PatchMapping("/{ticketNo}/followUps/{followUpId}")
    public CalendarEventResponse updateFollowUp(
            @PathVariable String ticketNo,
            @PathVariable Integer followUpId,
            @Valid @RequestBody FollowUpRequest request) {
        return calendarService.updateFollowUp(ticketNo, followUpId, request);
    }

    /**
     * 取消自己的某一筆回電安排，DELETE /api/tickets/{ticketNo}/followUps/{followUpId}。
     * <p>
     * 回 204，沒有 body。單號或號碼不存在、不是自己的、不屬於這張工單，
     * <b>都算成功</b>，不回 404。
     *
     * @param ticketNo   路徑上的工單編號，格式 TK-XXXXXX
     * @param followUpId 路徑上的安排流水號
     */
    @DeleteMapping("/{ticketNo}/followUps/{followUpId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFollowUp(@PathVariable String ticketNo,
                               @PathVariable Integer followUpId) {
        calendarService.deleteFollowUp(ticketNo, followUpId);
    }
}
