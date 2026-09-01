package com.poz.CustomerService.dto.ticket;

import com.poz.CustomerService.entity.Tickets;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 單張工單的完整內容，對應工單詳情。點開列表某一列之後看到的那一頁。
 * <p>
 * 跟 {@link TicketListItemResponse} 的關係：那支是「表格裡的一列」，只有七個欄位，
 * 為了讓列表輕一點；這支是「點進去之後的整頁」，欄位全給，另外多帶了底下的處理記錄
 * （{@code comments}，來自另一張表）。
 * <p>
 * <b>沒有 ticketId</b>：那是內部流水號，對外一律用 {@code ticketNo}。
 *
 * @param ticketNo     {@code String}——對外的工單編號，格式 TK-XXXXXX
 * @param title        {@code String}——主旨
 * @param description  {@code String}——問題描述／通話摘要，<b>可為 null</b>
 * @param status       {@code String}——IN_PROGRESS / PENDING / RESOLVED
 * @param category     {@code String}——問題分類
 * @param channel      {@code String}——進線管道，PHONE / EMAIL / Agent
 * @param customerName {@code String}——客戶姓名，<b>可為 null</b>
 * @param contactPhone {@code String}——客戶聯絡電話，<b>可為 null</b>
 * @param assigneeId   {@code String}——負責客服的代號
 * @param followUpAt   {@code LocalDateTime}——行事曆上的跟進時間，<b>可為 null</b>（沒排就是 null）
 * @param createdAt    {@code LocalDateTime}——建立時間
 * @param updatedAt    {@code LocalDateTime}——最後更新時間。等於 createdAt 代表從沒被動過
 * @param comments     {@code List<TicketCommentResponse>}——處理記錄，<b>由舊到新</b>。
 *                     沒有留言時是空 list，不會是 null
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
        LocalDateTime followUpAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<TicketCommentResponse> comments
) {

    /**
     * 從 entity 加上查好的處理記錄，組成詳情。
     * <p>
     * 前十二個欄位照抄 {@link Tickets}，timeline 這支方法生不出來——要查另一張表，
     * 所以由 Service 準備好傳進來。
     * <p>
     * {@code comments} 用 {@link List#copyOf} 複製一份存起來。record 只保證「欄位不能被換掉」，
     * 不保證「欄位指到的 list 不能被改」——直接存傳進來的 list，呼叫端之後對它 add、remove，
     * 這個 DTO 裡的內容會跟著變。{@code copyOf} 出來的是唯讀副本，從外面改不動。
     *
     * @param ticket   {@link Tickets}——來源 entity，不可為 null（查不到請在 Service 就丟 404）
     * @param comments {@code List<TicketCommentResponse>}——已排序的 timeline，
     *                 不可為 null（沒有就傳空 list）
     * @return {@link TicketDetailResponse}——詳情頁需要的全部內容
     */
    public static TicketDetailResponse from(Tickets ticket, List<TicketCommentResponse> comments) {
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
                ticket.getFollowUpAt(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                List.copyOf(comments)
        );
    }
}
