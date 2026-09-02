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
 * business logic 一律放 {@link AgentService}。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AgentService agentService;

    /**
     * 登入，POST /api/auth/login。
     *
     * @param request request body，欄位 {@code agentId} 和 {@code password}
     * @return 200，欄位 {@code token} 和 {@code agent}；
     *         格式不合回 400、帳密錯誤回 401 / {@code INVALID_CREDENTIALS}
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return agentService.login(request);
    }

    /**
     * 取得目前登入的客服，GET /api/auth/me。沒有參數，身分由 Service 決定。
     *
     * @return 200，agentId / name / status；查不到人回 404 / {@code AGENT_NOT_FOUND}
     */
    @GetMapping("/me")
    public AgentResponse me() {
        return agentService.me();
    }
}
