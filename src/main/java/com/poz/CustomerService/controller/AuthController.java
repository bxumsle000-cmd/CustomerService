package com.poz.CustomerService.controller;

import com.poz.CustomerService.dto.AgentResponse;
import com.poz.CustomerService.dto.LoginRequest;
import com.poz.CustomerService.dto.LoginResponse;
import com.poz.CustomerService.service.AgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 認證相關端點，對應 docs/api.md「二、認證與登入客服」。
 *
 * <h2>Controller 該做什麼、不該做什麼</h2>
 * 只做三件事：收參數、叫 Service、回結果。
 * business logic 一律放 Service，這裡不寫 if-else 判斷，也不碰資料庫。
 * <p>
 * 這樣切的好處是：想知道「登入的規則是什麼」，只要看 AgentService 一個檔案就夠了，
 * 不必在 Controller 和 Service 之間來回翻。
 *
 * <h2>錯誤怎麼回</h2>
 * 這裡看不到任何 try-catch。Service 丟出來的 ApiException 會由
 * {@link com.poz.CustomerService.exception.GlobalExceptionHandler} 統一接住轉成 JSON。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AgentService agentService;

    /**
     * 登入。
     *
     * <b>{@code @Valid} 這個字不能漏。</b>漏了的話 LoginRequest 上的
     * {@code @NotBlank} / {@code @Size} 全部不會執行——不會報錯，也不會有任何提示，
     * 就只是安靜地沒作用，是最難發現的那種問題。
     * <p>
     * {@code @RequestBody} 則是告訴 Spring：參數要從 HTTP 的 body 讀 JSON 反序列化，
     * 不是從網址的 ?key=value 讀。
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return agentService.login(request);
    }

    /**
     * 取得目前登入的客服，供側邊欄與右上角狀態選單顯示。
     *
     * 沒有任何參數——「我是誰」由 Service 的 currentAgentId() 決定。
     * 現階段它回傳寫死的 CSC00001，之後接上 JWT 只要改那一個方法，這裡不用動。
     */
    @GetMapping("/me")
    public AgentResponse me() {
        return agentService.me();
    }
}
