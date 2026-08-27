package com.poz.CustomerService.repository;

import com.poz.CustomerService.entity.TicketComments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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

    // ----------------------------------------------------------------
    // TODO 工單詳情的 timeline 需要「某張工單底下的所有留言，由舊到新」。
    //      就是上面 Javadoc 寫的那一行，照抄即可：
    //
    //   List<TicketComments> findByTicketIdOrderByCreatedAtAsc(Integer ticketId);
    //
    // 拆開來看方法名是怎麼變成 SQL 的：
    //   findBy + TicketId          →  WHERE ticket_id = ?
    //   OrderBy + CreatedAt + Asc  →  ORDER BY created_at ASC
    //
    // 參數是「內部流水號 ticketId」不是 ticketNo——FK 接的是 ticket_id。
    //
    // 記得 import java.util.List;
    // ----------------------------------------------------------------

}
