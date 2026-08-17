package com.poz.CustomerService.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 工單處理記錄／留言，對應 ticket_comments 資料表。
 *
 * 兩種來源共用同一張表：
 *   1. 客服自己寫的留言  → authorId 有值
 *   2. 系統事件（狀態變更、轉派、建單）→ authorId 為 null，前端顯示成「系統」
 */
@Entity
@Table(name = "ticket_comments")
public class TicketComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Integer commentId;

    /** 所屬工單的內部主鍵（不是 ticketNo） */
    @Column(name = "ticket_id", nullable = false)
    private Integer ticketId;

    /** 留言的客服代號；系統事件為 null */
    @Column(name = "author_id", length = 10)
    private String authorId;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    protected TicketComment() {
    }

    /** 建立客服留言 */
    public static TicketComment byAgent(Integer ticketId, String authorId, String content) {
        TicketComment c = new TicketComment();
        c.ticketId = ticketId;
        c.authorId = authorId;
        c.content = content;
        return c;
    }

    /** 建立系統事件記錄（沒有作者） */
    public static TicketComment bySystem(Integer ticketId, String content) {
        TicketComment c = new TicketComment();
        c.ticketId = ticketId;
        c.authorId = null;
        c.content = content;
        return c;
    }

    /** 這筆是不是系統事件 */
    public boolean isSystemEvent() {
        return authorId == null;
    }

    public Integer getCommentId() {
        return commentId;
    }

    public Integer getTicketId() {
        return ticketId;
    }

    public String getAuthorId() {
        return authorId;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
