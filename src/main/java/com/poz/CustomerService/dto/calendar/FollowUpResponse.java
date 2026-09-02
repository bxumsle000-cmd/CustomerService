package com.poz.CustomerService.dto.calendar;

import com.poz.CustomerService.entity.FollowUps;

import java.time.LocalDateTime;

/**
 * 工單詳情頁上列出的「我對這張單排的一筆回電」。
 *
 * @param followUpId 安排流水號，改期／取消時原樣送回後端
 * @param followUpAt 排定的回電時間，不會是 null
 * @param note       個人備註，可為 null，只有自己看得到
 */
public record FollowUpResponse(
        Integer followUpId,
        LocalDateTime followUpAt,
        String note
) {

    /**
     * 從 entity 轉成 DTO，不帶出 {@code agentId}。
     *
     * @param followUp 回電安排，不可為 null，必須是自己的那一筆
     * @return 詳情頁用得到的三個欄位
     */
    public static FollowUpResponse from(FollowUps followUp) {
        return new FollowUpResponse(
                followUp.getFollowUpId(),
                followUp.getFollowUpAt(),
                followUp.getNote()
        );
    }
}
