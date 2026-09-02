package com.poz.CustomerService.service;

import com.poz.CustomerService.dto.calendar.CalendarEventResponse;
import com.poz.CustomerService.dto.calendar.CalendarMonthResponse;
import com.poz.CustomerService.dto.calendar.FollowUpRequest;
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

/**
 * 行事曆相關的 business logic。
 *
 * <h2>一張工單可以排幾筆回電</h2>
 * 幾筆都可以，只要時間點不同——先週三初步回覆、再週五確認結果，
 * 或同一天上午下午各排一次，都成立。唯一擋下來的是「同一張單、同一個時間點排兩次」，
 * 那通常是誤按，由 {@code UQ_follow_ups_agent_ticket_time} 把關。
 * <p>
 * 因此<b>一筆安排的身分是 {@code followUpId}，不是工單編號</b>。
 * 改期和取消都吃流水號，前端從月檢視的回應裡拿。
 *
 * <h2>方法一覽</h2>
 * 對外開放（Controller 呼叫的）：
 * <ul>
 *   <li>{@link #monthlyFollowUps(int, int)}——查自己某個月排定的所有回電</li>
 *   <li>{@link #createFollowUp(String, FollowUpRequest)}——對某張工單新增一筆回電安排</li>
 *   <li>{@link #updateFollowUp(Integer, FollowUpRequest)}——改某一筆的時間或備註</li>
 *   <li>{@link #deleteFollowUp(Integer)}——取消某一筆</li>
 * </ul>
 * 內部小工具（private，Controller 叫不到）：
 * <ul>
 *   <li>{@link #findTicket(String)}——用 ticketNo 撈工單，撈不到丟 404</li>
 *   <li>{@link #findMyFollowUp(Integer)}——撈自己的某一筆安排，撈不到丟 404</li>
 * </ul>
 *
 * <h2>參數的檢查與正規化不在這裡</h2>
 * 「時間不可為空」「備註不超過 200 字」是 {@link FollowUpRequest} 上的
 * {@code @NotNull} / {@code @Size}；截秒和備註去空白在它的 constructor 裡。
 * 這支 Service 拿到的值已經是整理過的。
 * <p>
 * <b>但那兩條約束現在沒有人執行</b>——Bean Validation 要靠 Controller 的
 * {@code @Valid} 觸發，而 CalendarController 還沒做。補的時候別忘了掛。
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

    private static final int MIN_YEAR = 2015;
    private static final int MAX_YEAR = 2050;

    // ------------------------------------------------------------------
    // 對外的方法
    // ------------------------------------------------------------------

    /**
     * 查<b>自己</b>某個月排定的所有回電，對應 GET /api/calendar。行事曆的月檢視。
     * <p>
     * 掛 {@code readOnly = true}：整支方法只讀不寫，Hibernate 就不必為了偵測變更
     * 而保留每個 entity 的快照。
     *
     * <h3>為什麼這裡幾乎沒有程式碼</h3>
     * 一格事件的內容橫跨 follow_ups 和 tickets 兩張表，但那個 join 交給資料庫做了，
     * 見 {@code FollowUpsRepository.findMonthlyEvents}。這支方法只剩下驗參數、
     * 算出月份的時間區間、以及決定「查誰的」。
     *
     * @param year  {@code int}——西元年，{@value #MIN_YEAR} 到 {@value #MAX_YEAR}
     * @param month {@code int}——月份，<b>1 到 12</b>
     * @return {@link CalendarMonthResponse}——這個月的所有回電安排，依時間排序。
     *         同一張工單排了多筆就會出現多格事件。
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

        LocalDateTime start = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime end = start.plusMonths(1);

        String me = currentAgentProvider.currentAgentId();

        List<CalendarEventResponse> events = followUpsRepository.findMonthlyEvents(me, start, end);

        return new CalendarMonthResponse(year, month, events);
    }

    /**
     * 對某張工單新增一筆<b>自己</b>的回電安排，
     * 對應 POST /api/calendar/{ticketNo}/followUps。
     * <p>
     * 同一張單本來就排過別的時間也沒關係，這支<b>一律新增一列</b>，不會去改既有的那筆
     * （改期請用 {@link #updateFollowUp(Integer, FollowUpRequest)}）。
     * 唯一擋下來的是「同一張單、同一個時間點」已經有一筆，那是重複排定。
     * <p>
     * 新增的只有<b>自己</b>那一筆：同一張工單別的客服也排了回電，他那筆不受影響。
     * <p>
     * <b>刻意不擋「時間在過去」</b>：客服可能是事後補登昨天已經打過的回電，
     * 擋下來只會逼使用者去改系統時間或亂填。
     *
     * @param ticketNo {@code String}——網址上的工單編號，格式 TK-XXXXXX
     * @param request  {@link FollowUpRequest}——回電時間與備註。時間必填、備註選填，
     *                 截秒與備註正規化在它的 constructor 裡就做完了
     * @return {@link CalendarEventResponse}——剛排好的那一格事件，
     *         裡面的 {@code followUpId} 就是之後改期／取消要用的號碼
     * @throws ApiException 404 / {@code TICKET_NOT_FOUND}——查無此單號；
     *                      409 / {@code FOLLOW_UP_ALREADY_EXISTS}——這張單的這個時間點已經排過了。
     *                      「沒給時間」「備註超長」由 {@link FollowUpRequest} 上的約束負責，
     *                      Controller 掛 {@code @Valid} 之後會變成 400
     */
    @Transactional
    public CalendarEventResponse createFollowUp(String ticketNo, FollowUpRequest request) {
        Tickets ticket = findTicket(ticketNo);
        String me = currentAgentProvider.currentAgentId();

        // 資料庫的 UQ_follow_ups_agent_ticket_time 也會擋，但撞上去丟的是
        // DataIntegrityViolationException，使用者只會看到 500。先問一句才回得出人看得懂的訊息。
        if (followUpsRepository.existsByAgentIdAndTicketIdAndFollowUpAt(
                me, ticket.getTicketId(), request.followUpAt())) {
            throw ApiException.conflict("FOLLOW_UP_ALREADY_EXISTS",
                    "這張工單在這個時間已經排過回電了");
        }

        FollowUps followUp = followUpsRepository.save(FollowUps.builder()
                .agentId(me)
                .ticketId(ticket.getTicketId())
                .followUpAt(request.followUpAt())
                .note(request.note())
                .build());

        return CalendarEventResponse.from(followUp, ticket);
    }

    /**
     * 改<b>自己</b>某一筆回電安排的時間或備註，
     * 對應 PATCH /api/calendar/followUps/{followUpId}。
     * <p>
     * 吃的是安排流水號不是工單編號：一張單可以有好幾筆安排，單號講不清楚要改哪一筆。
     * 這個號碼前端是從月檢視的回應裡拿到的。
     * <p>
     * 時間和備註兩個都會被覆蓋，備註傳 null 或空白就是把備註清掉。
     * <p>
     * <b>刻意不擋「時間在過去」</b>，理由同
     * {@link #createFollowUp(String, FollowUpRequest)}。
     *
     * @param followUpId {@code Integer}——要改的那一筆的流水號，<b>必須是自己的</b>
     * @param request    {@link FollowUpRequest}——要改成的回電時間與備註。
     *                   取消排定請改用 {@link #deleteFollowUp(Integer)}，不是把時間傳成 null
     * @return {@link CalendarEventResponse}——改完的那一格事件
     * @throws ApiException 404 / {@code FOLLOW_UP_NOT_FOUND}——號碼不存在，<b>或那筆不是自己的</b>；
     *                      404 / {@code TICKET_NOT_FOUND}——安排指向的工單剛好被刪掉了；
     *                      409 / {@code FOLLOW_UP_ALREADY_EXISTS}——同一張單的那個時間點
     *                      已經有<b>另外</b>一筆安排。
     *                      「沒給時間」「備註超長」由 {@link FollowUpRequest} 上的約束負責
     */
    @Transactional
    public CalendarEventResponse updateFollowUp(Integer followUpId, FollowUpRequest request) {
        FollowUps followUp = findMyFollowUp(followUpId);

        // FollowUpIdNot：排除掉正在改的這一筆自己。少了它的話，
        // 只改備註（時間沒動）會查到自己，被誤判成重複排定。
        if (followUpsRepository.existsByAgentIdAndTicketIdAndFollowUpAtAndFollowUpIdNot(
                followUp.getAgentId(), followUp.getTicketId(),
                request.followUpAt(), followUp.getFollowUpId())) {
            throw ApiException.conflict("FOLLOW_UP_ALREADY_EXISTS",
                    "這張工單在這個時間已經排過回電了");
        }

        // 不必呼叫 save()：撈回來的是受管理的 entity，在這個有 @Transactional 的
        // 方法裡改欄位，交易結束時 Hibernate 會自己送出 UPDATE。
        followUp.setFollowUpAt(request.followUpAt());
        followUp.setNote(request.note());

        // 回應要顯示單號、主旨、狀態，所以得把工單撈出來。外鍵擋著，正常情況一定找得到。
        Tickets ticket = ticketsRepository.findById(followUp.getTicketId())
                .orElseThrow(() -> ApiException.notFound(
                        "TICKET_NOT_FOUND", "這筆安排對應的工單已不存在"));

        return CalendarEventResponse.from(followUp, ticket);
    }

    /**
     * 取消<b>自己</b>的某一筆回電安排，把那一列刪掉，
     * 對應 DELETE /api/calendar/followUps/{followUpId}。
     * <p>
     * 同一張工單的其他安排不受影響——刪的只有指名的這一筆。別的客服的安排也一樣不動。
     * <p>
     * <b>號碼不存在或不是自己的也算成功</b>，不丟 404：使用者要的是「這一格不要出現在
     * 我的行事曆上」，本來就不在的話那個狀態已經達成了。這樣前端連點兩次取消也不會跳錯誤，
     * 重送一次請求同樣安全（冪等）。
     * <p>
     * 「不是自己的」之所以能安全地當成沒事，是因為查詢條件帶了 {@code agentId}：
     * 撈不到就不會刪，別人的安排碰不到。
     *
     * @param followUpId {@code Integer}——要取消的那一筆的流水號
     */
    @Transactional
    public void deleteFollowUp(Integer followUpId) {
        String me = currentAgentProvider.currentAgentId();

        followUpsRepository.findByFollowUpIdAndAgentId(followUpId, me)
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

    /**
     * 撈<b>自己</b>的某一筆回電安排。改期前的第一道關卡，兼權限檢查。
     * <p>
     * 查詢條件同時帶 {@code followUpId} 和 {@code agentId}，所以別人的安排根本查不出來，
     * 不必撈回來再自己比對一次。
     * <p>
     * 「號碼不存在」和「號碼是別人的」都回同一個 404，<b>刻意不分開</b>：
     * 分得清楚等於告訴使用者「這個號碼是存在的，只是不屬於你」，
     * 那就可以拿號碼一個一個試，試出別人排了幾筆。
     *
     * @param followUpId {@code Integer}——安排流水號
     * @return {@link FollowUps}——自己的那一筆，不會是 null，而且是受 Hibernate 管理的 entity
     *         （在有 {@code @Transactional} 的方法裡改欄位即等於 UPDATE）
     * @throws ApiException 404 / {@code FOLLOW_UP_NOT_FOUND}——查無此安排，或那筆不是自己的
     */
    private FollowUps findMyFollowUp(Integer followUpId) {
        String me = currentAgentProvider.currentAgentId();
        return followUpsRepository.findByFollowUpIdAndAgentId(followUpId, me)
                .orElseThrow(() -> ApiException.notFound(
                        "FOLLOW_UP_NOT_FOUND", "找不到這筆回電安排"));
    }

}
