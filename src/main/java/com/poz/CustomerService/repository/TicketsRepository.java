package com.poz.CustomerService.repository;

import com.poz.CustomerService.entity.Tickets;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 工單資料的存取入口。主鍵型別是 {@code Integer}（工單流水號，由資料庫發號）。
 * <p>
 * 還沒有 TicketService，所以這個介面自己一支方法都沒有，
 * 目前只有 {@code JpaTest} 在用 {@link JpaRepository} 內建的那幾支：
 * {@code save} / {@code findById} / {@code findAll} / {@code deleteById} / {@code existsById}。
 * <p>
 * <b>提醒</b>：{@code save()} 傳進一個「有主鍵但不是從資料庫撈出來」的 Tickets 時會走 merge，
 * 等於整筆覆蓋——沒填到的欄位會被寫成 null。改資料請先 {@code findById} 撈出來再改。
 */
@Repository
public interface TicketsRepository extends JpaRepository<Tickets, Integer> {
    Optional<Tickets> findByTicketNo(String ticketNo);

    /**
     * 查某位客服在某一段時間內排定的回電，依回電時間由早到晚排序。行事曆的月檢視用這一支。
     * <p>
     * 方法名字長得嚇人，但每一段都有意義，Spring Data 是照著這些關鍵字自己生 SQL 的：
     * <pre>
     * findBy AssigneeId                    → WHERE assignee_id = ?
     *        And FollowUpAt GreaterThanEqual → AND follow_up_at &gt;= ?
     *        And FollowUpAt LessThan          → AND follow_up_at &lt;  ?
     *        OrderBy FollowUpAt Asc           → ORDER BY follow_up_at ASC
     * </pre>
     * 剛好吃得到 {@code IX_tickets_follow_up (assignee_id, follow_up_at)} 這條索引。
     *
     * <h3>為什麼不用 Between</h3>
     * Spring Data 的 {@code Between} <b>頭尾都包含</b>（SQL 的 BETWEEN 就是閉區間）。
     * 查九月時若傳 9/1 00:00:00 到 10/1 00:00:00，10/1 零點整那一筆會被算進九月，
     * 而且十月也會再撈到它一次——同一筆事件出現在兩個月。
     * 寫成「大於等於月初、小於下月初」就沒有這個邊界問題，
     * 也不必靠「減一秒」來閃避（那個作法還會綁死欄位的秒精度）。
     *
     * <h3>沒排回電的工單會自己被排除</h3>
     * {@code follow_up_at} 是 null 的工單不必特別過濾：SQL 裡 null 拿去比大小結果是 unknown，
     * 不算成立，所以那些工單根本不會進到結果裡。
     *
     * @param assigneeId {@code String}——負責客服的代號，例如 CSC00001
     * @param start      {@code LocalDateTime}——區間起點，<b>包含</b>這個時間點
     * @param end        {@code LocalDateTime}——區間終點，<b>不包含</b>這個時間點
     * @return {@code List<Tickets>}——符合條件的工單，已排序。查無資料時是空 list，不會是 null
     */
    List<Tickets> findByAssigneeIdAndFollowUpAtGreaterThanEqualAndFollowUpAtLessThanOrderByFollowUpAtAsc(
            String assigneeId, LocalDateTime start, LocalDateTime end);
}
