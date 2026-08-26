package com.poz.CustomerService.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

// 欄位命名規則同 Agents：Java 用 camelCase，資料庫欄位用 snake_case，靠 @Column 對接。
@Data
@Entity
@Table(name="ticket_comments")
public class TicketComments {

// comment_id 在資料庫是 INT IDENTITY(1,1)，號碼由資料庫發，所以要加 @GeneratedValue。
// 少了它，save() 時 Hibernate 會把 null 當成主鍵送進去，SQL Server 會拒絕。
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
@Column(name = "comment_id")
Integer commentId ;

@Column(name = "ticket_id")
Integer ticketId ;

// 留言的客服代號，對應 agents.agent_id（FK_ticket_comments_agents）。
// 可以是 null：代表這是「系統事件」（建單、狀態變更、轉派等由後端自動寫入的紀錄），
// 畫面上顯示為「系統」。所以型別要用 String 而不是原始型別。
//
// 注意：這一欄原本叫 author_id，V1__init_schema.sql 已經改名為 agent_id。
// 但對外的 JSON 仍沿用 authorId / authorName（見 docs/api.md），DTO 轉換時要對應。
@Column(name = "agent_id")
String agentId ;

@Column(name = "content")
String content ;

@Column(name = "created_at")
LocalDateTime createdAt;

@PrePersist
void applyDefaults(){
    if(createdAt==null){
        createdAt = LocalDateTime.now().withNano(0);
    }
}
}
