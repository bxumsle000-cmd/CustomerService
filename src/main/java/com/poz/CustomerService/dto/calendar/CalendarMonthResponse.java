package com.poz.CustomerService.dto.calendar;

import com.poz.CustomerService.entity.Tickets;

import java.util.List;

/**
 * 行事曆整個月的回應，對應 GET /api/calendar。
 * <p>
 * 把 {@code year} / {@code month} 原樣回給前端，是為了讓畫面能確認
 * 「我拿到的這批資料是哪一個月的」。使用者快速連點上下月時，回應不一定照送出順序回來，
 * 沒有這兩個欄位的話，慢回來的九月資料會蓋掉已經畫好的十月，
 * 而且畫面上不會有任何錯誤——只是月份標題寫十月、格子裡卻是九月的事件。
 *
 * @param year   {@code int}——西元年，例如 2026
 * @param month  {@code int}——月份，<b>1 到 12</b>（不是 0 到 11，跟 JavaScript 的 Date 不一樣，
 *               前端接的時候要注意）
 * @param events {@code List<CalendarEventResponse>}——這個月所有排了回電時間的工單，
 *               已依回電時間由早到晚排序。這個月沒有任何安排時是空 list，不會是 null
 */
public record CalendarMonthResponse(
        int year,
        int month,
        List<CalendarEventResponse> events
) {

    /**
     * 從 repository 查回來的工單清單組成回應。
     * <p>
     * 這裡<b>不</b>依日期分組成 {@code Map<LocalDate, List<...>>}：分組要用哪一種格子
     * （月曆一天一格、週檢視一小時一格）是畫面的事，後端先決定好反而綁死前端。
     * 回一個排好序的平面 list，前端要怎麼切都行。
     *
     * @param year    {@code int}——西元年，原樣帶回
     * @param month   {@code int}——月份 1 到 12，原樣帶回
     * @param tickets {@code List<Tickets>}——repository 查回來的工單，已排序，不可為 null
     * @return {@link CalendarMonthResponse}——events 已轉成 DTO
     */
    public static CalendarMonthResponse from(int year, int month, List<Tickets> tickets) {
        return new CalendarMonthResponse(
                year,
                month,
                tickets.stream()
                        .map(CalendarEventResponse::from)
                        .toList()
        );
    }
}
