package com.poz.CustomerService.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

// 欄位命名規則同 Agents：Java 用 camelCase，資料庫欄位用 snake_case，靠 @Column 對接。
@Data
@Entity
@Table(name="ticket_comments")
public class TicketComments {

@Id
@Column(name = "comment_id")
Integer commentId ;

@Column(name = "ticket_id")
Integer ticketId ;

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
