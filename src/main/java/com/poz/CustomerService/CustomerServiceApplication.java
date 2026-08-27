package com.poz.CustomerService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 電話客服工單系統 進入點。
 *
 * <h2>套件配置</h2>
 * <ul>
 *   <li>{@code entity/}——對應資料表。<b>絕對不要出現在 controller 的方法簽章上</b></li>
 *   <li>{@code repository/}——資料存取</li>
 *   <li>{@code service/}——業務邏輯，狀態機與交易邊界都放這裡</li>
 *   <li>{@code controller/}——HTTP 端點</li>
 *   <li>{@code dto/}——對外的資料形狀。entity 一律轉成 DTO 才能回傳，
 *       否則 Agents.passwordHash 這種欄位會直接外洩給瀏覽器</li>
 * </ul>
 * {@code @SpringBootApplication} 從這個類別所在的套件往下掃描，
 * 所以新增的 controller / service / repository 都要放在底下 Spring 才找得到。
 */
@SpringBootApplication
public class CustomerServiceApplication {

    /**
     * 啟動整個 Spring Boot 應用程式。
     *
     * @param args {@code String[]}——命令列參數，原封不動交給 Spring；
     *             例如 {@code --server.port=8081} 可蓋掉設定檔裡的通訊埠
     */
    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}
