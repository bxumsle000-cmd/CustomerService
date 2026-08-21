package com.poz.CustomerService.domain;


import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "agents")
public class Agents {
    // agent_id 是「業務主鍵」：由人決定的客服代號（CSC00001），不是資料庫自增流水號。
    // 所以這裡絕對不能加 @GeneratedValue(strategy = GenerationType.IDENTITY)，
    // 那代表「交給資料庫產生」，但 V1__init_schema.sql 裡 agent_id 是 NVARCHAR(10)，
    // 並沒有 IDENTITY(1,1)。兩邊對不起來的話，save() 新客服時 Hibernate 會誤判成
    // 「這是已存在的資料」而走 merge，接著在資料庫找不到，丟出 StaleObjectStateException。
    @Id
    @Column(name = "agent_id")
    String agent_id ;

    @Column(name = "name")
    String name;

    @Column(name = "password_hash")
    private String password_hash ;

    @Column(name = "status")
    private String status ;

    @Column(name = "created_at")
    private LocalDateTime created_at ;

    // @PrePersist：Hibernate 在「第一次 INSERT 這筆資料之前」會自動呼叫這個方法。
    // 為什麼需要它？因為 V1__init_schema.sql 裡 created_at / status 雖然有 DEFAULT，
    // 但 SQL Server 的 DEFAULT 只在「INSERT 語句完全沒提到該欄位」時才生效。
    // Hibernate 產生的 INSERT 會把所有映射欄位都列出來，等於明確送了一個 NULL 進去，
    // DEFAULT 就被跳過，直接撞上 NOT NULL：
    //     Cannot insert the value NULL into column 'created_at' ... INSERT fails.
    // 所以改由 Java 這邊自己補值。
    //
    // 兩個 if 都要判斷 null：呼叫端有指定就尊重呼叫端的值，沒指定才套預設。
    @PrePersist
    void applyDefaults() {
        if (created_at == null) {
            // withNano(0)：資料庫欄位是 DATETIME2(0)，只存到「秒」，小數秒會被截掉。
            // 不砍掉的話，物件裡是 15:35:31.431251400、資料庫裡卻是 15:35:31，
            // 之後重新查出來的值會跟存進去前的物件對不起來，debug 時很容易看花眼。
            created_at = LocalDateTime.now().withNano(0);
        }
        if (status == null) {
            status = "ONLINE";
        }
    }
}
