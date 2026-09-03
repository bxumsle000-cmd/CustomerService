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
     * 不篩——{@code like '%%'} 會把 {@code null} 的資料列排除掉，
     * 空字串要在 Service 先轉成 null。
     * <p>
     * 四個 {@code ...Like} 參數收的是<b>已經包好萬用字元的 pattern</b>（例如
     * {@code %測試%}），不是原始的關鍵字。包 {@code %} 的工作交給 Service，
     * 這裡<b>刻意不用 JPQL 的 concat()</b>：SQL Server dialect 會把它轉成
     * {@code cast(? as varchar(max))}，varchar 存不了中文，中文條件會全部查不到。
     * 參數直接送進來則是 nvarchar（mssql-jdbc 預設），中文才不會被吃掉。
     * <p>
     * SQL Server 預設定序不分大小寫，所以不必額外套 {@code lower()}。
     * 前面帶萬用字元的 {@code like '%xxx%'} 吃不到索引，這是模糊查本身的限制。
     *
     * @param ticketNoLike     工單編號的 like pattern，例如 %TK-0001%；null 表示不篩
     * @param customerNameLike 客戶姓名的 like pattern；null 表示不篩
     * @param contactPhoneLike 聯絡電話的 like pattern；null 表示不篩
     * @param assigneeIdLike   負責客服代號的 like pattern；null 表示不篩
     * @param status           處理狀態，<b>精確</b>比對；null 表示不篩（對應「全部」tab）
     * @param createdFrom      只要這個時間點<b>之後</b>建立的；null 表示不限時間
     * @param pageable         分頁與排序，由 Service 決定
     * @return 這一頁的工單與總筆數；沒有符合的資料時 content 是空 list
     */
    @Query("""
            select t
            from Tickets t
            where (:ticketNoLike is null or t.ticketNo like :ticketNoLike)
              and (:customerNameLike is null or t.customerName like :customerNameLike)
              and (:contactPhoneLike is null or t.contactPhone like :contactPhoneLike)
              and (:assigneeIdLike is null or t.assigneeId like :assigneeIdLike)
              and (:status is null or t.status = :status)
              and (:createdFrom is null or t.createdAt >= :createdFrom)
            """)
    Page<Tickets> search(@Param("ticketNoLike") String ticketNoLike,
                         @Param("customerNameLike") String customerNameLike,
                         @Param("contactPhoneLike") String contactPhoneLike,
                         @Param("assigneeIdLike") String assigneeIdLike,
                         @Param("status") String status,
                         @Param("createdFrom") LocalDateTime createdFrom,
                         Pageable pageable);
}
