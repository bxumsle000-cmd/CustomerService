package com.poz.CustomerService.dto;

/**
 * 全專案統一的錯誤回應格式。
 *
 * <pre>
 * { "code": "INVALID_STATUS_TRANSITION", "message": "無法從「已解決」變更為「待客戶回覆」" }
 * </pre>
 *
 * code 給程式判斷、message 給人看。分開的好處是文案之後要改或翻譯，都不影響前端判斷邏輯。
 *
 * @param code    {@code String}——錯誤代號，固定大寫英文，例如 INVALID_CREDENTIALS。
 *                前端用它決定要做什麼（例如導回登入頁）
 * @param message {@code String}——給人看的中文訊息，例如「客服代號或密碼錯誤」。
 *                前端直接顯示，不要拿來判斷
 */
public record ErrorResponse(
        String code,
        String message
) {
}
