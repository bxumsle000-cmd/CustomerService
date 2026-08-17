package com.poz.CustomerService.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 客服人員，對應 agents 資料表。
 *
 * 資料表結構由 Flyway 維護（V1__init_schema.sql），
 * 這個類別只負責「把資料表的一列對應成一個 Java 物件」，不會去改結構
 * （application.properties 裡 spring.jpa.hibernate.ddl-auto=none）。
 */
@Entity
@Table(name = "agents")
public class Agent {

    /**
     * 客服代號，例如 CSC00001。
     *
     * 這是「自然主鍵」——由業務規則決定的有意義編號，不是資料庫產生的流水號，
     * 所以沒有 @GeneratedValue，新增時要自己給值。
     */
    @Id
    @Column(name = "agent_id", length = 10)
    private String agentId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /** BCrypt 雜湊後的密碼，絕不存明文 */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /**
     * EnumType.STRING 表示存進資料庫的是 "ONLINE" 這樣的字串。
     * 千萬不要用預設的 EnumType.ORDINAL（存 0、1、2），
     * 那樣之後在 enum 中間插入一個新狀態，舊資料的意思就全部跑掉了。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AgentStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 新增前自動補上建立時間。
     *
     * 資料庫欄位雖然也有 DEFAULT SYSDATETIME()，但那是容器的 UTC 時間，
     * 跟 Java 這邊的本機時間差 8 小時。統一由 Java 產生，時間來源才只有一個。
     */
    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = AgentStatus.ONLINE;
        }
    }

    /** JPA 規定一定要有無參數建構子，才能在查詢時把物件建出來 */
    protected Agent() {
    }

    public Agent(String agentId, String name, String passwordHash) {
        this.agentId = agentId;
        this.name = name;
        this.passwordHash = passwordHash;
        this.status = AgentStatus.ONLINE;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public AgentStatus getStatus() {
        return status;
    }

    public void setStatus(AgentStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
