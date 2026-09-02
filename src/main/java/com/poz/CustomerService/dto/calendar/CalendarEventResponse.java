package com.poz.CustomerService.dto.calendar;

import com.poz.CustomerService.entity.FollowUps;
import com.poz.CustomerService.entity.Tickets;

import java.time.LocalDateTime;

/**
 * 行事曆上的「一格事件」，也就是一筆回電安排。
 * 資料來自 {@link FollowUps} 與 {@link Tickets} 兩張表。
 *
 * @param followUpId   安排流水號，改期／取消時原樣送回後端，<b>不是</b>工單編號
 * @param ticketNo     對外的工單編號，格式 TK-XXXXXX
 * @param title        工單主旨
 * @param customerName 客戶姓名，可為 null
 * @param contactPhone 客戶聯絡電話，可為 null
 * @param status       IN_PROGRESS / PENDING / RESOLVED，前端用來決定事件顏色
 * @param followUpAt   排定的回電時間，不會是 null
 * @param note         個人備註，可為 null，只有安排的主人看得到
 */
public record CalendarEventResponse(
        Integer followUpId,
        String ticketNo,
        String title,
        String customerName,
        String contactPhone,
        String status,
        LocalDateTime followUpAt,
        String note
) {

    /**
     * 從兩個 entity 合成一格事件；兩個參數必須是同一筆安排的兩半，這裡不檢查。
     *
     * @param followUp 回電安排，不可為 null，必須是已經存過的
     * @param ticket   {@code followUp.ticketId} 指向的那張工單，不可為 null
     * @return 行事曆用得到的八個欄位
     */
    public static CalendarEventResponse from(FollowUps followUp, Tickets ticket) {
        return new CalendarEventResponse(
                followUp.getFollowUpId(),
                ticket.getTicketNo(),
                ticket.getTitle(),
                ticket.getCustomerName(),
                ticket.getContactPhone(),
                ticket.getStatus(),
                followUp.getFollowUpAt(),
                followUp.getNote()
        );
    }
}
