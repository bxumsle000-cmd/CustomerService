package com.poz.CustomerService.dto.agent;

import jakarta.validation.constraints.NotNull;

/**
 * 通話開始／結束時前端送上來的內容，對應 PATCH /api/agents/me/call。
 * <p>
 * {@code ON_CALL} 不接受客服手動設定，所以它走這一支，不走
 * {@link UpdateAgentStatusRequest}——那支的白名單裡沒有 ON_CALL。
 *
 * @param inCall 是不是正在通話中，必填。
 *               {@code true} 代表接聽、{@code false} 代表掛斷
 */
public record UpdateCallStateRequest(
        @NotNull(message = "通話狀態不可為空")
        Boolean inCall
) {
}
