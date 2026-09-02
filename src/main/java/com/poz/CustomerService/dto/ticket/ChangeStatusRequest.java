package com.poz.CustomerService.dto.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 變更工單狀態時前端送上來的內容。合不合法的狀態轉換由 Service 的狀態機負責。
 *
 * @param status 要改成的新狀態，必填，只能是 IN_PROGRESS / PENDING / RESOLVED
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
