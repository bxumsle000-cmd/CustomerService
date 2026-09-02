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
 * 行事曆端點：查某個月的回電安排，以及新增、改期、取消單筆安排。
 * business logic 一律放 {@link CalendarService}。
 */
@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;

    /**
     * 查自己某個月排定的所有回電，GET /api/calendar。
     *
     * @param year  西元年，2015 到 2050
     * @param month 月份，1 到 12
     * @return 200，這個月的所有回電安排，依時間排序；沒排任何事情時 events 是空 list。
     *         年份或月份超出範圍回 400 / {@code VALIDATION_ERROR}
     */
    @GetMapping
    public CalendarMonthResponse monthlyFollowUps(
            @RequestParam int year,
            @RequestParam int month) {
        return calendarService.monthlyFollowUps(year, month);
    }

    /**
     * 對某張工單新增一筆自己的回電安排，POST /api/calendar/{ticketNo}/followUps。
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
     * 改自己某一筆回電安排的時間或備註，PATCH /api/calendar/followUps/{followUpId}。
     *
     * @param followUpId 路徑上的安排流水號，必須是自己的
     * @param request    要改成的回電時間與備註，兩個欄位都會被覆蓋
     * @return 200，改完的那一格事件。
     *         號碼不存在或不是自己的回 404 / {@code FOLLOW_UP_NOT_FOUND}；
     *         那個時間點已經有另外一筆回 409 / {@code FOLLOW_UP_ALREADY_EXISTS}
     */
    @PatchMapping("/followUps/{followUpId}")
    public CalendarEventResponse updateFollowUp(
            @PathVariable Integer followUpId,
            @Valid @RequestBody FollowUpRequest request) {
        return calendarService.updateFollowUp(followUpId, request);
    }

    /**
     * 取消自己的某一筆回電安排，DELETE /api/calendar/followUps/{followUpId}。
     *
     * @param followUpId 路徑上的安排流水號
     * @return 204，沒有 body。號碼不存在或不是自己的也算成功，不回 404
     */
    @DeleteMapping("/followUps/{followUpId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFollowUp(@PathVariable Integer followUpId) {
        calendarService.deleteFollowUp(followUpId);
    }
}
