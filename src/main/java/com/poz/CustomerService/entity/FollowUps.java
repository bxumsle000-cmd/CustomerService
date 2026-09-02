package com.poz.CustomerService.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 一筆行事曆上的回電安排，對應資料表 {@code follow_ups}。
 * 資料的主人是 {@code agentId} 而不是工單，工單轉派時這筆安排不會跟著換人。
 * <ul>
 *   <li><b>必填</b>：{@code agentId}、{@code ticketId}、{@code followUpAt}</li>
 *   <li><b>可留空</b>：{@code note}——個人備註，只有主人看得到</li>
 *   <li><b>不要自己填</b>：{@code followUpId}（資料庫發號）</li>
 * </ul>
 * 新增用 builder，修改先撈出來再 setter；取消排定是刪掉整列，不是把時間設成 null。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "follow_ups")
public class FollowUps {

    /**
     * 安排流水號，內部主鍵。{@code follow_ups.follow_up_id}，INT IDENTITY。
     * <b>新增時不要填</b>，號碼由資料庫發。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "follow_up_id")
    private Integer followUpId;

    /**
     * 這筆安排的主人，也就是「誰的行事曆」。{@code follow_ups.agent_id}，NOT NULL。
     * 外鍵指向 {@code agents.agent_id}，代號<b>必須真的存在</b>。
     * 注意這<b>不是</b> {@code tickets.assignee_id}。
     */
    @Column(name = "agent_id")
    private String agentId;

    /**
     * 要跟進哪一張工單。{@code follow_ups.ticket_id}，NOT NULL。
     * 存的是內部流水號，<b>不是</b> TK-000001 那種對外編號。
     * 工單被刪掉時這筆安排會被連帶刪除（ON DELETE CASCADE）。
     */
    @Column(name = "ticket_id")
    private Integer ticketId;

    /**
     * 排定的回電時間。{@code follow_ups.follow_up_at}，DATETIME2(0)、<b>NOT NULL</b>。
     * 只存到秒，寫入前請先 {@code withNano(0)} 截掉。
     */
    @Column(name = "follow_up_at")
    private LocalDateTime followUpAt;

    /**
     * 個人備註，只有安排的主人看得到，不會寫進工單的 timeline。
     * {@code follow_ups.note}，NVARCHAR(200)，<b>可為 null</b>。
     */
    @Column(name = "note")
    private String note;
}
