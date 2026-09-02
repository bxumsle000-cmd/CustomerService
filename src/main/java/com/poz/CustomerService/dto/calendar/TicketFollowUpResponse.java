package com.poz.CustomerService.dto.calendar;

import com.poz.CustomerService.entity.FollowUps;

import java.time.LocalDateTime;

/**
 * 工單詳情頁上列出的「我對這張單排的一筆回電」。
 *
 * @param followUpId {@code Integer}——安排流水號，改期／取消時原樣送回後端
 * @param followUpAt {@code LocalDateTime}——排定的回電時間，<b>不會是 null</b>
 * @param note       {@code String}——個人備註，<b>可為 null</b>（沒寫就是 null）。
 *                   只有自己看得到，不會出現在底下的 timeline
 */
public record TicketFollowUpResponse(
        Integer followUpId,
        LocalDateTime followUpAt,
        String note
) {

    /**
     * 從 entity 轉成 DTO。「entity 的哪些欄位可以出去」只有這一個地方說了算——
     * 尤其是 {@code agentId}：它一定等於目前登入的人（查詢時就是這樣撈的），
     * 再回給前端沒有意義。
     *
     * @param followUp {@link FollowUps}——回電安排，不可為 null，必須是自己的那一筆
     * @return {@link TicketFollowUpResponse}——詳情頁用得到的三個欄位
     */
    public static TicketFollowUpResponse from(FollowUps followUp) {
        return new TicketFollowUpResponse(
                followUp.getFollowUpId(),
                followUp.getFollowUpAt(),
                followUp.getNote()
        );
    }
}
