package com.poz.CustomerService.repository;

import com.poz.CustomerService.entity.FollowUps;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 行事曆回電安排的存取入口。主鍵型別是 {@code Integer}（安排流水號，由資料庫發號）。
 * <p>
 * 這裡的每一支方法<b>第一個條件都是 {@code agentId}</b>，不是巧合：
 * 回電安排是私人的，「查別人的行事曆」這件事不該有現成的方法可用。
 * 少了這個條件的查詢請不要加在這裡，避免哪天有人不小心叫到。
 */
@Repository
public interface FollowUpsRepository extends JpaRepository<FollowUps, Integer> {

    /**
     * 查某位客服在某一段時間內排定的回電，依回電時間由早到晚排序。行事曆的月檢視用這一支。
     * <p>
     * 方法名字長得嚇人，但每一段都有意義，Spring Data 是照著這些關鍵字自己生 SQL 的：
     * <pre>
     * findBy AgentId                        → WHERE agent_id = ?
     *        And FollowUpAt GreaterThanEqual → AND follow_up_at &gt;= ?
     *        And FollowUpAt LessThan         → AND follow_up_at &lt;  ?
     *        OrderBy FollowUpAt Asc          → ORDER BY follow_up_at ASC
     *                FollowUpId Asc          →        , follow_up_id ASC
     * </pre>
     * 前兩個條件剛好吃得到 {@code IX_follow_ups_agent_time (agent_id, follow_up_at)} 這條索引。
     *
     * <h3>為什麼不用 Between</h3>
     * Spring Data 的 {@code Between} <b>頭尾都包含</b>（SQL 的 BETWEEN 就是閉區間）。
     * 查九月時若傳 9/1 00:00:00 到 10/1 00:00:00，10/1 零點整那一筆會被算進九月，
     * 而且十月也會再撈到它一次——同一筆安排出現在兩個月。
     * 寫成「大於等於月初、小於下月初」就沒有這個邊界問題，
     * 也不必靠「減一秒」來閃避（那個作法還會綁死欄位的秒精度）。
     *
     * <h3>為什麼要排到 followUpId</h3>
     * 同一分鐘排了兩筆時，只照 {@code followUpAt} 排的話，兩筆誰先誰後是資料庫說了算，
     * 同一份資料重查兩次順序可能不一樣，畫面會莫名其妙跳動。
     * 補上流水號當第二排序鍵，順序就固定了。作法同
     * {@code TicketCommentsRepository.findByTicketIdOrderByCreatedAtAscCommentIdAsc}。
     * <p>
     * 代價是第二個排序鍵不在索引的鍵欄位裡，資料庫可能得多做一次 Sort；
     * 但一個月的安排最多也就幾十筆，這個成本可以忽略。
     *
     * @param agentId {@code String}——行事曆的主人，例如 CSC00001
     * @param start   {@code LocalDateTime}——區間起點，<b>包含</b>這個時間點
     * @param end     {@code LocalDateTime}——區間終點，<b>不包含</b>這個時間點
     * @return {@code List<FollowUps>}——符合條件的安排，已排序。
     *         查無資料時是空 list，不會是 null
     */
    List<FollowUps> findByAgentIdAndFollowUpAtGreaterThanEqualAndFollowUpAtLessThanOrderByFollowUpAtAscFollowUpIdAsc(
            String agentId, LocalDateTime start, LocalDateTime end);

    /**
     * 查「我對這張工單」有沒有排過回電。設定回電時間前要先問這一句，
     * 有就改時間、沒有就新增一筆。
     * <p>
     * 回傳 {@link Optional} 而不是 list，是因為資料庫的
     * {@code UQ_follow_ups_agent_ticket (agent_id, ticket_id)} 保證了這個組合最多一筆。
     * 哪天要支援「同一張單排多次回電」而拿掉那條唯一約束時，
     * 這支方法的回傳型別得跟著改成 {@code List}，否則 Spring Data 撈到兩筆會丟
     * {@code IncorrectResultSizeDataAccessException}。
     *
     * @param agentId  {@code String}——行事曆的主人，例如 CSC00001
     * @param ticketId {@code Integer}——工單的<b>內部流水號</b>，不是 TK-000001 那種對外編號
     * @return {@code Optional<FollowUps>}——排過就有值，沒排過是 {@code Optional.empty()}
     */
    Optional<FollowUps> findByAgentIdAndTicketId(String agentId, Integer ticketId);
}
