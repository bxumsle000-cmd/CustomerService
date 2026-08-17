package com.poz.CustomerService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 電話客服工單系統 進入點。
 *
 * @SpringBootApplication 會從這個類別所在的套件（com.poz.customerservice）
 * 開始往下掃描，所以之後新增的 controller / service / repository
 * 都要放在這個套件底下，Spring 才找得到。
 */
@SpringBootApplication
public class CustomerServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}
