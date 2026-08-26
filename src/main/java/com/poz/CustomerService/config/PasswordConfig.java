package com.poz.CustomerService.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密碼雜湊工具的 bean。
 *
 * <h2>為什麼要自己宣告，不是 Spring 自動給的</h2>
 * pom.xml 裡只有 spring-security-crypto——那是一包「純工具」，只提供 BCrypt 等演算法，
 * 不含任何自動組態。會自動幫你準備 PasswordEncoder 的是
 * spring-boot-starter-security，那一包還會順便把整個網站鎖起來、
 * 產生登入頁、要求 CSRF token，現階段不需要。
 * 所以這裡自己宣告一個就好。
 *
 * <h2>為什麼回傳型別寫 PasswordEncoder 而不是 BCryptPasswordEncoder</h2>
 * 注入的地方只依賴介面，將來要換演算法（例如 Argon2）只要改這一行。
 *
 * <h2>strength 用預設值 10</h2>
 * V2__seed_agents.sql 裡那三個雜湊就是用 strength 10 算出來的，
 * 這裡若改成別的數字，舊的雜湊還是驗得過（強度寫在雜湊字串裡），
 * 但之後新建的帳號會用新強度，兩者混在一起不好追。
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
