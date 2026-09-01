package com.poz.CustomerService.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 一筆行事曆上的回電安排，對應資料表 {@code follow_ups}。
 *
 * <h2>這張表和工單的關係</h2>
 * 一筆安排 = 「<b>某個客服</b>打算在<b>某個時間</b>跟進<b>某張工單</b>，順便寫給自己一句備註」。
 * 重點是第一個「某個客服」——這筆資料的主人是 {@code agentId}，不是工單。
 * 工單轉派給別人時，這筆安排<b>不會</b>跟著換人，因為它記的是「我的行程」，不是工單的屬性。
 *
 * <h2>建立一筆安排要填什麼</h2>
 * <ul>
 *   <li><b>必填</b>：{@code agentId}、{@code ticketId}（都要是真的存在的）、{@code followUpAt}</li>
 *   <li><b>可留空</b>：{@code note}——個人備註，只有主人看得到</li>
 *   <li><b>不要自己填</b>：{@code followUpId}（資料庫發號）</li>
 * </ul>
 * <pre>
 * FollowUps f = FollowUps.builder()
 *         .agentId("CSC00001").ticketId(ticket.getTicketId())
 *         .followUpAt(LocalDateTime.of(2026, 9, 5, 14, 0))
 *         .note("客戶說下午三點後才方便")
 *         .build();
 * </pre>
 * 修改<b>不要</b>用 builder，理由同 {@link Tickets}：沒填的欄位是 null，會整筆覆蓋回資料庫。
 *
 * <h2>為什麼沒有 createdAt / updatedAt</h2>
 * 其他三張表有，是因為真的有查詢在用（工單列表 {@code ORDER BY created_at DESC}、
 * timeline 照 {@code created_at} 排序）。這張表沒有任何地方會顯示「這筆安排是什麼時候建的」，
 * 行事曆是照 {@code followUpAt} 排序的。加了就是每次寫入都要維護、卻沒有人讀。
 * 之後真的需要再開 migration 補。
 *
 * <h2>取消排定 = 刪掉整列</h2>
 * {@code followUpAt} 在資料庫是 NOT NULL——沒有時間就不成其為一筆行事曆安排。
 * 所以取消是 {@code delete()}，不是把欄位設成 null。
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
@Table(name = "follow_ups")
public class FollowUps {

    /**
     * 安排流水號，內部主鍵，不對外顯示。{@code follow_ups.follow_up_id}，INT IDENTITY。
     * <p>
     * <b>新增時不要填</b>，號碼由資料庫發，存檔後才有值。
     * （型別用 Integer 不用 int：null 才分得出「還沒存過」和「主鍵真的是 0」。）
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "follow_up_id")
    private Integer followUpId;

    /**
     * 這筆安排的主人，也就是「誰的行事曆」。{@code follow_ups.agent_id}，NOT NULL。
     * <p>
     * 外鍵指向 {@code agents.agent_id}，所以填的代號<b>必須真的存在</b>，否則寫不進去。
     * <p>
     * 注意這<b>不是</b> {@code tickets.assignee_id}：工單目前指派給誰，跟這筆安排是誰排的，
     * 是兩件事。工單轉派之後，原本那個人的安排還在他自己的行事曆上。
     */
    @Column(name = "agent_id")
    private String agentId;

    /**
     * 要跟進哪一張工單。{@code follow_ups.ticket_id}，NOT NULL。
     * <p>
     * 存的是內部流水號，<b>不是</b> TK-000001 那種對外編號——外鍵指向
     * {@code tickets.ticket_id}。作法同 {@link TicketComments#getTicketId()}。
     * <p>
     * 工單被刪掉時這筆安排會被連帶刪除（外鍵設了 ON DELETE CASCADE），
     * 因為工單都不在了，行事曆上那一格點進去只會 404。
     */
    @Column(name = "ticket_id")
    private Integer ticketId;

    /**
     * 排定的回電時間。{@code follow_ups.follow_up_at}，DATETIME2(0)、<b>NOT NULL</b>。
     * <p>
     * 欄位只存到秒，所以寫入前請先 {@code withNano(0)} 自己截掉，
     * 不然存進去的值會跟記憶體裡的物件對不起來。
     */
    @Column(name = "follow_up_at")
    private LocalDateTime followUpAt;

    /**
     * 個人備註，只有 {@link #getAgentId()} 這個人看得到。
     * {@code follow_ups.note}，NVARCHAR(200)，<b>可為 null</b>（不寫備註就是 null）。
     * <p>
     * 這是「寫給自己看的」，跟 {@link TicketComments} 那條所有人都看得到的處理記錄是兩回事——
     * 排回電、改期都<b>不會</b>寫進 timeline。
     * <p>
     * 長度上限 200 要跟資料庫的 NVARCHAR(200) 一致，由
     * {@code CalendarService.updateFollowUp()} 負責擋。
     */
    @Column(name = "note")
    private String note;
}
