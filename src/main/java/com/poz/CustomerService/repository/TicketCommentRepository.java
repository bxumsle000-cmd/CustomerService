package com.poz.CustomerService.repository;

import com.poz.CustomerService.model.TicketComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 工單處理記錄／留言資料存取。
 *
 * JpaRepository&lt;TicketComment, Integer&gt; 的 Integer 是 commentId 的型別。
 */
@Repository
public interface TicketCommentRepository extends JpaRepository<TicketComment, Integer> {

    /**
     * 撈某張工單的完整 timeline，由舊到新。
     *
     * 前端顯示時是新的在上面，但這裡刻意用「舊到新」回傳：
     * 時間軸的原始順序就是由舊到新，要倒過來顯示是畫面的事，
     * 資料層保持自然順序，之後要做「只顯示最新 N 筆」之類的需求也比較好處理。
     *
     * 參數是 ticketId（內部主鍵），不是 ticketNo。
     * 呼叫端通常先用 ticketRepository.findByTicketNo() 拿到工單，
     * 再用 ticket.getTicketId() 來查這裡。
     */
    List<TicketComment> findByTicketIdOrderByCreatedAtAsc(Integer ticketId);

    /** 某張工單有幾筆處理記錄 */
    long countByTicketId(Integer ticketId);
}
