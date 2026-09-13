package com.poz.CustomerService.dto.ticket;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 工單列表的篩選條件，對應 GET /api/tickets 的 query 參數。
 * <p>
 * 這不是 request body，Controller 端不加 {@code @RequestBody}，Spring 會把
 * <b>同名的 query 參數</b>自動對到這裡的欄位（{@code ?status=PENDING&contactPhone=0912...}）。
 * 沒帶的參數就是 {@code null}，代表「這一項不篩」。所以不管前端要用哪幾個條件查，
 * 都是同一支 API、同一個物件，只是有值的欄位不一樣。
 * <p>
 * 之後要多一個篩選條件，只要在這裡加一個欄位，再到 Repository 的查詢補一行，
 * Controller 的方法簽名完全不用動。
 *
 * @param ticketNo     工單編號，完整的 TK-XXXXXX；null 表示不篩
 * @param customerName 客戶姓名，要連稱謂一起打；null 表示不篩
 * @param contactPhone 聯絡電話，完整號碼；null 表示不篩
 * @param assigneeId   負責客服代號；null 表示不篩
 * @param status       處理狀態，IN_PROGRESS / PENDING / RESOLVED；null 表示不篩
 * @param createdFrom  建立時間區間的起點（含），格式 2026-09-01T00:00:00；null 表示不限起點。
 *                     「近 7 天」由前端自己換算成絕對時間
 * @param createdTo    建立時間區間的終點（含），格式 2026-09-30T23:59:59；null 表示不限終點。
 *                     要查整天記得打到 23:59:59，只打日期會被當成當天 00:00:00
 */
public record TicketSearchRequest(
        String ticketNo,
        String customerName,
        String contactPhone,
        String assigneeId,
        String status,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime createdFrom,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime createdTo
) {

    /** 什麼都不篩，等同於「全部」。測試或程式內部要撈全部時用。 */
    public static TicketSearchRequest empty() {
        return new TicketSearchRequest(null, null, null, null, null, null, null);
    }
}
