package com.poz.CustomerService.dto.auth;

import com.poz.CustomerService.dto.agent.AgentResponse;

/**
 * 登入成功的回應，對應 POST /api/auth/login。
 *
 * @param token 登入憑證。現階段還沒接 JWT，回寫死的 DEV-TOKEN-NOT-A-REAL-JWT
 * @param agent 登入者的公開資訊，status 一定是 ONLINE
 */
public record LoginResponse(
        String token,
        AgentResponse agent
) {
}
