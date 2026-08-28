package com.poz.CustomerService.dto;

import com.poz.CustomerService.entity.Tickets;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

/**
 * 工單列表的回應，對應 GET /api/tickets。
 * <p>
 * 首頁（index.html:641 renderTickets）整頁的資料來源：表格內容來自 {@code content}、
 * 底下的頁碼來自 {@code page} / {@code size} / {@code totalElements} / {@code totalPages}、
 * 上方四個 tab 括號裡的數字來自 {@code tabCounts}。
 *
 * @param content       {@code List<TicketListItemResponse>}——這一頁的工單。
 *                      沒有符合條件的資料時是空 list，不會是 null
 * @param page          {@code int}——目前頁碼，<b>從 1 開始</b>
 * @param size          {@code int}——每頁筆數
 * @param totalElements {@code long}——符合條件的總筆數。用 long 是因為 JPA 的 count 回傳 long
 * @param totalPages    {@code int}——總頁數
 * @param tabCounts     {@code Map<String, Long>}——四個 tab 各自的件數，
 *                      key 固定是 ALL / IN_PROGRESS / PENDING / RESOLVED 四個都在，
 *                      就算某一種是 0 也有那個 key（否則前端會顯示 undefined）
 */
public record TicketPageResponse(
        List<TicketListItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        Map<String, Long> tabCounts
) {

    /**
     * 從 Spring Data 的 {@code Page} 轉成回應。
     * <p>
     * <b>頁碼加一就發生在這裡，而且只在這裡。</b>Spring Data 的頁碼從 0 開始、
     * 我們的 API 從 1 開始，這個轉換若散在各處，遲早有一支忘記加，
     * 使用者點第一頁卻看到第二頁的資料——而且不會有任何錯誤訊息。
     *
     * @param tickets   {@code Page<Tickets>}——repository 查回來的一頁，
     *                  它同時帶著內容與總筆數 / 總頁數
     * @param tabCounts {@code Map<String, Long>}——由 Service 算好傳進來。
     *                  這個 DTO 不碰 repository，所以自己算不出來
     * @return {@link TicketPageResponse}——page 已經轉成 1-based
     */
    public static TicketPageResponse from(Page<Tickets> tickets, Map<String, Long> tabCounts) {
        return new TicketPageResponse(
                tickets.getContent().stream()
                        .map(TicketListItemResponse::from)
                        .toList(),
                tickets.getNumber() + 1,   // Page 是 0-based，對外要 1-based
                tickets.getSize(),
                tickets.getTotalElements(),
                tickets.getTotalPages(),
                tabCounts
        );
    }
}
