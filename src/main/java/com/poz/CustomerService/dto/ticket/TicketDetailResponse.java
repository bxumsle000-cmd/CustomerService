package com.poz.CustomerService.dto.ticket;

import com.poz.CustomerService.entity.Tickets;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 單張工單的完整內容，對應工單詳情頁。不含內部流水號 ticketId。
 *
 * @param ticketNo     對外的工單編號，格式 TK-XXXXXX
 * @param title        主旨
 * @param description  問題描述／通話摘要，可為 null
 * @param status       IN_PROGRESS / PENDING / RESOLVED
 * @param category     問題分類
 * @param channel      進線管道，PHONE / Agent
 * @param customerName 客戶姓名，可為 null
 * @param contactPhone 客戶聯絡電話，可為 null
 * @param assigneeId   負責客服的代號
 * @param createdAt    建立時間
 * @param updatedAt    最後更新時間
 * @param comments     處理記錄，由舊到新；沒有留言時是空 list
 */
public record TicketDetailResponse(
        String ticketNo,
        String title,
        String description,
        String status,
        String category,
        String channel,
        String customerName,
        String contactPhone,
        String assigneeId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<TicketCommentResponse> comments
) {

    /**
     * 從 entity 加上查好的處理記錄，組成詳情；comments 會複製成唯讀副本。
     *
     * @param ticket   來源 entity，不可為 null
     * @param comments 已排序的 timeline，不可為 null（沒有就傳空 list）
     * @return 詳情頁需要的全部內容
     */
    public static TicketDetailResponse from(Tickets ticket,
                                            List<TicketCommentResponse> comments) {
        return new TicketDetailResponse(
                ticket.getTicketNo(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getCategory(),
                ticket.getChannel(),
                ticket.getCustomerName(),
                ticket.getContactPhone(),
                ticket.getAssigneeId(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                List.copyOf(comments)
        );
    }
}
