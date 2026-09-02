package com.poz.CustomerService.dto;

/**
 * 全專案統一的錯誤回應格式。
 *
 * @param code    錯誤代號，固定大寫英文，例如 INVALID_CREDENTIALS，給程式判斷用
 * @param message 給人看的中文訊息，例如「客服代號或密碼錯誤」，前端直接顯示
 */
public record ErrorResponse(
        String code,
        String message
) {
}
