package com.poz.CustomerService.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 變更自己的工作狀態，對應 PATCH /api/agents/me/status。
 *
 * {@code @Pattern} 裡刻意沒有 ON_CALL——通話中只能由通話事件觸發。
 * 但這只擋得住「值本身不合法」；「目前已經是 ON_CALL 不准改」要看資料庫現況，由 Service 負責。
 *
 * @param status {@code String}——要切換到的狀態，必填。只能是
 *               ONLINE / BREAK / RESTROOM / LUNCH / MEETING，送 ON_CALL 會被擋回 400。
 *               不吃 agentId：讓呼叫端指定要改誰，等於開一個「任何人都能改別人」的洞
 */
public record UpdateAgentStatusRequest(
        @NotBlank(message = "狀態不可空白")
        @Pattern(
                regexp = "ONLINE|BREAK|RESTROOM|LUNCH|MEETING",
                message = "狀態只能是 ONLINE / BREAK / RESTROOM / LUNCH / MEETING"
        )
        String status
) {
}
