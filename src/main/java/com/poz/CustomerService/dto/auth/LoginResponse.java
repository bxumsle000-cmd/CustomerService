package com.poz.CustomerService.dto.auth;

import com.poz.CustomerService.dto.agent.AgentResponse;

/**
 * 登入成功的回應，對應 POST /api/auth/login。
 *
 * <pre>
 * { "token": "...", "agent": { "agentId": "CSC00001", "name": "林曉明", "status": "ONLINE" } }
 * </pre>
 *
 * 登入失敗一律回 401，且不區分「帳號不存在」和「密碼錯誤」，免得對方能枚舉出有效帳號。
 *
 * @param token {@code String}——登入憑證。現階段還沒接 JWT，回寫死的 DEV-TOKEN-NOT-A-REAL-JWT
 * @param agent {@link AgentResponse}——登入者的公開資訊，status 一定是 ONLINE
 */
public record LoginResponse(
        String token,
        AgentResponse agent
) {
}
