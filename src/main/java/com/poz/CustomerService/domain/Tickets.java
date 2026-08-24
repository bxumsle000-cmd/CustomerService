package com.poz.CustomerService.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "tickets")
public class Tickets {

    // 這裡跟 Agents 剛好相反：V1__init_schema.sql 裡 ticket_id 是 INT IDENTITY(1,1)，
    // 也就是「流水號交給資料庫產生」，所以一定要加 @GeneratedValue(IDENTITY)。
    // 少了它，Hibernate 會以為主鍵該由我們自己填，INSERT 時送一個 null（或 0）進去，
    // SQL Server 會拒絕寫入 IDENTITY 欄位而報錯。
    //
    // 型別也從 int 改成 Integer：int 沒填時是 0，Hibernate 分不出「還沒存過」還是
    // 「主鍵真的是 0」；Integer 沒填是 null，語意清楚。
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_id")
    private Integer ticket_id;

    @Column(name = "ticket_no")
    private String ticket_no;

    @Column(name = "customer_name")
    private String customer_name;

    @Column(name = "contact_phone")
    private String contact_phone;

    @Column(name = "title")
    private String title;

    // description 在資料庫是 NVARCHAR(MAX)（很長的文字）。
    // 若不特別標註，Hibernate 會照 @Column 預設長度 255 去對應；
    // 我們沒有讓 Hibernate 自動建表（表是 Flyway 建的），所以不會建錯，
    // 但加上 columnDefinition 讓兩邊的意圖一致，之後看程式碼比較不會誤會。
    @Column(name = "description", columnDefinition = "NVARCHAR(MAX)")
    private String description;

    // 狀態白名單：IN_PROGRESS 處理中 / PENDING 待客戶回覆 / RESOLVED 已解決。
    // 資料庫端有 CK_tickets_status 把關，填別的值會被擋下來。
    @Column(name = "status")
    private String status;

    @Column(name = "category")
    private String category;

    // 進線管道白名單：PHONE / EMAIL，由 CK_tickets_channel 把關。
    @Column(name = "channel")
    private String channel;

    // 負責處理的客服代號，對應 agents.agent_id（FK_tickets_agents）。
    // 這裡先用單純的字串欄位，不做 @ManyToOne 關聯：
    // 關聯物件會牽涉到 lazy loading、無限遞迴 toString 等議題，等需要時再改。
    @Column(name = "assignee_id")
    private String assignee_id;

    @Column(name = "follow_up_at")
    private LocalDateTime follow_up_at;

    @Column(name = "created_at")
    private LocalDateTime created_at;

    @Column(name = "updated_at")
    private LocalDateTime updated_at;

    // @PrePersist：Hibernate 在「第一次 INSERT 之前」呼叫，用途跟 Agents 那支一樣——
    // SQL Server 的 DEFAULT 只在 INSERT 完全沒提到該欄位時才生效，
    // 但 Hibernate 會把所有映射欄位都列進 INSERT（等於明確送 NULL），
    // DEFAULT 就被跳過，直接撞上 NOT NULL。所以預設值改由 Java 這邊補。
    //
    // withNano(0)：欄位是 DATETIME2(0) 只存到秒，先自己截掉小數秒，
    // 免得記憶體裡的物件跟資料庫存的值對不起來。
    @PrePersist
    void applyDefaults() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        if (created_at == null) {
            created_at = now;
        }
        if (updated_at == null) {
            updated_at = now;
        }
    }

    // @PreUpdate：Hibernate 在「UPDATE 之前」呼叫。
    // MySQL 可以用 ON UPDATE CURRENT_TIMESTAMP 讓資料庫自動維護 updated_at，
    // 但 SQL Server 沒有這個語法（見 V1__init_schema.sql 開頭的翻譯對照表），
    // 所以改由這裡負責。
    @PreUpdate
    void touchUpdatedAt() {
        updated_at = LocalDateTime.now().withNano(0);
    }
}
