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
 * 行事曆回電安排的存取入口。主鍵型別是 {@code Integer}（安排流水號，由資料庫發號）。
 * <p>
 * 這裡的每一支方法<b>條件裡都有 {@code agentId}</b>，不是巧合：
 * 回電安排是私人的，「查別人的行事曆」這件事不該有現成的方法可用。
 * 尤其是用 {@code followUpId} 撈的那一支——那個號碼是前端傳來的，
 * 少了 {@code agentId} 就等於把別人安排的讀寫權限送出去。
 * 少了這個條件的查詢請不要加在這裡，避免哪天有人不小心叫到。
 */
@Repository
public interface FollowUpsRepository extends JpaRepository<FollowUps, Integer> {

    /**
     * @param agentId {@code String}——行事曆的主人，例如 CSC00001
     * @param start   {@code LocalDateTime}——區間起點，<b>包含</b>這個時間點
     * @param end     {@code LocalDateTime}——區間終點，<b>不包含</b>這個時間點
     * @return {@code List<CalendarEventResponse>}——已排序、可以直接回給前端的事件。
     *         查無資料時是空 list，不會是 null
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
     * 查「我對這張工單排了哪些回電」，由早到晚。工單詳情頁用這一支。
     * <p>
     * 回 {@code List} 而不是 {@code Optional}：同一張單只要時間點不同就能排多筆
     * （見 {@code UQ_follow_ups_agent_ticket_time}），寫成 {@code Optional} 的話，
     * 排了兩筆就會丟 {@code IncorrectResultSizeDataAccessException}。
     * <p>
     * 第二排序鍵補 {@code followUpId}，理由同上面那支月檢視的查詢：
     * 光靠時間排序，同一時間點的兩筆誰先誰後是資料庫說了算，畫面會莫名其妙跳動。
     * （這張表的唯一約束其實已經讓「同一張單同一時間」不可能發生，
     * 但排序寫法保持一致比較不用去記哪裡有例外。）
     *
     * @param agentId  {@code String}——行事曆的主人，例如 CSC00001
     * @param ticketId {@code Integer}——工單的<b>內部流水號</b>，不是 TK-000001 那種對外編號
     * @return {@code List<FollowUps>}——我對這張單的所有安排，已排序。
     *         沒排過是空 list，不會是 null
     */
    List<FollowUps> findByAgentIdAndTicketIdOrderByFollowUpAtAscFollowUpIdAsc(
            String agentId, Integer ticketId);

    /**
     * 撈出「<b>我的</b>某一筆安排」，改期和取消都要先過這一關。
     * <p>
     * 條件寫兩個而不是只用 {@code findById(followUpId)}：{@code followUpId} 是前端傳來的數字，
     * 使用者想改成幾號都行。把 {@code agentId} 一起放進 WHERE，
     * 「別人的安排」就直接查不到，不必撈回來再自己比對一次。
     * <p>
     * 因為 {@code followUpId} 是主鍵，加上 {@code agentId} 之後結果最多還是一筆，所以回
     * {@link Optional}。查不到有兩種情況——號碼不存在、號碼是別人的——這裡不區分，
     * Service 一律當成 404；分得太清楚等於告訴使用者「這個號碼存在，只是不是你的」。
     *
     * @param followUpId {@code Integer}——安排流水號，前端從月檢視的回應拿到的那個
     * @param agentId    {@code String}——行事曆的主人，例如 CSC00001
     * @return {@code Optional<FollowUps>}——是自己的那一筆才有值，否則 {@code Optional.empty()}
     */
    Optional<FollowUps> findByFollowUpIdAndAgentId(Integer followUpId, String agentId);

    /**
     * 問「我對這張單、這個時間點，是不是已經排過了」。<b>新增</b>安排前用這一支擋重複。
     * <p>
     * 資料庫的 {@code UQ_follow_ups_agent_ticket_time} 本來就會擋，
     * 但撞上去丟的是 {@code DataIntegrityViolationException}，
     * 對使用者來說就是一個看不懂的 500。先問一句才能回「你已經排過這個時間了」。
     *
     * @param agentId    {@code String}——行事曆的主人
     * @param ticketId   {@code Integer}——工單的<b>內部流水號</b>，不是 TK-000001 那種對外編號
     * @param followUpAt {@code LocalDateTime}——要排定的時間，秒以下必須先截掉，
     *                   否則跟資料庫裡只存到秒的值比不起來
     * @return {@code boolean}——已經有一筆一模一樣的就是 true
     */
    boolean existsByAgentIdAndTicketIdAndFollowUpAt(
            String agentId, Integer ticketId, LocalDateTime followUpAt);

    /**
     * 同上，但排除掉指定的那一筆。<b>改期</b>時用這一支。
     * <p>
     * 為什麼要多這個 {@code FollowUpIdNot}：改期時「新時間」有可能就是這筆安排原本的時間
     * （使用者只改了備註、或改完又改回來）。用上面那支問的話會查到它自己，
     * 於是「沒有真的改動」反而被當成重複排定擋下來。
     *
     * @param agentId    {@code String}——行事曆的主人
     * @param ticketId   {@code Integer}——工單的內部流水號
     * @param followUpAt {@code LocalDateTime}——要改成的時間，秒以下先截掉
     * @param followUpId {@code Integer}——正在改的這一筆，要從比對範圍裡排除
     * @return {@code boolean}——<b>另外</b>有一筆佔住同一個時間點就是 true
     */
    boolean existsByAgentIdAndTicketIdAndFollowUpAtAndFollowUpIdNot(
            String agentId, Integer ticketId, LocalDateTime followUpAt, Integer followUpId);
}
