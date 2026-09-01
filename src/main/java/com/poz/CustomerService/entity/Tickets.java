package com.poz.CustomerService.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDateTime;

/**
 * 工單，對應資料表 {@code tickets}。
 *
 * <h2>建立一筆新工單要填什麼</h2>
 * <ul>
 *   <li><b>必填</b>：{@code title}、{@code category}、{@code channel}、{@code assigneeId}</li>
 *   <li><b>可留空</b>：{@code customerName}、{@code contactPhone}、{@code description}</li>
 *   <li><b>不要自己填</b>：{@code ticketId}（資料庫發號）、{@code ticketNo}（資料庫算出來的）、
 *       {@code status}（自動補 IN_PROGRESS）、{@code createdAt} / {@code updatedAt}（callback 維護）</li>
 * </ul>
 *
 * <h2>新增用 builder，修改用 setter</h2>
 * <pre>
 * Tickets t = Tickets.builder()
 *         .title("詢問帳單")
 *         .category("帳單問題").channel("PHONE").assigneeId("CSC00001")
 *         .build();
 * </pre>
 * 修改<b>不要</b>用 builder：沒填的欄位是 null，拿去 {@code save()} 會整筆覆蓋回資料庫。
 * 要改就先 {@code findById} 撈出來再 setter。
 * <p>
 * <h2>排定的回電時間不在這裡</h2>
 * 以前這裡有一個 {@code followUpAt} 欄位，已經搬到 {@link FollowUps}。
 * 理由是那筆資料的主人是<b>客服</b>而不是工單：工單轉派給別人時，
 * 原本那個人排的回電不該跟著換人，個人備註更不該跟著送到別人眼前。
 * <p>
 * 資料庫的 {@code tickets.follow_up_at} 欄位目前<b>還在</b>（要等 V4 migration 才 DROP），
 * 但 Java 這邊已經不再對映它，所以從現在起那個欄位不會再被讀、也不會再被寫。
 * <p>
 * 欄位命名規則同 {@link Agents}：Java 用 camelCase，資料庫用 snake_case，靠 {@code @Column} 對接。
 */
@Data
// @Data + @Builder 會讓無參數 constructor 消失，但 Hibernate 撈資料時一定要它，
// 所以 @NoArgsConstructor（給 Hibernate）和 @AllArgsConstructor（給 builder）兩個都得補。
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tickets")
public class Tickets {

    /**
     * 工單流水號，內部主鍵，不對外顯示。{@code tickets.ticket_id}，INT IDENTITY。
     * <p>
     * <b>新增時不要填</b>，號碼由資料庫發，存檔後才有值。
     * （型別用 Integer 不用 int：null 才分得出「還沒存過」和「主鍵真的是 0」。）
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_id")
    private Integer ticketId;

    /**
     * 對外顯示的工單編號，格式 TK-000001。
     * {@code tickets.ticket_no}，NOT NULL、<b>全表唯一</b>。
     * <p>
     * <b>不要自己填</b>：這一欄在資料庫是「計算欄位」，值由 {@code ticket_id} 推導
     *（見 {@code V1__init_schema.sql}），Java 這邊只讀不寫——所以標了
     * {@code insertable = false, updatable = false}，你就算 set 了也不會寫進資料庫。
     * <p>
     * {@code @Generated(event = INSERT)} 是告訴 Hibernate「這一欄的值是資料庫產生的」，
     * INSERT 完會自動再發一次 SELECT 把算好的編號讀回來，
     * 所以 {@code save()} 回來的物件就已經有 ticketNo 可用了。
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "ticket_no", insertable = false, updatable = false)
    private String ticketNo;

    /**
     * 通話中向客戶確認的姓名。{@code tickets.customer_name}，NVARCHAR(255)，<b>可為 null</b>。
     */
    @Column(name = "customer_name")
    private String customerName;

    /**
     * 客戶提供的聯絡電話，用來查歷史紀錄。
     * {@code tickets.contact_phone}，NVARCHAR(50)，<b>可為 null</b>。
     */
    @Column(name = "contact_phone")
    private String contactPhone;

    /**
     * 工單主旨。{@code tickets.title}，NVARCHAR(50)、NOT NULL。
     */
    @Column(name = "title")
    private String title;

    /**
     * 問題描述內容。{@code tickets.description}，NVARCHAR(MAX)，<b>可為 null</b>。
     */
    @Column(name = "description", columnDefinition = "NVARCHAR(MAX)")
    private String description;

    /**
     * 處理狀態，只能是 {@code IN_PROGRESS} 處理中 / {@code PENDING} 待客戶回覆 /
     * {@code RESOLVED} 已解決。{@code tickets.status}，NOT NULL。
     * <p>
     * 沒填會自動補 {@code IN_PROGRESS}；填白名單以外的值會被 CK_tickets_status 擋下來。
     */
    @Column(name = "status")
    private String status;

    /**
     * 問題分類，例如「帳號問題」「付款、發票」。
     * {@code tickets.category}，NVARCHAR(255)、NOT NULL。
     */
    @Column(name = "category")
    private String category;

    /**
     * 派單來源，只能是 {@code PHONE}（通話工作台在通話中建立）或
     * {@code Agent}（客服從「＋ 新增派件」手動建立），由 CK_tickets_channel 把關。
     * {@code tickets.channel}，NVARCHAR(10)、NOT NULL。
     */
    @Column(name = "channel")
    private String channel;

    /**
     * 負責處理的客服代號，例如 CSC00001。{@code tickets.assignee_id}，NOT NULL。
     * <p>
     * 外鍵指向 {@code agents.agent_id}，所以填的代號<b>必須真的存在</b>，否則寫不進去。
     */
    @Column(name = "assignee_id")
    private String assigneeId;

    /**
     * 建立時間。{@code tickets.created_at}，NOT NULL。
     * <b>不要自己填</b>，{@link #applyDefaults()} 會補。
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * 最後更新時間。{@code tickets.updated_at}，NOT NULL。
     * <b>不要自己填</b>，由 {@link #applyDefaults()} 和 {@link #touchUpdatedAt()} 維護。
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;


    /**
     * INSERT 前補預設值：{@code createdAt} / {@code updatedAt} 補現在時間、
     * {@code status} 補 {@code IN_PROGRESS}，已經有值的不動。
     * <p>
     * Hibernate 自動呼叫（{@code @PrePersist}），<b>不要自己叫</b>。無參數、無回傳值。
     * <p>
     * 不靠資料庫 DEFAULT 的原因：Hibernate 的 INSERT 會列出所有欄位、等於明確送 NULL，
     * SQL Server 的 DEFAULT 就被跳過，直接撞 NOT NULL。
     * {@code withNano(0)} 則是因為欄位只存到秒，先自己截掉才不會跟資料庫的值對不起來。
     */
    @PrePersist
    void applyDefaults() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = "IN_PROGRESS";   // 新建工單一律是「處理中」
        }
    }

    /**
     * UPDATE 前把 {@code updatedAt} 更新為現在時間。
     * <p>
     * Hibernate 自動呼叫（{@code @PreUpdate}），<b>不要自己叫</b>。無參數、無回傳值。
     * SQL Server 沒有 MySQL 那種 ON UPDATE CURRENT_TIMESTAMP，所以得由這裡負責。
     */
    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = LocalDateTime.now().withNano(0);
    }
}
