package com.poz.CustomerService.dto.calendar;

import java.util.List;

/**
 * 行事曆整個月的回應，對應 GET /api/calendar。
 * 回一個排好序的平面 list，不依日期分組。
 *
 * @param year   西元年，例如 2026
 * @param month  月份，<b>1 到 12</b>（不是 0 到 11）
 * @param events 這個月的所有回電安排，已依時間由早到晚排序；沒有任何安排時是空 list
 */
public record CalendarMonthResponse(
        int year,
        int month,
        List<CalendarEventResponse> events
) {

    /** 把 {@code events} 複製成唯讀副本，避免呼叫端事後改動內容。 */
    public CalendarMonthResponse {
        events = List.copyOf(events);
    }
}
