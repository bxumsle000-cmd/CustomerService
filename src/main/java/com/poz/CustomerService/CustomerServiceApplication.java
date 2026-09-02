package com.poz.CustomerService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 電話客服工單系統進入點。
 * <p>
 * 套件配置：{@code entity/} 對應資料表、{@code repository/} 資料存取、
 * {@code service/} 業務邏輯、{@code controller/} HTTP 端點、{@code dto/} 對外的資料形狀。
 */
//http://localhost:8080/swagger-ui/index.html
@SpringBootApplication
public class CustomerServiceApplication {

    /**
     * 啟動整個 Spring Boot 應用程式。
     *
     * @param args 命令列參數，原封不動交給 Spring，例如 {@code --server.port=8081}
     */
    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}
