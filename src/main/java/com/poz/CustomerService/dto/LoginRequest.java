package com.poz.CustomerService.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登入請求，對應 POST /api/auth/login。
 *
 * {@code @Size(max = 10)} 是照著資料庫欄位 agents.agent_id NVARCHAR(10) 訂的。
 * 在這裡先擋掉，超長的輸入就不會一路帶到資料庫才炸成 500，
 * 而是乾淨地回一個 400。
 * <p>
 * 注意這些驗證要 Controller 的參數加 {@code @Valid} 才會觸發。
 *
 * @param agentId  客服代號，必填、不可空白、最長 10 字。例：CSC00001
 * @param password 密碼明文，必填、不可空白。後端用 BCrypt 比對 agents.password_hash，
 *                 明文不會被存下來，也不會出現在任何回應裡
 */
public record LoginRequest(
        @NotBlank(message = "客服代號不可空白")
        @Size(max = 10, message = "客服代號長度不可超過 10")
        String agentId,

        @NotBlank(message = "密碼不可空白")
        String password
) {
}
