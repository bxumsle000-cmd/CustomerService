package com.poz.CustomerService.dto.ticket;

import com.poz.CustomerService.entity.Tickets;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 工單列表的回應，對應 GET /api/tickets。
 *
 * @param content       這一頁的工單；沒有符合條件的資料時是空 list，不會是 null
 * @param page          目前頁碼，<b>從 1 開始</b>
 * @param size          每頁筆數
 * @param totalElements 符合條件的總筆數
 * @param totalPages    總頁數
 */
public record TicketPageResponse(
        List<TicketListItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    /**
     * 從 Spring Data 的 {@code Page} 轉成回應，頁碼由 0-based 轉成 1-based。
     *
     * @param tickets repository 查回來的一頁，含內容與總筆數／總頁數
     * @return page 已經轉成 1-based 的列表回應
     */
    public static TicketPageResponse from(Page<Tickets> tickets) {
        return new TicketPageResponse(
                tickets.getContent().stream()
                        .map(TicketListItemResponse::from)
                        .toList(),
                tickets.getNumber() + 1,
                tickets.getSize(),
                tickets.getTotalElements(),
                tickets.getTotalPages()
        );
    }
}
