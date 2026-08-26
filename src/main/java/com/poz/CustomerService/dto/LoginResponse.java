package com.poz.CustomerService.dto;

/**
 * 登入成功的回應，對應 POST /api/auth/login。
 *
 * <pre>
 * { "token": "...", "agent": { "agentId": "CSC00001", "name": "林曉明", "status": "ONLINE" } }
 * </pre>
 *
 * 登入失敗一律回 401，而且訊息不要區分「帳號不存在」和「密碼錯誤」——
 * 兩者分開講，等於告訴嘗試入侵的人「這個帳號是存在的」，
 * 讓對方可以先枚舉出有效帳號再專攻密碼。
 *
 * @param token 登入憑證。現階段還沒接 JWT，回的是寫死的 DEV-TOKEN-NOT-A-REAL-JWT
 * @param agent 登入者的公開資訊。其中 status 一定是 ONLINE，因為登入時會重設
 */
public record LoginResponse(
        String token,
        AgentResponse agent
) {
}
