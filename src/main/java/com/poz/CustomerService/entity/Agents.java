package com.poz.CustomerService.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 客服人員，對應資料表 {@code agents}。
 * <ul>
 *   <li><b>必填</b>：{@code agentId}（主鍵，由人決定的代號）、{@code name}、{@code passwordHash}</li>
 *   <li><b>不要自己填</b>：{@code status}、{@code createdAt} / {@code updatedAt}</li>
 * </ul>
 * 新增用 builder，修改先 {@code findById} 撈出來再 setter。
 * 要回給前端時一律轉成 {@link com.poz.CustomerService.dto.agent.AgentResponse}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "agents")
public class Agents {

    /**
     * 客服代號，主鍵。例：CSC00001。{@code agents.agent_id}，NVARCHAR(10)。
     * 由人決定的代號，<b>新增時必須自己填</b>。
     */
    @Id
    @Column(name = "agent_id")
    String agentId ;

    /**
     * 客服姓名，例：林曉明。{@code agents.name}，NVARCHAR(50)。
     */
    @Column(name = "name")
    String name;

    /**
     * 密碼的 BCrypt 雜湊值。{@code agents.password_hash}。
     * <b>不可外洩</b>；驗密碼要用 {@code PasswordEncoder.matches(明文, 這個值)}。
     */
    @Column(name = "password_hash")
    private String passwordHash ;

    /**
     * 目前的工作狀態，六種之一：{@code ONLINE} / {@code ON_CALL} / {@code BREAK} /
     * {@code RESTROOM} / {@code LUNCH} / {@code MEETING}。{@code agents.status}。
     * {@code ON_CALL} 只能由通話事件設定，客服不能自己選；沒填會自動補 {@code ONLINE}。
     */
    @Column(name = "status")
    private String status ;

    /**
     * 建立時間。{@code agents.created_at}，NOT NULL。
     * <b>不要自己填</b>，{@link #applyDefaults()} 會補。
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt ;

    /**
     * 最後更新時間。{@code agents.updated_at}，NOT NULL。
     * <b>不要自己填</b>，由 {@link #applyDefaults()} 和 {@link #touchUpdatedAt()} 維護。
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt ;

    /**
     * INSERT 前補預設值：{@code createdAt} 補現在時間、{@code status} 補 {@code ONLINE}、
     * {@code updatedAt} 補成跟 {@code createdAt} 一樣，已經有值的不動。
     * Hibernate 自動呼叫，<b>不要自己叫</b>。無參數、無回傳值。
     */
    @PrePersist
    void applyDefaults() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now().withNano(0);
        }
        if (status == null) {
            status = "ONLINE";
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    /**
     * UPDATE 前把 {@code updatedAt} 更新為現在時間。
     * Hibernate 自動呼叫，<b>不要自己叫</b>。無參數、無回傳值。
     */
    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = LocalDateTime.now().withNano(0);
    }
}
