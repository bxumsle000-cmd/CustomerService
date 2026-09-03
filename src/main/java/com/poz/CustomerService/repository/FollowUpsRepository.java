package com.poz.CustomerService.repository;

import com.poz.CustomerService.dto.calendar.CalendarEventResponse;
import com.poz.CustomerService.entity.FollowUps;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 行事曆回電安排的存取入口。主鍵型別是 {@code Integer}（安排流水號）。
 * <p>
 * 回電安排是私人的，這裡每一支方法的條件裡<b>都必須有 {@code agentId}</b>。
 */
@Repository
public interface FollowUpsRepository extends JpaRepository<FollowUps, Integer> {

    /**
     * 查某人某個時間區間內的回電安排，已 join 工單並組成回應。
     *
     * @param agentId 行事曆的主人，例如 CSC00001
     * @param start   區間起點，<b>包含</b>這個時間點
     * @param end     區間終點，<b>不包含</b>這個時間點
     * @return 已排序的事件；查無資料時是空 list，不會是 null
     */
    @Query("""
            select new com.poz.CustomerService.dto.calendar.CalendarEventResponse(
                       f.followUpId, t.ticketNo, t.title, t.customerName, t.contactPhone,
                       t.status, f.followUpAt, f.note)
            from FollowUps f
            join Tickets t on t.ticketId = f.ticketId
            where f.agentId = :agentId
              and f.followUpAt >= :start
              and f.followUpAt < :end
            order by f.followUpAt asc, f.followUpId asc
            """)
    List<CalendarEventResponse> findMonthlyEvents(@Param("agentId") String agentId,
                                                  @Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end);

    /**
     * 撈出「<b>我的</b>某一筆安排」，改期和取消都要先過這一關。
     *
     * @param followUpId 安排流水號，前端從月檢視的回應拿到的那個
     * @param agentId    行事曆的主人，例如 CSC00001
     * @return 是自己的那一筆才有值，否則 {@code Optional.empty()}
     */
    Optional<FollowUps> findByFollowUpIdAndAgentId(Integer followUpId, String agentId);

    /**
     * 問「我對這張單、這個時間點，是不是已經排過了」。<b>新增</b>安排前用這一支擋重複。
     *
     * @param agentId    行事曆的主人
     * @param ticketId   工單的<b>內部流水號</b>，不是 TK-000001 那種對外編號
     * @param followUpAt 要排定的時間，秒以下必須先截掉
     * @return 已經有一筆一模一樣的就是 true
     */
    boolean existsByAgentIdAndTicketIdAndFollowUpAt(
            String agentId, Integer ticketId, LocalDateTime followUpAt);

    /**
     * 同上，但排除掉指定的那一筆。<b>改期</b>時用這一支。
     *
     * @param agentId    行事曆的主人
     * @param ticketId   工單的內部流水號
     * @param followUpAt 要改成的時間，秒以下先截掉
     * @param followUpId 正在改的這一筆，要從比對範圍裡排除
     * @return <b>另外</b>有一筆佔住同一個時間點就是 true
     */
    boolean existsByAgentIdAndTicketIdAndFollowUpAtAndFollowUpIdNot(
            String agentId, Integer ticketId, LocalDateTime followUpAt, Integer followUpId);
}
