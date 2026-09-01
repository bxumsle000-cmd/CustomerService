package com.poz.CustomerService.dto.calendar;

import com.poz.CustomerService.entity.Tickets;

import java.time.LocalDateTime;

/**
 * 行事曆上的「一格事件」，也就是一張排了回電時間的工單。
 * <p>
 * 目前行事曆的事件來源只有工單的 {@code follow_up_at}，所以這個 DTO 就是
 * {@link Tickets} 的一個子集合；之後若要放請假、教育訓練那種跟工單無關的事件，
 * 得另開資料表，這裡也要跟著調整。
 *
 * @param ticketNo     {@code String}——對外的工單編號，格式 TK-XXXXXX。
 *                     點擊行事曆上的事件要導到工單詳情，用的就是它
 * @param title        {@code String}——工單主旨，行事曆格子裡顯示的文字
 * @param customerName {@code String}——客戶姓名，<b>可為 null</b>
 * @param status       {@code String}——IN_PROGRESS / PENDING / RESOLVED，
 *                     前端用來決定事件的顏色（已解決的通常畫成灰色）
 * @param followUpAt   {@code LocalDateTime}——排定的回電時間。
 *                     <b>在這個 DTO 裡不會是 null</b>——會被查出來的前提就是它有值
 */
public record CalendarItemResponse(
        String ticketNo,
        String title,
        String customerName,
        String status,
        LocalDateTime followUpAt
) {

    /**
     * 從 entity 轉成 DTO，寫法與用意同 {@code TicketListItemResponse.from}：
     * 「Tickets 的哪些欄位可以出去」只有這一個地方說了算。
     * <p>
     * 五個欄位全部來自 {@link Tickets} 這一張表，不需要 join，所以這支方法自己就能完成轉換。
     *
     * @param ticket {@link Tickets}——來源 entity，不可為 null（查不到請在 Service 就丟 404）
     * @return {@link CalendarItemResponse}——行事曆用得到的五個欄位
     */
    public static CalendarItemResponse from(Tickets ticket) {
        return new CalendarItemResponse(
                ticket.getTicketNo(),
                ticket.getTitle(),
                ticket.getCustomerName(),
                ticket.getStatus(),
                ticket.getFollowUpAt()
        );
    }
}
