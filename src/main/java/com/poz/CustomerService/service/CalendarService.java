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
 * 行事曆相關的 business logic：查月份回電、新增、改期、取消。
 * <p>
 * 一張工單只要時間點不同就能排多筆，所以一筆安排的身分是 {@code followUpId}，不是工單編號。
 * <p>
 * 方法一覽：
 * <ul>
 *   <li>{@link #monthlyFollowUps} —— 查自己某個月的所有回電安排，依時間排序</li>
 *   <li>{@link #createFollowUp} —— 對某張工單新增一筆回電，同時間點不可重複</li>
 *   <li>{@link #updateFollowUp} —— 改一筆回電的時間與備註，兩個欄位都覆蓋</li>
 *   <li>{@link #deleteFollowUp} —— 取消一筆回電，刪不到也算成功（冪等）</li>
 *   <li>{@code findTicket}（private）—— 用工單編號撈工單，查不到丟 404</li>
 *   <li>{@code findMyFollowUp}（private）—— 撈自己的那一筆安排，兼權限檢查</li>
 * </ul>
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
     * 查自己某個月排定的所有回電。
     *
     * @param year  西元年，{@value #MIN_YEAR} 到 {@value #MAX_YEAR}
     * @param month 月份，1 到 12
     * @return 這個月的所有回電安排，依時間排序；沒排任何事情時 events 是空 list
     * @throws ApiException 400 / {@code VALIDATION_ERROR}——年份或月份超出範圍
     */
    @Transactional(readOnly = true)
    public CalendarMonthResponse monthlyFollowUps(int year, int month) {
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
     * 對某張工單新增一筆自己的回電安排，一律新增一列，不會去改既有的那筆。
     *
     * @param ticketNo 工單編號，格式 TK-XXXXXX
     * @param request  回電時間與備註，時間必填、備註選填
     * @return 剛排好的那一格事件，含改期／取消要用的 followUpId
     * @throws ApiException 404 / {@code TICKET_NOT_FOUND}——查無此單號；
     *                      409 / {@code FOLLOW_UP_ALREADY_EXISTS}——這張單的這個時間點已經排過了
     */
    @Transactional
    public CalendarEventResponse createFollowUp(String ticketNo, FollowUpRequest request) {
        Tickets ticket = findTicket(ticketNo);
        String me = currentAgentProvider.currentAgentId();

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
     * 改自己某一筆回電安排的時間或備註，兩個欄位都會被覆蓋。
     *
     * @param followUpId 要改的那一筆的流水號，必須是自己的
     * @param request    要改成的回電時間與備註
     * @return 改完的那一格事件
     * @throws ApiException 404 / {@code FOLLOW_UP_NOT_FOUND}——號碼不存在或不是自己的；
     *                      404 / {@code TICKET_NOT_FOUND}——安排指向的工單已不存在；
     *                      409 / {@code FOLLOW_UP_ALREADY_EXISTS}——那個時間點已經有另外一筆安排
     */
    @Transactional
    public CalendarEventResponse updateFollowUp(Integer followUpId, FollowUpRequest request) {
        FollowUps followUp = findMyFollowUp(followUpId);

        // FollowUpIdNot：排除正在改的這一筆，否則只改備註會被誤判成重複排定
        if (followUpsRepository.existsByAgentIdAndTicketIdAndFollowUpAtAndFollowUpIdNot(
                followUp.getAgentId(), followUp.getTicketId(),
                request.followUpAt(), followUp.getFollowUpId())) {
            throw ApiException.conflict("FOLLOW_UP_ALREADY_EXISTS",
                    "這張工單在這個時間已經排過回電了");
        }

        followUp.setFollowUpAt(request.followUpAt());
        followUp.setNote(request.note());

        Tickets ticket = ticketsRepository.findById(followUp.getTicketId())
                .orElseThrow(() -> ApiException.notFound(
                        "TICKET_NOT_FOUND", "這筆安排對應的工單已不存在"));

        return CalendarEventResponse.from(followUp, ticket);
    }

    /**
     * 取消自己的某一筆回電安排，把那一列刪掉。
     * 號碼不存在或不是自己的也算成功，不丟 404（冪等）。
     *
     * @param followUpId 要取消的那一筆的流水號
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
     * 用工單編號撈工單。
     *
     * @param ticketNo 工單編號，格式 TK-XXXXXX
     * @return 查到的工單，不會是 null
     * @throws ApiException 404 / {@code TICKET_NOT_FOUND}——查無此單號
     */
    private Tickets findTicket(String ticketNo) {
        return ticketsRepository.findByTicketNo(ticketNo)
                .orElseThrow(() -> ApiException.notFound(
                        "TICKET_NOT_FOUND", "找不到工單：" + ticketNo));
    }

    /**
     * 撈自己的某一筆回電安排，兼權限檢查（別人的安排查不出來）。
     *
     * @param followUpId 安排流水號
     * @return 自己的那一筆，不會是 null
     * @throws ApiException 404 / {@code FOLLOW_UP_NOT_FOUND}——查無此安排，或那筆不是自己的
     */
    private FollowUps findMyFollowUp(Integer followUpId) {
        String me = currentAgentProvider.currentAgentId();
        return followUpsRepository.findByFollowUpIdAndAgentId(followUpId, me)
                .orElseThrow(() -> ApiException.notFound(
                        "FOLLOW_UP_NOT_FOUND", "找不到這筆回電安排"));
    }

}
