package com.poz.CustomerService.dto.calendar;

import com.poz.CustomerService.entity.FollowUps;
import com.poz.CustomerService.entity.Tickets;

import java.util.List;
import java.util.Map;

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
 * @param events {@code List<CalendarEventResponse>}——這個月的所有回電安排，
 *               已依回電時間由早到晚排序。這個月沒有任何安排時是空 list，不會是 null
 */
public record CalendarMonthResponse(
        int year,
        int month,
        List<CalendarEventResponse> events
) {

    /**
     * 把「回電安排」和「它們對應的工單」兩批資料合起來組成回應。
     *
     * <h3>為什麼要傳一個 Map 進來</h3>
     * 一格事件的內容橫跨 follow_ups 和 tickets 兩張表（見 {@link CalendarEventResponse}）。
     * 最直覺的寫法是每跑一筆安排就去查一次工單，但那是典型的 N+1：一個月三十筆安排
     * 就會發三十一次查詢。所以 Service 先用一次 {@code findAllById} 把這批工單一次撈回來、
     * 做成 {@code ticketId -> Tickets} 的 Map 傳進來，這裡只做記憶體裡的配對，不再碰資料庫。
     *
     * <h3>這裡不依日期分組</h3>
     * <b>不</b>組成 {@code Map<LocalDate, List<...>>}：分組要用哪一種格子
     * （月曆一天一格、週檢視一小時一格）是畫面的事，後端先決定好反而綁死前端。
     * 回一個排好序的平面 list，前端要怎麼切都行。
     *
     * @param year        {@code int}——西元年，原樣帶回
     * @param month       {@code int}——月份 1 到 12，原樣帶回
     * @param followUps   {@code List<FollowUps>}——repository 查回來的安排，已排序，不可為 null
     * @param ticketsById {@code Map<Integer, Tickets>}——上面那批安排指向的工單，
     *                    key 是 {@code ticketId}，不可為 null
     * @return {@link CalendarMonthResponse}——events 已轉成 DTO，順序與 {@code followUps} 相同
     */
    public static CalendarMonthResponse from(int year, int month,
                                             List<FollowUps> followUps,
                                             Map<Integer, Tickets> ticketsById) {
        return new CalendarMonthResponse(
                year,
                month,
                followUps.stream()
                        // 正常情況下每筆安排都找得到工單（外鍵擋著）。
                        // 唯一的例外是「兩次查詢中間剛好有人把工單刪掉」——
                        // 那筆安排其實也已經被連帶刪除（ON DELETE CASCADE），
                        // 顯示不出來才是對的，所以濾掉而不是丟例外。
                        .filter(f -> ticketsById.containsKey(f.getTicketId()))
                        .map(f -> CalendarEventResponse.from(f, ticketsById.get(f.getTicketId())))
                        .toList()
        );
    }
}
