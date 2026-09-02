package com.poz.CustomerService.repository;

import com.poz.CustomerService.entity.TicketComments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 工單留言／處理記錄的存取入口。主鍵型別是 {@code Integer}（留言流水號）。
 */
@Repository
public interface TicketCommentsRepository extends JpaRepository<TicketComments, Integer> {

    /**
     * 查某張工單底下的所有處理記錄，由舊到新。
     *
     * @param ticketId 工單的內部流水號，不是 TK-000001 那種對外編號
     * @return 這張單的所有記錄，已排序；沒有留言時是空 list，不會是 null
     */
    List<TicketComments> findByTicketIdOrderByCreatedAtAscCommentIdAsc(Integer ticketId);
}
