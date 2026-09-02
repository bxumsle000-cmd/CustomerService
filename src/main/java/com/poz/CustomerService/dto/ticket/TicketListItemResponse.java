package com.poz.CustomerService.dto.ticket;

import com.poz.CustomerService.entity.Tickets;

import java.time.LocalDateTime;

/**
 * 首頁（派件列表）表格裡的「一列工單」。
 *
 * @param ticketNo     對外的工單編號，格式 TK-XXXXXX
 * @param title        工單主旨
 * @param customerName 客戶姓名，可為 null
 * @param contactPhone 客戶聯絡電話，可為 null
 * @param status       IN_PROGRESS / PENDING / RESOLVED
 * @param assigneeId   負責客服的代號，例如 CSC00001
 * @param createdAt    建立時間，時間篩選依據
 * @param updatedAt    最後更新時間，清單排序依據
 */
public record TicketListItemResponse(
        String ticketNo,
        String title,
        String customerName,
        String contactPhone,
        String status,
        String assigneeId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 從 entity 轉成 DTO。
     *
     * @param ticket 來源 entity，不可為 null
     * @return 首頁表格與候選清單用得到的八個欄位
     */
    public static TicketListItemResponse from(Tickets ticket) {
        return new TicketListItemResponse(
                ticket.getTicketNo(),
                ticket.getTitle(),
                ticket.getCustomerName(),
                ticket.getContactPhone(),
                ticket.getStatus(),
                ticket.getAssigneeId(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }
}
