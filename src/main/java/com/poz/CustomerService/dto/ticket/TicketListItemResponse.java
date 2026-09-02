package com.poz.CustomerService.dto.ticket;

import com.poz.CustomerService.entity.Tickets;

import java.time.LocalDateTime;

/**
 * 首頁（派件列表）表格裡的「一列工單」。
 *
 * @param ticketNo     {@code String}——對外的工單編號，格式 TK-XXXXXX。點擊開詳情用的就是它
 * @param title        {@code String}——工單主旨。首頁表格不顯示它，
 *                     是給「挑一張單排入行事曆」那種候選清單用的——
 *                     只給單號選不出來要排哪一張
 * @param customerName {@code String}——通話中確認的客戶姓名，<b>可為 null</b>
 * @param contactPhone {@code String}——客戶聯絡電話，<b>可為 null</b>
 * @param status       {@code String}——IN_PROGRESS / PENDING / RESOLVED，前端轉成彩色標籤
 * @param assigneeId   {@code String}——負責客服的代號，例如 CSC00001。
 *                     表格直接顯示代號、不顯示姓名，所以不必多查一次 agents
 * @param createdAt    {@code LocalDateTime}——建立時間。時間篩選依據
 * @param updatedAt    {@code LocalDateTime}——最後更新時間。清單排序依據；
 *                     從沒被動過的工單，這個值等於 {@code createdAt}
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
     * <p>
     * 轉換集中寫在這裡、而不是散在各個 Service，是為了讓「Tickets 的哪些欄位可以出去」
     * 只有這一個地方說了算。首頁列表和通話工作台歷史紀錄都要做這個轉換，
     * 散開寫的話兩邊很容易挑得不一樣；之後 Tickets 再多幾個不該外洩的欄位，
     * 也不必擔心某支 Service 忘記過濾。
     * <p>
     * 八個欄位全部來自 {@link Tickets} 這一張表，不需要 join，
     * 所以這支方法自己就能完成轉換（對比
     * {@code TicketCommentResponse} 的 agentName 要另外查 agents，就沒辦法只靠 entity）。
     *
     * @param ticket {@link Tickets}——來源 entity，不可為 null（查不到請在 Service 就丟 404）
     * @return {@link TicketListItemResponse}——首頁表格與候選清單用得到的八個欄位
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
