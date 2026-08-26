package com.poz.CustomerService.dto;

/**
 * 統一的錯誤回應格式，對應 docs/api.md「一、共通約定」那一節。
 *
 * <pre>
 * { "code": "INVALID_STATUS_TRANSITION", "message": "無法從「已解決」變更為「待客戶回覆」" }
 * </pre>
 *
 * code 給程式判斷、message 給人看。兩者分開的好處是：訊息文案之後要改、要翻譯，
 * 都不會影響前端的判斷邏輯。
 *
 * @param code    錯誤代號，固定的大寫英文，例如 INVALID_CREDENTIALS、AGENT_NOT_FOUND。
 *                前端用它來決定要做什麼（例如導回登入頁）
 * @param message 給人看的中文訊息，例如「客服代號或密碼錯誤」。前端直接顯示，不要拿來判斷
 */
public record ErrorResponse(
        String code,
        String message
) {
}
