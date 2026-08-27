package com.poz.CustomerService.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 工單的一則留言／處理記錄，對應資料表 {@code ticket_comments}。
 *
 * <h2>新增一則留言要填什麼</h2>
 * <ul>
 *   <li><b>必填</b>：{@code ticketId}（要是真的存在的工單）、{@code content}</li>
 *   <li><b>可為 null</b>：{@code agentId}——null 代表這是系統自動寫的紀錄</li>
 *   <li><b>不要自己填</b>：{@code commentId}（資料庫發號）、{@code createdAt}（callback 補）</li>
 * </ul>
 * <pre>
 * TicketComments c = TicketComments.builder()
 *         .ticketId(ticket.getTicketId()).agentId("CSC00001")
 *         .content("已致電客戶，確認為帳號被鎖定。")
 *         .build();
 * </pre>
 * 系統事件留言就把 {@code agentId} 留空不填。修改<b>不要</b>用 builder，理由同 {@link Tickets}。
 * <p>
 * 欄位命名規則同 {@link Agents}。
 */
@Data
// @Data + @Builder 會讓無參數 constructor 消失，但 Hibernate 撈資料時一定要它，
// 所以 @NoArgsConstructor（給 Hibernate）和 @AllArgsConstructor（給 builder）兩個都得補。
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name="ticket_comments")
public class TicketComments {

/**
 * 留言流水號，主鍵。{@code ticket_comments.comment_id}，INT IDENTITY。
 * <b>新增時不要填</b>，號碼由資料庫發，存檔後才有值。
 */
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
@Column(name = "comment_id")
Integer commentId ;

/**
 * 這則留言屬於哪張工單。{@code ticket_comments.ticket_id}。
 * <p>
 * 外鍵指向 {@code tickets.ticket_id}，所以填的號碼<b>必須真的存在</b>，否則寫不進去。
 */
@Column(name = "ticket_id")
Integer ticketId ;

/**
 * 留言者的客服代號。{@code ticket_comments.agent_id}，外鍵指向 {@code agents.agent_id}。
 * <p>
 * <b>可以是 null</b>：代表這是系統事件（建單、狀態變更、轉派等後端自動寫的紀錄），
 * 畫面上顯示為「系統」。
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
 * <p>
 * Hibernate 自動呼叫（{@code @PrePersist}），<b>不要自己叫</b>。無參數、無回傳值。
 * 理由同 {@link Agents#applyDefaults()}。
 */
@PrePersist
void applyDefaults(){
    if(createdAt==null){
        createdAt = LocalDateTime.now().withNano(0);
    }
}
}
