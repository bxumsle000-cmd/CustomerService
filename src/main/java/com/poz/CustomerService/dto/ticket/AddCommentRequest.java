package com.poz.CustomerService.dto.ticket;

import jakarta.validation.constraints.NotBlank;

/**
 * 新增一筆處理記錄時前端送上來的內容，對應 POST /api/tickets/{ticketNo}/comments。
 * <p>
 * 留言者不由前端指定，後端取登入身分。
 *
 * @param content 留言內容，必填。走 request body 而不是 query string，
 *                內容才可以有換行與各種符號
 */
public record AddCommentRequest(
        @NotBlank(message = "留言內容不可空白")
        String content
) {
}
