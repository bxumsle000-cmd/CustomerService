package com.poz.CustomerService.repository;

import com.poz.CustomerService.entity.Tickets;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 工單資料的存取入口。主鍵型別是 {@code Integer}（工單流水號）。
 */
@Repository
public interface TicketsRepository extends JpaRepository<Tickets, Integer> {

    /**
     * 用對外的工單編號查工單。
     *
     * @param ticketNo 工單編號，格式 TK-XXXXXX
     * @return 查到才有值，否則 {@code Optional.empty()}
     */
    Optional<Tickets> findByTicketNo(String ticketNo);

    /**
     * 首頁列表的篩選查詢。
     * <p>
     * 每個條件都寫成「{@code 參數 is null} 就整條放行」，所以呼叫端<b>不想篩的欄位傳
     * null 就好</b>，不必為了不同的條件組合各寫一支查詢。注意傳空字串<b>不等於</b>
     * 不篩——那會變成「查值剛好是空字串的資料列」，一筆都撈不到。
     * 呼叫端要自己確保「沒篩的欄位」進來時就是 null。
     * <p>
     * 除了 {@code createdFrom} / {@code createdTo} 之外全部是<b>精確比對</b>：篩選欄要打完整的值，
     * 打一半查不到。SQL Server 預設定序不分大小寫，所以 {@code csc00001} 一樣撈得到
     * {@code CSC00001}。精確比對吃得到 {@code IX_tickets_assignee_status_created} 索引。
     *
     * @param ticketNo     工單編號，完整的 TK-XXXXXX；null 表示不篩
     * @param customerName 客戶姓名，要跟資料庫存的完全一樣（含先生／小姐）；null 表示不篩
     * @param contactPhone 聯絡電話，完整號碼；null 表示不篩
     * @param assigneeId   負責客服代號；null 表示不篩
     * @param status       處理狀態；null 表示不篩（對應「全部」tab）
     * @param createdFrom  區間起點，建立時間 &gt;= 這個時間點；null 表示不限起點
     * @param createdTo    區間終點，建立時間 &lt;= 這個時間點（<b>含</b>邊界）；null 表示不限終點。
     *                     兩個都傳就是閉區間；起點晚於終點不會報錯，就是撈不到資料
     * @param pageable     分頁與排序，由 Service 決定
     * @return 這一頁的工單與總筆數；沒有符合的資料時 content 是空 list
     */
    @Query("""
            select t
            from Tickets t
            where (:ticketNo is null or t.ticketNo = :ticketNo)
              and (:customerName is null or t.customerName = :customerName)
              and (:contactPhone is null or t.contactPhone = :contactPhone)
              and (:assigneeId is null or t.assigneeId = :assigneeId)
              and (:status is null or t.status = :status)
              and (:createdFrom is null or t.createdAt >= :createdFrom)
              and (:createdTo is null or t.createdAt <= :createdTo)
            """)
    Page<Tickets> search(@Param("ticketNo") String ticketNo,
                         @Param("customerName") String customerName,
                         @Param("contactPhone") String contactPhone,
                         @Param("assigneeId") String assigneeId,
                         @Param("status") String status,
                         @Param("createdFrom") LocalDateTime createdFrom,
                         @Param("createdTo") LocalDateTime createdTo,
                         Pageable pageable);
}
