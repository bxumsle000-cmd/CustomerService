package com.poz.CustomerService.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
@Configuration
public class PasswordConfig {

    /**
     * 提供全專案共用的密碼雜湊工具。
     * <p>
     * 注入之後主要用兩支：{@code encode(明文)} 算雜湊、
     * {@code matches(明文, 雜湊)} 驗密碼。
     * <b>驗密碼一定要用 matches</b>，不能自己 encode 一次再比字串——
     * BCrypt 每次算出來的鹽不同，同一個密碼兩次的雜湊值不會一樣。
     *
     * @return PasswordEncoder——BCrypt 實作，strength 用預設值 10
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
