package com.poz.CustomerService.dto.calendar;

import java.util.List;

/**
 * 行事曆整個月的回應，對應 GET /api/calendar。
 * <p>
 * 把 {@code year} / {@code month} 原樣回給前端，是為了讓畫面能確認
 * 「我拿到的這批資料是哪一個月的」。使用者快速連點上下月時，回應不一定照送出順序回來，
 * 沒有這兩個欄位的話，慢回來的九月資料會蓋掉已經畫好的十月，
 * 而且畫面上不會有任何錯誤——只是月份標題寫十月、格子裡卻是九月的事件。
 *
 * <h2>這裡不依日期分組</h2>
 * <b>不</b>組成 {@code Map<LocalDate, List<...>>}：分組要用哪一種格子
 * （月曆一天一格、週檢視一小時一格）是畫面的事，後端先決定好反而綁死前端。
 * 回一個排好序的平面 list，前端要怎麼切都行。
 *
 * <h2>為什麼沒有 from()</h2>
 * 其他 DTO 的 {@code from()} 是在做「entity → DTO」的轉換，負責決定哪些欄位可以出去。
 * 這一支沒有那件事可做——它只是把已經轉好的 event list 包一層月份資訊，
 * 真正在轉 entity 的是 {@link CalendarEventResponse#from}。
 * 硬加一支 {@code from()} 只會讓「把安排和工單配對起來」這個屬於 Service 的工作
 * 跑到回應物件裡，還得為此把兩個 entity 當參數傳進來。
 *
 * @param year   {@code int}——西元年，例如 2026
 * @param month  {@code int}——月份，<b>1 到 12</b>（不是 0 到 11，跟 JavaScript 的 Date 不一樣，
 *               前端接的時候要注意）
 * @param events {@code List<CalendarEventResponse>}——這個月的所有回電安排，
 *               已依回電時間由早到晚排序。同一張工單排了多筆就會出現多格。
 *               這個月沒有任何安排時是空 list，不會是 null
 */
public record CalendarMonthResponse(
        int year,
        int month,
        List<CalendarEventResponse> events
) {

    /**
     * 把 {@code events} 複製成唯讀副本。record 只保證「欄位不能被換掉」，
     * 不保證「欄位指到的 list 不能被改」——直接存傳進來的 list，
     * 呼叫端之後對它 add、remove，這個 DTO 裡的內容會跟著變。
     * 作法同 {@code TicketDetailResponse}。
     */
    public CalendarMonthResponse {
        events = List.copyOf(events);
    }
}
