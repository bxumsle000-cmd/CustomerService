package com.poz.CustomerService.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 變更自己的工作狀態，對應 PATCH /api/agents/me/status。不吃 agentId。
 *
 * @param status 要切換到的狀態，必填。只能是
 *               ONLINE / BREAK / RESTROOM / LUNCH / MEETING，送 ON_CALL 會被擋回 400
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
