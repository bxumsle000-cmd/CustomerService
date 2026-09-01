package com.poz.CustomerService.dto.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 變更工單狀態，對應 PATCH /api/tickets/{ticketNo}/status。
 *
 * 這裡只擋得住「值本身不合法」。
 * 「RESOLVED 不能直接跳 PENDING」這種要看工單目前是什麼狀態，
 * 得先查資料庫才知道，DTO 拿不到那個資訊，由 Service 的狀態機負責。
 *
 * @param status {@code String}——要改成的新狀態，必填。
 *               只能是 IN_PROGRESS / PENDING / RESOLVED。
 *               不吃 ticketNo：那是「改哪一張單」的身分，走路徑不走 body
 */
public record ChangeStatusRequest(
        @NotBlank(message = "狀態不可空白")
        @Pattern(
                regexp = "IN_PROGRESS|PENDING|RESOLVED",
                message = "狀態只能是 IN_PROGRESS / PENDING / RESOLVED"
        )
        String status
) {
}
