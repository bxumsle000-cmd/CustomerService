package com.poz.CustomerService.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 工單，對應 tickets 資料表。
 *
 * 【為什麼 assigneeId 是 String 而不是 @ManyToOne Agent？】
 * JPA 可以把外鍵寫成物件關聯（@ManyToOne），但那會帶來延遲載入的陷阱：
 * 我們在 application.properties 關掉了 open-in-view，
 * 一旦在交易外面碰到還沒載入的關聯物件就會拋 LazyInitializationException。
 * 而且 docs/api.md 的回應本來就只需要 assigneeId 這個字串。
 * 所以這裡直接存代號，需要客服姓名時再另外查 agents，單純且可預測。
 */
@Entity
@Table(name = "tickets")
public class Ticket {

    /**
     * 內部主鍵，資料庫 IDENTITY 自動遞增。
     * IDENTITY 表示「值由資料庫產生」，新增前是 null，存進去之後才會有值。
     * 這個欄位不對外，API 一律用 ticketNo。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_id")
    private Integer ticketId;

    /** 對外顯示的工單編號，格式 TK-XXXXXX，由後端產生 */
    @Column(name = "ticket_no", nullable = false, unique = true, length = 10)
    private String ticketNo;

    /** 通話中向客戶確認的姓名，未提供時為 null */
    @Column(name = "customer_name", length = 50)
    private String customerName;

    /** 客戶提供的聯絡電話，用來查歷史紀錄，不代表已核實的客戶身分 */
    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 資料庫是 NVARCHAR(MAX)，Java 這邊就是普通 String，不用特別處理 */
    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TicketStatus status;

    /** 問題分類，例如 帳號問題 / 付款、發票。保持 String，之後要新增分類不用改程式 */
    @Column(name = "category", nullable = false, length = 30)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10)
    private TicketChannel channel;

    @Column(name = "assignee_id", nullable = false, length = 10)
    private String assigneeId;

    /** 排定的跟進／回電時間，行事曆用；沒排就是 null */
    @Column(name = "follow_up_at")
    private LocalDateTime followUpAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 新增前：兩個時間都補上。
     *
     * MySQL 有 ON UPDATE CURRENT_TIMESTAMP 可以讓資料庫自動維護 updated_at，
     * 但 SQL Server 完全沒有這個語法，所以改由 JPA 在這裡處理。
     */
    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    /** 更新前：只動 updated_at */
    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    protected Ticket() {
    }

    public Ticket(String ticketNo, String title, TicketStatus status,
                  String category, TicketChannel channel, String assigneeId) {
        this.ticketNo = ticketNo;
        this.title = title;
        this.status = status;
        this.category = category;
        this.channel = channel;
        this.assigneeId = assigneeId;
    }

    public Integer getTicketId() {
        return ticketId;
    }

    public String getTicketNo() {
        return ticketNo;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public TicketChannel getChannel() {
        return channel;
    }

    public void setChannel(TicketChannel channel) {
        this.channel = channel;
    }

    public String getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(String assigneeId) {
        this.assigneeId = assigneeId;
    }

    public LocalDateTime getFollowUpAt() {
        return followUpAt;
    }

    public void setFollowUpAt(LocalDateTime followUpAt) {
        this.followUpAt = followUpAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
