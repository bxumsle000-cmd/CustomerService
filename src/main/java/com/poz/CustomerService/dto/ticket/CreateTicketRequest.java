package com.poz.CustomerService.dto.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 建立工單時前端送上來的表單內容，對應 POST /api/tickets。
 * <p>
 * 不含 ticketNo、ticketId 與建立者，那三個都由後端決定。
 *
 * @param title        主旨，必填，最長 50 字
 * @param customerName 客戶姓名，必填
 * @param contactPhone 聯絡電話，必填
 * @param category     問題分類，必填
 * @param assigneeId   轉派對象的客服代號，選填，沒送就是自己處理
 * @param description  問題描述／通話摘要，必填
 * @param status       通話結果，必填，只能是 IN_PROGRESS / PENDING / RESOLVED
 * @param channel      派單來源，必填，只能是 PHONE / Agent
 */
public record CreateTicketRequest(
    @NotBlank(message = "不可以沒有主旨")
    @Size(max = 50, message = "主旨長度不可超過 50")
    String title,

    @NotBlank(message = "不可以沒有姓名")
    String customerName,

    @NotBlank(message = "不可以沒有聯絡電話")
    String contactPhone,

    @NotBlank(message = "不可以沒有分類")
    String category,

    String assigneeId,

    @NotBlank(message = "不可以沒有通話摘要")
    String description,

    @NotBlank(message = "不可以沒有通話結果")
    @Pattern(
            regexp = "IN_PROGRESS|PENDING|RESOLVED",
            message = "通話結果只能是 IN_PROGRESS / PENDING / RESOLVED"
    )
    String status,

    @NotBlank(message = "不可以沒有派單來源")
    @Pattern(
            regexp = "PHONE|Agent",
            message = "派單來源只能是 PHONE / Agent"
    )
    String channel
) {
}
