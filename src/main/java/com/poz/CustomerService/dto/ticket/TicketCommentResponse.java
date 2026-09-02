package com.poz.CustomerService.dto.ticket;

import com.poz.CustomerService.entity.TicketComments;

import java.time.LocalDateTime;

/**
 * 工單詳情裡 timeline 的「一則處理記錄」，只帶客服代號、不帶姓名。
 *
 * @param commentId 留言流水號，同一秒建立的多筆記錄靠它決定先後順序
 * @param agentId   留言者的客服代號；null 代表系統事件
 * @param content   留言內容或系統事件描述
 * @param createdAt 留言時間，只精確到秒
 */
public record TicketCommentResponse(
        Integer commentId,
        String agentId,
        String content,
        LocalDateTime createdAt
) {

    /**
     * 從 entity 轉成 DTO。
     *
     * @param comment 來源 entity，不可為 null
     * @return timeline 用得到的四個欄位
     */
    public static TicketCommentResponse from(TicketComments comment) {
        return new TicketCommentResponse(
                comment.getCommentId(),
                comment.getAgentId(),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }
}
