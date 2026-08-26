package com.poz.CustomerService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 電話客服工單系統 進入點。
 *
 * {@code @SpringBootApplication} 會從這個類別所在的套件（com.poz.CustomerService）
 * 開始往下掃描，所以之後新增的 controller / service / repository
 * 都要放在這個套件底下，Spring 才找得到。
 *
 * 目前的套件配置：
 *   domain/      entity，對應資料表。※ 絕對不要出現在 controller 的方法簽章上
 *   repository/  資料存取
 *   service/     業務邏輯（狀態機、交易邊界都放這裡）
 *   controller/  HTTP 端點
 *   dto/         對外的資料形狀。entity 一律轉成 DTO 才能回傳，
 *                否則 Agents.passwordHash 這種欄位會直接外洩給瀏覽器
 *
 * 其中 dto/ 裡的類別不需要被掃描——它們只是單純的資料容器，
 * 由你自己 new 或由 Jackson 反序列化產生，不是 Spring 管理的 bean。
 */
@SpringBootApplication
public class CustomerServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}
