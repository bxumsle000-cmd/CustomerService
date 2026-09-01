package com.poz.CustomerService.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 客服人員，對應資料表 {@code agents}。
 *
 * <h2>有哪些欄位</h2>
 * <ul>
 *   <li><b>必填</b>：{@code agentId}（主鍵，由人決定的代號）、{@code name}、{@code passwordHash}</li>
 *   <li><b>不要自己填</b>：{@code status}（自動補 ONLINE）、
 *       {@code createdAt} / {@code updatedAt}（callback 維護）</li>
 * </ul>
 * 要回給前端時一律轉成 {@link com.poz.CustomerService.dto.agent.AgentResponse}，
 * 它只帶 agentId / name / status 出去，不含密碼雜湊。
 *
 * <h2>新增用 builder，修改用 setter</h2>
 * <pre>
 * Agents a = Agents.builder()
 *         .agentId("CSC00004").name("陳小美")
 *         .passwordHash(passwordEncoder.encode("明文密碼"))
 *         .build();
 * </pre>
 * 修改<b>不要</b>用 builder：沒填的欄位是 null，拿去 {@code save()} 會整筆覆蓋回資料庫。
 * 要改就先 {@code findById} 撈出來再 setter。
 *
 * <h2>欄位命名規則</h2>
 * Java 屬性用 camelCase（agentId），資料庫欄位用 snake_case（agent_id），靠 {@code @Column} 對接。
 * 屬性名不能有底線——Spring Data 把 {@code _} 當成巢狀屬性的分隔符號，
 * {@code findByCustomer_name} 會被理解成「先找 customer 再找它的 name」，衍生查詢就壞了。
 */
@Data
// @Data + @Builder 會讓無參數 constructor 消失，但 Hibernate 撈資料時一定要它，
// 所以 @NoArgsConstructor（給 Hibernate）和 @AllArgsConstructor（給 builder）兩個都得補。
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "agents")
public class Agents {

    /**
     * 客服代號，主鍵。例：CSC00001。{@code agents.agent_id}，NVARCHAR(10)。
     * <p>
     * 這是「業務主鍵」——由人決定的代號，不是資料庫自增流水號，所以<b>新增時必須自己填</b>，
     * 也因此這裡不能加 {@code @GeneratedValue}。
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
     * <p>
     * <b>不可外洩</b>，要回給前端請改用
     * {@link com.poz.CustomerService.dto.agent.AgentResponse}。
     * 驗密碼要用 {@code PasswordEncoder.matches(明文, 這個值)}，
     * 不能自己算一次雜湊再比字串——BCrypt 每次的鹽不同，同一個密碼算兩次結果不一樣。
     */
    @Column(name = "password_hash")
    private String passwordHash ;

    /**
     * 目前的工作狀態，六種之一：{@code ONLINE} / {@code ON_CALL} / {@code BREAK} /
     * {@code RESTROOM} / {@code LUNCH} / {@code MEETING}。{@code agents.status}。
     * <p>
     * {@code ON_CALL}（通話中）只能由通話事件設定、客服不能自己選，
     * 這條規則由 {@link com.poz.CustomerService.service.AgentService} 把關。
     * 沒填會自動補 {@code ONLINE}。
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
     * <p>
     * Hibernate 自動呼叫（{@code @PrePersist}），<b>不要自己叫</b>。無參數、無回傳值。
     * <p>
     * 不靠資料庫 DEFAULT 的原因同 {@link Tickets#applyDefaults()}：
     * Hibernate 的 INSERT 會明確送 NULL，DEFAULT 被跳過，直接撞 NOT NULL。
     */
    @PrePersist
    void applyDefaults() {
        if (createdAt == null) {
            // withNano(0)：欄位是 DATETIME2(0) 只存到秒，先自己截掉，
            // 免得記憶體裡是 15:35:31.4312514、資料庫裡卻是 15:35:31，debug 時看花眼。
            createdAt = LocalDateTime.now().withNano(0);
        }
        if (status == null) {
            status = "ONLINE";
        }
        if (updatedAt == null) {
            updatedAt = createdAt;   // 從未更新過時，updated_at 等於 created_at
        }
    }

    /**
     * UPDATE 前把 {@code updatedAt} 更新為現在時間。改狀態、改名字都會走到這裡。
     * <p>
     * Hibernate 自動呼叫（{@code @PreUpdate}），<b>不要自己叫</b>。無參數、無回傳值。
     */
    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = LocalDateTime.now().withNano(0);
    }
}
