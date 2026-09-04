package com.poz.CustomerService.controller;

import com.poz.CustomerService.dto.calendar.CalendarEventResponse;
import com.poz.CustomerService.dto.calendar.CalendarMonthResponse;
import com.poz.CustomerService.dto.calendar.FollowUpRequest;
import com.poz.CustomerService.service.CalendarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 回電安排的端點：查某個月排了哪些，以及新增、改期、取消。
 * <p>
 * 新增、改期、取消的路徑掛在工單底下（{@code /api/tickets/{ticketNo}/followUps}），
 * 因為一筆安排本來就是某張工單的附屬資料。改期與取消雖然靠 {@code followUpId} 就能定位，
 * 路徑上還是帶著 ticketNo：Service 會比對這筆安排是不是真的屬於這張工單，
 * 拿別張工單的號碼湊上來會被當成查無此安排。
 * <p>
 * 月檢視的查詢是唯一的例外，路徑是 {@code /api/calendar}——它問的是「我這個月排了什麼」，
 * 不屬於任何一張工單，掛在工單底下反而講不通。兩種路徑的共同前綴只到 {@code /api}，
 * 所以 class 層只寫到那裡，剩下的段落由各方法自己補完。
 * <p>
 * business logic 一律放 {@link CalendarService}。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FollowUpController {

    private final CalendarService calendarService;

    /**
     * 查自己某個月排定的所有回電，GET /api/calendar。
     *
     * @param year  西元年，2015 到 2050
     * @param month 月份，1 到 12
     * @return 200，這個月的所有回電安排，依時間排序；沒排任何事情時 events 是空 list。
     *         年份或月份超出範圍回 400 / {@code VALIDATION_ERROR}
     */
    @GetMapping("/calendar")
    public CalendarMonthResponse monthlyFollowUps(
            @RequestParam int year,
            @RequestParam int month) {
        return calendarService.monthlyFollowUps(year, month);
    }

    /**
     * 對某張工單新增一筆自己的回電安排，POST /api/tickets/{ticketNo}/followUps。
     *
     * @param ticketNo 路徑上的工單編號，格式 TK-XXXXXX
     * @param request  回電時間與備註，時間必填、備註選填
     * @return 201，剛排好的那一格事件，含改期／取消要用的 followUpId。
     *         單號不存在回 404 / {@code TICKET_NOT_FOUND}；
     *         這個時間點已經排過回 409 / {@code FOLLOW_UP_ALREADY_EXISTS}
     */
    @PostMapping("/tickets/{ticketNo}/followUps")
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
    @PatchMapping("/tickets/{ticketNo}/followUps/{followUpId}")
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
    @DeleteMapping("/tickets/{ticketNo}/followUps/{followUpId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFollowUp(@PathVariable String ticketNo,
                               @PathVariable Integer followUpId) {
        calendarService.deleteFollowUp(ticketNo, followUpId);
    }
}
