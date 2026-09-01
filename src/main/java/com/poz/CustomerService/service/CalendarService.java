package com.poz.CustomerService.service;

import com.poz.CustomerService.dto.calendar.CalendarEventResponse;
import com.poz.CustomerService.dto.calendar.CalendarMonthResponse;
import com.poz.CustomerService.entity.TicketComments;
import com.poz.CustomerService.entity.Tickets;
import com.poz.CustomerService.exception.ApiException;
import com.poz.CustomerService.repository.TicketCommentsRepository;
import com.poz.CustomerService.repository.TicketsRepository;
import com.poz.CustomerService.security.CurrentAgentProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * 行事曆相關的 business logic。
 *
 * <h2>行事曆上的事件是什麼</h2>
 * 就是工單的 {@code follow_up_at}（排定的回電／跟進時間）。這裡<b>沒有</b>獨立的事件資料表：
 * 每一格事件背後都是一張工單，點下去就回到工單詳情。
 * 之後若要放請假、教育訓練那種跟工單無關的事件，得另開一張表，那是另一個題目。
 *
 * <h2>回電時間為什麼不在建單時填</h2>
 * 建單是在「記錄已經發生的事」，排回電是在「安排未來的事」，時機常常不同步——
 * 通話當下未必知道要幾號回電。所以 {@code CreateTicketRequest} 裡沒有這個欄位，
 * 唯一能設定它的入口就是這支 Service。
 *
 * <h2>方法一覽</h2>
 * 對外開放（Controller 呼叫的）：
 * <ul>
 *   <li>{@link #monthlyFollowUps(int, int)}——查自己某個月排定的所有回電</li>
 *   <li>{@link #updateFollowUp(String, LocalDateTime)}——設定或取消某張工單的回電時間</li>
 * </ul>
 * 內部小工具（private，Controller 叫不到）：
 * <ul>
 *   <li>{@link #findTicket(String)}——用 ticketNo 撈工單，撈不到丟 404</li>
 *   <li>{@link #writeComment(Tickets, String, String)}——寫一筆處理記錄</li>
 * </ul>
 *
 * <h2>為什麼不呼叫 TicketService</h2>
 * 這支 Service 動到的是 tickets 資料表，看起來很像該去借 {@code TicketService} 的方法用，
 * 但那兩支 private 小工具借不到，而讓 Service 互相呼叫會讓交易邊界和相依方向都變複雜
 * （誰包誰的交易？之後 TicketService 反過來要用行事曆的東西怎麼辦？）。
 * 這裡照專案現有的作法直接用 repository，代價是 {@link #findTicket} 和
 * {@link #writeComment} 跟 {@code TicketService} 各有一份幾乎一樣的實作——
 * 加起來不到十行，比綁死兩支 Service 划算。真的長出第三份時再抽成共用元件。
 */
@Service
@RequiredArgsConstructor
public class CalendarService {

    private final TicketsRepository ticketsRepository;
    private final TicketCommentsRepository ticketCommentsRepository;
    private final CurrentAgentProvider currentAgentProvider;

    // ------------------------------------------------------------------
    // 常數
    // ------------------------------------------------------------------

    /**
     * 處理記錄裡顯示回電時間用的格式，例如「2026-09-05 14:00」。
     * <p>
     * 只寫到分：回電是排給人看的行程，秒沒有意義，寫出來反而難讀。
     * 這只影響 timeline 上那句話長什麼樣，不影響存進資料庫的值。
     */
    private static final DateTimeFormatter FOLLOW_UP_TEXT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 可以查詢的年份下限。純粹擋離譜的輸入（例如網址被亂改成 year=0），
     * 不是業務規則——{@code LocalDate.of()} 遇到 0 或負數會丟 {@code DateTimeException}，
     * 那是 500，使用者只會看到「系統發生錯誤」。
     */
    private static final int MIN_YEAR = 2015;

    /** 可以查詢的年份上限，用意同 {@link #MIN_YEAR}。 */
    private static final int MAX_YEAR = 2050;

    // ------------------------------------------------------------------
    // 對外的方法
    // ------------------------------------------------------------------

    /**
     * 查<b>自己</b>某個月排定的所有回電，對應 GET /api/calendar。行事曆的月檢視。
     * <p>
     * 「自己」是指 {@link CurrentAgentProvider#currentAgentId()} 回傳的那個人，
     * 不接受前端指定要看誰的行事曆——不然只要改個網址參數就能看別人的行程。
     * 之後真要做「主管看整組」的功能，那是另一支方法加上權限判斷，不是在這裡加參數。
     * <p>
     * 掛 {@code readOnly = true}：整支方法只讀不寫，Hibernate 就不必為了偵測變更
     * 而保留每個 entity 的快照。
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

        List<Tickets> tickets = ticketsRepository
                .findByAssigneeIdAndFollowUpAtGreaterThanEqualAndFollowUpAtLessThanOrderByFollowUpAtAsc(
                        me, start, end);

        return CalendarMonthResponse.from(year, month, tickets);
    }

    /**
     * 設定或取消某張工單的回電時間，對應 PATCH /api/calendar/{ticketNo}/followUp。
     * <p>
     * 傳 {@code null} 代表<b>取消</b>排定，這是合法操作，不是漏填。
     * <p>
     * 改完會在工單的 timeline 上留一筆記錄，掛在<b>操作的人</b>名下而不是系統名下——
     * 「誰把客戶的回電時間往後挪了」是之後追進度時會想知道的事。
     * （{@code TicketService.changeStatus()} 的狀態變更掛系統，是因為它也會在建單時
     * 自動發生、當下沒有「某人手動做了這件事」可言，跟這裡情況不同。）
     * <p>
     * 不必呼叫 {@code save()}：{@link #findTicket(String)} 撈回來的是受管理的 entity，
     * 在這個有 {@code @Transactional} 的方法裡改欄位，交易結束時 Hibernate 會自己送出 UPDATE。
     * <p>
     * <b>刻意不擋「時間在過去」</b>：客服可能是事後補登昨天已經打過的回電，
     * 擋下來只會逼使用者去改系統時間或亂填。
     *
     * @param ticketNo   {@code String}——網址上的工單編號，格式 TK-XXXXXX
     * @param followUpAt {@code LocalDateTime}——要排定的回電時間；<b>傳 null 代表取消排定</b>。
     *                   秒以下的位數會被截掉，因為欄位只存到秒
     * @return {@link CalendarEventResponse}——改完的那一格事件。
     *         取消排定時 {@code followUpAt} 會是 null，前端據此把該格從畫面上移除
     * @throws ApiException 404 / {@code TICKET_NOT_FOUND}——查無此單號
     */
    @Transactional
    public CalendarEventResponse updateFollowUp(String ticketNo, LocalDateTime followUpAt) {
        Tickets ticket = findTicket(ticketNo);

        if (Objects.equals(ticket.getFollowUpAt(), followUpAt)) {
            return CalendarEventResponse.from(ticket);
        }

        ticket.setFollowUpAt(followUpAt);

        String me = currentAgentProvider.currentAgentId();
        if (followUpAt == null) {
            writeComment(ticket, me, "取消排定的回電時間");
        } else {
            writeComment(ticket, me, "排定回電時間 "
                    + followUpAt.format(FOLLOW_UP_TEXT_FORMAT));
        }

        return CalendarEventResponse.from(ticket);
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
     * 寫一筆處理記錄。內容與 {@code TicketService.writeComment()} 相同。
     *
     * @param ticket  {@link Tickets}——已經存在於資料庫的工單，這裡要拿它的 ticketId
     * @param agentId {@code String}——留言者的客服代號；傳 null 代表系統事件。
     *                行事曆這邊一律傳實際操作的人，不傳 null
     * @param content {@code String}——記錄內容，不可為 null
     */
    private void writeComment(Tickets ticket, String agentId, String content) {
        ticketCommentsRepository.save(TicketComments.builder()
                .ticketId(ticket.getTicketId())
                .agentId(agentId)
                .content(content)
                .build());
        // createdAt 由 TicketComments 的 @PrePersist 補。
    }
}
