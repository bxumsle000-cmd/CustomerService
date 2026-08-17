package com.poz.CustomerService.repository;

import com.poz.CustomerService.model.Ticket;
import com.poz.CustomerService.model.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 工單資料存取。
 *
 * JpaRepository&lt;Ticket, Integer&gt; 的 Integer 是 ticketId（內部主鍵）的型別。
 * 注意：對外一律用 ticketNo，所以下面才需要 findByTicketNo。
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, Integer> {

    // ------------------------------------------------------------------
    // 工單詳情：GET /api/tickets/{ticketNo}
    // ------------------------------------------------------------------

    /** 依對外編號查工單。查無資料回傳空的 Optional，不會是 null */
    Optional<Ticket> findByTicketNo(String ticketNo);

    /** 產生新編號時檢查有沒有撞號 */
    boolean existsByTicketNo(String ticketNo);

    // ------------------------------------------------------------------
    // 首頁列表：GET /api/tickets
    // ------------------------------------------------------------------

    /**
     * 多條件查詢 + 分頁，對應首頁的篩選欄。**請呼叫這一支**，不要直接呼叫下面的
     * searchByPattern——這支會幫你把值處理好再轉過去。
     *
     * 【為什麼這組要自己寫 @Query？】
     * 因為每個條件都是「可有可無」的——使用者可能只填單號、也可能什麼都不填。
     * 用方法名稱推導的話，六個條件要排列組合出幾十個方法，不切實際。
     *
     * 排序和分頁交給 Pageable，呼叫端用
     * PageRequest.of(page, size, Sort.by("createdAt").descending()) 帶進來。
     */
    default Page<Ticket> search(TicketStatus status,
                                String ticketNo,
                                String customerName,
                                String phone,
                                String assigneeId,
                                LocalDateTime createdFrom,
                                Pageable pageable) {
        return searchByPattern(status,
                toLikePattern(ticketNo),
                toLikePattern(customerName),
                toLikePattern(phone),
                toLikePattern(assigneeId),
                createdFrom,
                pageable);
    }

    /**
     * 把使用者輸入轉成 LIKE 用的樣式；沒填或只有空白就回 null（代表不過濾）。
     *
     * 【為什麼空字串一定要轉成 null】
     * 前端沒填的欄位送過來是 ""，若直接拿去比對會變成 LIKE '%%'。
     * 在 SQL 裡 NULL LIKE '%%' 的結果是 NULL（不成立），
     * 所以 customer_name 是 NULL 的工單會被整批濾掉——看起來就像資料不見了。
     */
    private static String toLikePattern(String raw) {
        return (raw == null || raw.isBlank()) ? null : "%" + raw + "%";
    }

    /**
     * 實際執行查詢的方法。字串參數收到的是「已經包含 % 的完整樣式」。
     *
     * 【為什麼不在 JPQL 裡用 CONCAT('%', :x, '%')？】
     * 那樣 Hibernate 會產生 like ('%' + cast(? as varchar(max)) + '%')。
     * 注意是 varchar 不是 nvarchar——中文參數被 cast 成 varchar 會變成 ???，
     * 導致「姓名」「分類」這種中文欄位永遠查不到東西（實測確認過）。
     * 改成直接綁完整樣式，mssql-jdbc 預設就以 NVARCHAR 送出參數，中文才正常。
     *
     * 【:param IS NULL OR ... 是什麼意思】
     * 「這個條件沒給（null）就不過濾，有給才過濾」。
     */
    @Query("""
            SELECT t FROM Ticket t
            WHERE (:status IS NULL OR t.status = :status)
              AND (:ticketNo IS NULL OR t.ticketNo LIKE :ticketNo)
              AND (:customerName IS NULL OR t.customerName LIKE :customerName)
              AND (:phone IS NULL OR t.contactPhone LIKE :phone)
              AND (:assigneeId IS NULL OR t.assigneeId LIKE :assigneeId)
              AND (:createdFrom IS NULL OR t.createdAt >= :createdFrom)
            """)
    Page<Ticket> searchByPattern(@Param("status") TicketStatus status,
                                 @Param("ticketNo") String ticketNo,
                                 @Param("customerName") String customerName,
                                 @Param("phone") String phone,
                                 @Param("assigneeId") String assigneeId,
                                 @Param("createdFrom") LocalDateTime createdFrom,
                                 Pageable pageable);

    /** 各 tab 的筆數：全部 / 已處理 / 處理中 / 等待客戶回復 */
    long countByStatus(TicketStatus status);

    /** 側邊欄的未結案數量（GET /api/tickets/stats） */
    long countByStatusNot(TicketStatus status);

    // ------------------------------------------------------------------
    // 通話工作台：依進線號碼查歷史紀錄
    // ------------------------------------------------------------------

    /**
     * 對應原型右側的「此號碼歷史紀錄」，一頁 4 筆。
     * 方法名稱拆法：findBy + ContactPhone + OrderBy + CreatedAt + Desc
     */
    Page<Ticket> findByContactPhoneOrderByCreatedAtDesc(String contactPhone, Pageable pageable);

    // ------------------------------------------------------------------
    // 行事曆
    // ------------------------------------------------------------------

    /**
     * 某位客服在某段期間內排定的跟進安排。
     * 查整個月時，from 傳該月 1 號 00:00，to 傳下個月 1 號 00:00。
     *
     * Between 在 JPQL 是「包含兩端」，所以 to 要用下個月 1 號而不是這個月最後一天，
     * 才不會把最後一天 00:00 之後的資料漏掉。
     */
    List<Ticket> findByAssigneeIdAndFollowUpAtBetweenOrderByFollowUpAtAsc(
            String assigneeId, LocalDateTime from, LocalDateTime to);

    /**
     * 可排入行事曆的案件：自己的、且狀態為處理中或待客戶回覆。
     * 對應 GET /api/tickets/followable。
     *
     * StatusIn 表示「狀態是這幾個其中之一」，呼叫時傳
     * List.of(TicketStatus.IN_PROGRESS, TicketStatus.PENDING)
     */
    List<Ticket> findByAssigneeIdAndStatusInOrderByCreatedAtDesc(
            String assigneeId, Collection<TicketStatus> statuses);
}
