package com.poz.CustomerService.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name="ticket_comments")
public class TicketComments {

@Id
@Column(name = "comment_id")
Integer comment_id ;

@Column(name = "ticket_id")
Integer ticket_id ;

@Column(name = "agent_id")
String agent_id ;

@Column(name = "content")
String content ;

@Column(name = "created_at")
LocalDateTime created_at;

@PrePersist
void applyDefaults(){
    if(created_at==null){
        created_at = LocalDateTime.now().withNano(0);
    }
}
}
