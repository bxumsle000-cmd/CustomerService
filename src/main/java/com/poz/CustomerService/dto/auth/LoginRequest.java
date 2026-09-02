package com.poz.CustomerService.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登入請求，對應 POST /api/auth/login。
 *
 * @param agentId  客服代號，必填、最長 10 字。例：CSC00001
 * @param password 密碼明文，必填。後端用 BCrypt 比對，不會被存下來或回傳
 */
public record LoginRequest(
        @NotBlank(message = "客服代號不可空白")
        @Size(max = 10, message = "客服代號長度不可超過 10")
        String agentId,

        @NotBlank(message = "密碼不可空白")
        String password
) {
}
