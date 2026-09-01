package com.poz.CustomerService.dto.calendar;

import com.poz.CustomerService.entity.FollowUps;
import com.poz.CustomerService.entity.Tickets;

import java.time.LocalDateTime;

/**
 * 行事曆上的「一格事件」，也就是一筆回電安排。
 *
 * <h2>資料來自兩張表</h2>
 * <ul>
 *   <li>{@link FollowUps}——{@code followUpAt}（什麼時候回電）、{@code note}（個人備註）</li>
 *   <li>{@link Tickets}——{@code ticketNo} / {@code title} / {@code customerName} /
 *       {@code status}（格子上要顯示什麼、點下去去哪裡）</li>
 * </ul>
 * 顯示用的那四個欄位<b>刻意不複製一份存進 follow_ups</b>，每次都查 tickets 拿現在的值。
 * 尤其是 {@code status}：它會從 IN_PROGRESS 變成 RESOLVED，前端還要靠它決定事件顏色。
 * 存成快照的話，工單結案了行事曆那格還會停在「處理中」，而且永遠不會自己更新。
 * 通則：會變的東西用查的，不會變的才複製。
 *
 * @param ticketNo     {@code String}——對外的工單編號，格式 TK-XXXXXX。
 *                     點擊行事曆上的事件要導到工單詳情，用的就是它
 * @param title        {@code String}——工單主旨，行事曆格子裡顯示的文字
 * @param customerName {@code String}——客戶姓名，<b>可為 null</b>
 * @param status       {@code String}——IN_PROGRESS / PENDING / RESOLVED，
 *                     前端用來決定事件的顏色（已解決的通常畫成灰色）
 * @param followUpAt   {@code LocalDateTime}——排定的回電時間。
 *                     <b>不會是 null</b>：{@code follow_ups.follow_up_at} 是 NOT NULL，
 *                     沒有時間就不會有這一列（取消排定是把整列刪掉）
 * @param note         {@code String}——個人備註，<b>可為 null</b>（沒寫就是 null）。
 *                     只有安排的主人看得到，不會出現在工單的處理記錄 timeline 上
 */
public record CalendarEventResponse(
        String ticketNo,
        String title,
        String customerName,
        String status,
        LocalDateTime followUpAt,
        String note
) {

    /**
     * 從兩個 entity 合成一格事件，寫法與用意同 {@code TicketListItemResponse.from}：
     * 「entity 的哪些欄位可以出去」只有這一個地方說了算。
     * <p>
     * 兩個參數必須是<b>同一筆安排</b>的兩半，也就是
     * {@code followUp.getTicketId().equals(ticket.getTicketId())}。
     * 這裡不檢查——配對是 Service 的責任（見
     * {@code CalendarMonthResponse.from}），在這裡再驗一次只是把同一件事做兩遍。
     *
     * @param followUp {@link FollowUps}——回電安排，不可為 null
     * @param ticket   {@link Tickets}——{@code followUp.ticketId} 指向的那張工單，不可為 null
     * @return {@link CalendarEventResponse}——行事曆用得到的六個欄位
     */
    public static CalendarEventResponse from(FollowUps followUp, Tickets ticket) {
        return new CalendarEventResponse(
                ticket.getTicketNo(),
                ticket.getTitle(),
                ticket.getCustomerName(),
                ticket.getStatus(),
                followUp.getFollowUpAt(),
                followUp.getNote()
        );
    }
}
