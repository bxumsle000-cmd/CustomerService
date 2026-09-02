package com.poz.CustomerService.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
@Configuration
public class PasswordConfig {

    /**
     * 提供全專案共用的密碼雜湊工具。
     * 主要用 {@code encode(明文)} 算雜湊、{@code matches(明文, 雜湊)} 驗密碼。
     *
     * @return BCrypt 實作的 PasswordEncoder，strength 用預設值 10
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
