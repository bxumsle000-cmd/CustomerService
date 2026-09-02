package com.poz.CustomerService.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 工單的一則留言／處理記錄，對應資料表 {@code ticket_comments}。
 * <ul>
 *   <li><b>必填</b>：{@code ticketId}、{@code content}</li>
 *   <li><b>可為 null</b>：{@code agentId}——null 代表系統自動寫的紀錄</li>
 *   <li><b>不要自己填</b>：{@code commentId}、{@code createdAt}</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name="ticket_comments")
public class TicketComments {

/**
 * 留言流水號，主鍵。{@code ticket_comments.comment_id}，INT IDENTITY。
 * <b>新增時不要填</b>，號碼由資料庫發。
 */
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
@Column(name = "comment_id")
Integer commentId ;

/**
 * 這則留言屬於哪張工單。{@code ticket_comments.ticket_id}，
 * 外鍵指向 {@code tickets.ticket_id}，號碼<b>必須真的存在</b>。
 */
@Column(name = "ticket_id")
Integer ticketId ;

/**
 * 留言者的客服代號。{@code ticket_comments.agent_id}，外鍵指向 {@code agents.agent_id}。
 * <b>可以是 null</b>：代表系統事件，畫面上顯示為「系統」。
 */
@Column(name = "agent_id")
String agentId ;

/**
 * 留言內容。{@code ticket_comments.content}。
 */
@Column(name = "content")
String content ;

/**
 * 留言時間。{@code ticket_comments.created_at}。
 * <b>不要自己填</b>，{@link #applyDefaults()} 會補。
 */
@Column(name = "created_at")
LocalDateTime createdAt;

/**
 * INSERT 前補上 {@code createdAt}（已經有值就不動）。
 * Hibernate 自動呼叫，<b>不要自己叫</b>。無參數、無回傳值。
 */
@PrePersist
void applyDefaults(){
    if(createdAt==null){
        createdAt = LocalDateTime.now().withNano(0);
    }
}
}
