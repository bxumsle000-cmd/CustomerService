package com.poz.CustomerService.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登入請求，對應 POST /api/auth/login。
 *
 * 註解上的驗證要 Controller 的參數加 {@code @Valid} 才會觸發，
 * 先在這裡擋掉，超長輸入就不會一路帶到資料庫才炸成 500。
 *
 * @param agentId  {@code String}——客服代號，必填、不可空白、最長 10 字。例：CSC00001
 * @param password {@code String}——密碼明文，必填、不可空白。
 *                 後端用 BCrypt 比對，明文不會被存下來，也不會出現在任何回應裡
 */
public record LoginRequest(
        @NotBlank(message = "客服代號不可空白")
        @Size(max = 10, message = "客服代號長度不可超過 10")
        String agentId,

        @NotBlank(message = "密碼不可空白")
        String password
) {
}
