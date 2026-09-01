package com.poz.CustomerService.controller;

import com.poz.CustomerService.dto.agent.AgentResponse;
import com.poz.CustomerService.dto.auth.LoginRequest;
import com.poz.CustomerService.dto.auth.LoginResponse;
import com.poz.CustomerService.service.AgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 認證相關端點：登入，以及取得目前登入的客服。
 * <p>
 * Controller 只做三件事：收參數、叫 Service、回結果。business logic 一律放
 * {@link AgentService}，這裡不寫判斷也不碰資料庫。
 * 也看不到 try-catch——Service 丟的 ApiException 由
 * {@link com.poz.CustomerService.exception.GlobalExceptionHandler} 統一轉成 JSON。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AgentService agentService;

    /**
     * 登入，POST /api/auth/login。
     *
     * @param request {@link LoginRequest}——request body，欄位 {@code agentId} 和 {@code password}。
     *                格式不合由 {@code @Valid} 擋下，回 400 / {@code VALIDATION_ERROR}
     * @return {@link LoginResponse}——200，欄位 {@code token} 和 {@code agent}。
     *         帳密錯誤回 401 / {@code INVALID_CREDENTIALS}
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return agentService.login(request);
    }

    /**
     * 取得目前登入的客服，供側邊欄與右上角狀態選單顯示。
     *
     * 沒有任何參數——「我是誰」由 Service 的 currentAgentId() 決定。
     *
     * @return {@link AgentResponse}——200，agentId / name / status。
     *         查不到人回 404 / {@code AGENT_NOT_FOUND}
     */
    @GetMapping("/me")
    public AgentResponse me() {
        return agentService.me();
    }
}
