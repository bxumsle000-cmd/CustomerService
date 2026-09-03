package com.poz.CustomerService.controller;

import com.poz.CustomerService.dto.calendar.CalendarMonthResponse;
import com.poz.CustomerService.service.CalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 行事曆端點：只有「查某個月的回電安排」這一支。
 * <p>
 * 新增、改期、取消都在 {@link FollowUpController}——那些動作的對象是某張工單底下的
 * 回電安排，日曆只是它的一種呈現方式，不是它的擁有者。
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
}
