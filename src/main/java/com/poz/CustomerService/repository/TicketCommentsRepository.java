package com.poz.CustomerService.repository;

import com.poz.CustomerService.entity.TicketComments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 工單留言／處理記錄的存取入口。主鍵型別是 {@code Integer}（留言流水號，由資料庫發號）。
 * <p>
 * 還沒有處理記錄相關的 Service，所以這個介面自己一支方法都沒有，
 * 目前只用 {@link JpaRepository} 內建的 {@code save} / {@code findById} /
 * {@code deleteById} / {@code existsById}。
 * <p>
 * 之後要「查某張工單底下的所有留言」時，在這裡加一行就有：
 * <pre>
 * List&lt;TicketComments&gt; findByTicketIdOrderByCreatedAtAsc(Integer ticketId);
 * </pre>
 */
@Repository
public interface TicketCommentsRepository extends JpaRepository<TicketComments, Integer> {
    List<TicketComments> findByTicketIdOrderByCreatedAtAscCommentIdAsc(Integer ticketId);
}
