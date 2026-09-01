package com.poz.CustomerService.service;

import com.poz.CustomerService.dto.calendar.CalendarEventResponse;
import com.poz.CustomerService.dto.calendar.CalendarMonthResponse;
import com.poz.CustomerService.entity.FollowUps;
import com.poz.CustomerService.entity.Tickets;
import com.poz.CustomerService.exception.ApiException;
import com.poz.CustomerService.repository.FollowUpsRepository;
import com.poz.CustomerService.repository.TicketsRepository;
import com.poz.CustomerService.security.CurrentAgentProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 行事曆相關的 business logic。
 *
 * <h2>行事曆上的事件是什麼</h2>
 * 一筆 {@link FollowUps}：「<b>我</b>打算在<b>某個時間</b>跟進<b>某張工單</b>，順便寫給自己一句備註」。
 * 格子上要顯示的單號、主旨、狀態則是查 {@link Tickets} 拿現在的值，沒有複製一份存起來
 * （理由見 {@link CalendarEventResponse}）。
 *
 * <h2>「我的」行事曆，不接受指定看誰的</h2>
 * 這支 Service 每一支方法都用 {@link CurrentAgentProvider#currentAgentId()} 決定主人，
 * 不吃前端傳來的 agentId——不然只要改個網址參數就能看別人的行程和私人備註。
 * 之後真要做「主管看整組」的功能，那是另一支方法加上權限判斷，不是在這裡加參數。
 *
 * <h2>排回電不寫進工單的處理記錄</h2>
 * 回電安排是個人行程，不是工單的公開歷程，所以排定／改期／取消都<b>不會</b>在
 * {@code ticket_comments} 留下任何東西。備註也只存在 {@code follow_ups.note}，
 * 只有主人看得到。
 * <p>
 * （舊版本每次改期都會往 timeline 寫一句「排定回電時間 ...」，已經拿掉。）
 *
 * <h2>方法一覽</h2>
 * 對外開放（Controller 呼叫的）：
 * <ul>
 *   <li>{@link #monthlyFollowUps(int, int)}——查自己某個月排定的所有回電</li>
 *   <li>{@link #updateFollowUp(String, LocalDateTime, String)}——排定或改期，沒排過就新增</li>
 *   <li>{@link #deleteFollowUp(String)}——取消排定</li>
 * </ul>
 * 內部小工具（private，Controller 叫不到）：
 * <ul>
 *   <li>{@link #findTicket(String)}——用 ticketNo 撈工單，撈不到丟 404</li>
 * </ul>
 *
 * <h2>為什麼不呼叫 TicketService</h2>
 * 讓 Service 互相呼叫會讓交易邊界和相依方向都變複雜（誰包誰的交易？之後 TicketService
 * 反過來要用行事曆的東西怎麼辦？）。這裡照專案現有的作法直接用 repository，
 * 代價是 {@link #findTicket} 跟 {@code TicketService} 各有一份幾乎一樣的實作——
 * 四行，比綁死兩支 Service 划算。真的長出第三份時再抽成共用元件。
 */
@Service
@RequiredArgsConstructor
public class CalendarService {

    private final TicketsRepository ticketsRepository;
    private final FollowUpsRepository followUpsRepository;
    private final CurrentAgentProvider currentAgentProvider;

    // ------------------------------------------------------------------
    // 常數
    // ------------------------------------------------------------------

    /**
     * 可以查詢的年份下限。純粹擋離譜的輸入（例如網址被亂改成 year=0），
     * 不是業務規則——{@code LocalDate.of()} 遇到 0 或負數會丟 {@code DateTimeException}，
     * 那是 500，使用者只會看到「系統發生錯誤」。
     */
    private static final int MIN_YEAR = 2015;

    /** 可以查詢的年份上限，用意同 {@link #MIN_YEAR}。 */
    private static final int MAX_YEAR = 2050;

    /**
     * 個人備註的長度上限，必須跟 {@code follow_ups.note} 的 NVARCHAR(200) 一致。
     * <p>
     * 在這裡擋是因為目前還沒有 Controller 和 request DTO，Service 就是最外層。
     * 之後補上 request DTO 時，這個數字要一起寫成 {@code @Size(max = 200)}——
     * 作法同 {@code tickets.title} 的 NVARCHAR(50) 對應 {@code @Size(max = 50)}。
     * 少了這道檢查，超長的備註會直接撞資料庫的欄位長度，變成使用者看不懂的 500。
     */
    private static final int NOTE_MAX_LENGTH = 200;

    // ------------------------------------------------------------------
    // 對外的方法
    // ------------------------------------------------------------------

    /**
     * 查<b>自己</b>某個月排定的所有回電，對應 GET /api/calendar。行事曆的月檢視。
     * <p>
     * 掛 {@code readOnly = true}：整支方法只讀不寫，Hibernate 就不必為了偵測變更
     * 而保留每個 entity 的快照。
     *
     * <h3>為什麼是兩次查詢</h3>
     * 一格事件的內容橫跨 follow_ups 和 tickets 兩張表，所以：
     * <ol>
     *   <li>先查這個月「我的」安排（吃 {@code IX_follow_ups_agent_time} 索引）</li>
     *   <li>再用一次 {@code findAllById} 把這批安排指到的工單<b>一次</b>撈回來</li>
     * </ol>
     * 固定兩次，跟這個月有幾筆安排無關。若改成邊跑邊查工單，三十筆安排就會發三十一次
     * 查詢（N+1）。
     *
     * @param year  {@code int}——西元年，{@value #MIN_YEAR} 到 {@value #MAX_YEAR}
     * @param month {@code int}——月份，<b>1 到 12</b>
     * @return {@link CalendarMonthResponse}——這個月的所有回電安排，依時間排序。
     *         這個月沒排任何事情時 events 是空 list，<b>不是 404</b>
     *         （「這個月沒事」是正常結果，不是錯誤）
     * @throws ApiException 400 / {@code VALIDATION_ERROR}——月份不在 1 到 12，
     *                      或年份超出可查詢範圍
     */
    @Transactional(readOnly = true)
    public CalendarMonthResponse monthlyFollowUps(int year, int month) {
        // 這兩個值來自網址，使用者想改就改，所以一定要擋。
        if (month < 1 || month > 12) {
            throw ApiException.badRequest("VALIDATION_ERROR", "月份必須介於 1 到 12");
        }
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw ApiException.badRequest("VALIDATION_ERROR",
                    "年份必須介於 " + MIN_YEAR + " 到 " + MAX_YEAR);
        }

        // 月初零點整（含）到下月初零點整（不含）。
        // plusMonths(1) 不必自己處理 12 月要跳年、也不必記每個月幾天，
        // 更不會有二月的閏年問題——java.time 全部算好了。
        LocalDateTime start = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime end = start.plusMonths(1);

        String me = currentAgentProvider.currentAgentId();

        List<FollowUps> followUps = followUpsRepository
                .findByAgentIdAndFollowUpAtGreaterThanEqualAndFollowUpAtLessThanOrderByFollowUpAtAscFollowUpIdAsc(
                        me, start, end);

        // 這個月沒排任何事情就到此為止：再發一次 WHERE ticket_id IN (...) 沒有意義。
        if (followUps.isEmpty()) {
            return CalendarMonthResponse.from(year, month, followUps, Map.of());
        }

        // distinct()：目前 UQ_follow_ups_agent_ticket 保證同一人對同一單只會有一筆，
        // 所以其實不會重複；哪天為了「一張單排多次」拿掉那條約束，這裡不必跟著改。
        List<Integer> ticketIds = followUps.stream()
                .map(FollowUps::getTicketId)
                .distinct()
                .toList();

        Map<Integer, Tickets> ticketsById = ticketsRepository.findAllById(ticketIds).stream()
                .collect(Collectors.toMap(Tickets::getTicketId, Function.identity()));

        return CalendarMonthResponse.from(year, month, followUps, ticketsById);
    }

    /**
     * 排定或修改<b>自己</b>對某張工單的回電時間，對應 PATCH /api/calendar/{ticketNo}/followUp。
     * <p>
     * 沒排過就新增一筆、排過就改那一筆（upsert）。判斷依據是
     * {@code (agent_id, ticket_id)} 這個組合，資料庫也有
     * {@code UQ_follow_ups_agent_ticket} 這條唯一約束擋著，
     * 所以「同一張單在我的行事曆上出現兩次」不可能發生。
     * <p>
     * 動到的只有<b>自己</b>那一筆：同一張工單如果別的客服也排了回電，他那筆不受影響。
     * <p>
     * <b>刻意不擋「時間在過去」</b>：客服可能是事後補登昨天已經打過的回電，
     * 擋下來只會逼使用者去改系統時間或亂填。
     *
     * @param ticketNo   {@code String}——網址上的工單編號，格式 TK-XXXXXX
     * @param followUpAt {@code LocalDateTime}——要排定的回電時間，<b>不可為 null</b>
     *                   （取消排定請改用 {@link #deleteFollowUp(String)}）。
     *                   秒以下的位數會被截掉，因為欄位只存到秒
     * @param note       {@code String}——個人備註，<b>可為 null</b>；
     *                   只有空白字元也會被當成沒寫，存成 null
     * @return {@link CalendarEventResponse}——排好的那一格事件
     * @throws ApiException 404 / {@code TICKET_NOT_FOUND}——查無此單號；
     *                      400 / {@code VALIDATION_ERROR}——沒給回電時間，
     *                      或備註超過 {@value #NOTE_MAX_LENGTH} 個字
     */
    @Transactional
    public CalendarEventResponse updateFollowUp(String ticketNo, LocalDateTime followUpAt, String note) {
        // follow_up_at 在資料庫是 NOT NULL：沒有時間就不成其為一筆行事曆安排。
        // 舊版本用「傳 null 代表取消」，改用獨立的刪除方法之後那個特殊規則就不需要了。
        if (followUpAt == null) {
            throw ApiException.badRequest("VALIDATION_ERROR",
                    "回電時間不可為空；要取消排定請改用取消的 API");
        }

        // 前端把備註清空時送過來的是空字串而不是 null，兩種都當成「沒寫備註」，
        // 統一存成 null，之後查出來才不必分辨空字串和 null 哪個代表沒寫。
        String cleanNote = (note == null || note.isBlank()) ? null : note.trim();
        if (cleanNote != null && cleanNote.length() > NOTE_MAX_LENGTH) {
            throw ApiException.badRequest("VALIDATION_ERROR",
                    "備註不可超過 " + NOTE_MAX_LENGTH + " 個字");
        }

        Tickets ticket = findTicket(ticketNo);
        String me = currentAgentProvider.currentAgentId();
        // 欄位只存到秒，先自己截掉，回傳給前端的物件才會跟資料庫裡的值一致。
        LocalDateTime at = followUpAt.withNano(0);

        FollowUps followUp = followUpsRepository
                .findByAgentIdAndTicketId(me, ticket.getTicketId())
                .orElse(null);

        if (followUp == null) {
            followUp = followUpsRepository.save(FollowUps.builder()
                    .agentId(me)
                    .ticketId(ticket.getTicketId())
                    .followUpAt(at)
                    .note(cleanNote)
                    .build());
        } else {
            // 不必呼叫 save()：撈回來的是受管理的 entity，在這個有 @Transactional 的
            // 方法裡改欄位，交易結束時 Hibernate 會自己送出 UPDATE。
            followUp.setFollowUpAt(at);
            followUp.setNote(cleanNote);
        }

        return CalendarEventResponse.from(followUp, ticket);
    }

    /**
     * 取消<b>自己</b>對某張工單的回電排定，把那一列刪掉。
     * <p>
     * <b>沒排過也算成功</b>，不丟 404：使用者要的是「這張單不要出現在我的行事曆上」，
     * 本來就沒排的話那個狀態已經達成了。這樣前端連點兩次取消也不會跳錯誤，
     * 重送一次請求同樣安全（冪等）。
     * <p>
     * 只刪自己那一筆，別的客服對同一張工單的安排不受影響。
     *
     * @param ticketNo {@code String}——網址上的工單編號，格式 TK-XXXXXX
     * @throws ApiException 404 / {@code TICKET_NOT_FOUND}——查無此單號。
     *                      工單不存在跟「工單存在但沒排回電」是兩件事，前者仍然是錯誤
     */
    @Transactional
    public void deleteFollowUp(String ticketNo) {
        Tickets ticket = findTicket(ticketNo);
        String me = currentAgentProvider.currentAgentId();

        followUpsRepository.findByAgentIdAndTicketId(me, ticket.getTicketId())
                .ifPresent(followUpsRepository::delete);
    }

    // ------------------------------------------------------------------
    // 內部小工具
    // ------------------------------------------------------------------

    /**
     * 用對外的工單編號撈工單。內容與 {@code TicketService.findTicket()} 相同，
     * 原因見類別說明「為什麼不呼叫 TicketService」。
     *
     * @param ticketNo {@code String}——對外的工單編號，格式 TK-XXXXXX
     * @return {@link Tickets}——查到的工單，不會是 null
     * @throws ApiException 404 / {@code TICKET_NOT_FOUND}——查無此單號。
     *                      查不到不是程式壞掉，是使用者把單號打錯了，
     *                      所以回 404 而不是讓 NPE 冒出去變成 500
     */
    private Tickets findTicket(String ticketNo) {
        return ticketsRepository.findByTicketNo(ticketNo)
                .orElseThrow(() -> ApiException.notFound(
                        "TICKET_NOT_FOUND", "找不到工單：" + ticketNo));
    }
}
