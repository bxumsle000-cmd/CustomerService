package com.poz.CustomerService.dto.ticket;

import com.poz.CustomerService.entity.TicketComments;

import java.time.LocalDateTime;

/**
 * 工單詳情裡 timeline 的「一則處理記錄」。四個欄位全部來自 {@code ticket_comments} 這張表。
 * <p>
 * <b>刻意不帶客服姓名</b>：畫面上顯示的就是代號（見 index.html 的 timeline，
 * 人工留言印的是 CSC00002 這種代號），首頁列表的 {@code assigneeId} 也是同樣作法。
 * 為了多一個沒人看的姓名欄位去查一次 agents 不划算。
 * 之後真的要顯示姓名再加——作法是在 Service 收集這批留言的 agentId、
 * 一次 {@code findAllById} 查回來做成 Map，不要在迴圈裡一筆一筆查。
 *
 * @param commentId {@code Integer}——留言流水號。同一秒建立的多筆記錄靠它決定先後順序
 * @param agentId   {@code String}——留言者的客服代號；<b>null 代表系統事件</b>
 *                  （建單、狀態變更、轉派）。前端就是看這個欄位決定要顯示成「系統」還是代號
 * @param content   {@code String}——留言內容或系統事件描述
 * @param createdAt {@code LocalDateTime}——留言時間。<b>只精確到秒</b>，
 *                  同一次建單寫入的幾筆時間會一模一樣
 */
public record TicketCommentResponse(
        Integer commentId,
        String agentId,
        String content,
        LocalDateTime createdAt
) {

    /**
     * 從 entity 轉成 DTO。
     * <p>
     * 四個欄位都在 {@link TicketComments} 這一張表裡，不需要 join，
     * 所以這支方法自己就能完成轉換（同 {@link TicketListItemResponse#from}）。
     *
     * @param comment {@link TicketComments}——來源 entity，不可為 null
     * @return {@link TicketCommentResponse}——timeline 用得到的四個欄位
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
