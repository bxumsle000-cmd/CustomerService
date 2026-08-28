package com.poz.CustomerService.repository;

import com.poz.CustomerService.entity.Tickets;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 工單資料的存取入口。主鍵型別是 {@code Integer}（工單流水號，由資料庫發號）。
 * <p>
 * 還沒有 TicketService，所以這個介面自己一支方法都沒有，
 * 目前只有 {@code JpaTest} 在用 {@link JpaRepository} 內建的那幾支：
 * {@code save} / {@code findById} / {@code findAll} / {@code deleteById} / {@code existsById}。
 * <p>
 * <b>提醒</b>：{@code save()} 傳進一個「有主鍵但不是從資料庫撈出來」的 Tickets 時會走 merge，
 * 等於整筆覆蓋——沒填到的欄位會被寫成 null。改資料請先 {@code findById} 撈出來再改。
 */
@Repository
public interface TicketsRepository extends JpaRepository<Tickets, Integer> {

    // TODO(1) 工單詳情要「用對外編號 ticket_no 查一張工單」，這裡缺一支方法。
    //
    // 不用寫實作，Spring Data 會照方法名稱自動生 SQL——
    // 方法名要怎麼取才會變成 WHERE ticket_no = ?，去看 TicketCommentsRepository
    // 的 Javadoc 有個現成範例，規則是一樣的。
    //
    // 回傳型別想一下：查不到的時候要回什麼，才不會有人忘記檢查？
    // AgentService.findAgentOrThrow() 接的是哪一種型別，照著挑就對了。


    // TODO(2) 首頁列表 GET /api/tickets 的查詢。這是整個練習最難的一支，慢慢來。
    //
    // 難在「動態條件」：使用者可能一個篩選都沒填，也可能填了單號 + 電話 + 時間。
    // 衍生查詢（findByStatusAndTicketNoContaining...）做不到——那是固定條件，
    // 使用者沒填的欄位會變成 WHERE ticket_no LIKE '%null%'，什麼都查不到。
    //
    // 三種做法，由易到難：
    //
    //   (a) @Query + 「參數是 null 就跳過這個條件」
    //
    //       @Query("""
    //           SELECT t FROM Tickets t
    //           WHERE (:status IS NULL OR t.status = :status)
    //             AND (:ticketNo IS NULL OR t.ticketNo LIKE %:ticketNo%)
    //             AND ...
    //           """)
    //       Page<Tickets> search(@Param("status") String status, ..., Pageable pageable);
    //
    //       看得懂、好維護，SQL Server 也吃得下。**建議先做這個。**
    //       （JPQL 語法去 docs/jpql.md 複習；:param 是具名參數。）
    //
    //   (b) Specification（Criteria API）——條件用 Java 組出來，最靈活，但很囉嗦。
    //   (c) QueryDSL——要另外裝東西，這個專案沒有。
    //
    // 幾個實作細節：
    //
    //   - 排序：原型是「最近有動靜的排前面」（index.html:637 的 sort by updatedAt 遞減）。
    //     排序寫在 JPQL 的 ORDER BY，還是包在 Pageable 裡由 Service 決定？
    //     兩種都行，挑一個並寫成註解。
    //
    //   - 模糊查：LIKE %x% 是「包含」。原型的姓名/單號比對有 toLowerCase()，
    //     SQL Server 預設定序（collation）通常本來就不分大小寫，
    //     所以這裡多半不用特別處理——但這是**我的推測**，你的資料庫定序是什麼，
    //     實際查一次比較準（SELECT SERVERPROPERTY('Collation')）。
    //
    //   - timeRange（ALL / D1 / D7 / D30）不要傳字串進 JPQL。
    //     在 Service 先算成一個 LocalDateTime 起點，這裡收「createdAt >= ?」就好；
    //     ALL 就傳 null，走上面「IS NULL 就跳過」那條路。
    //
    //   - 回傳型別用 Page<Tickets>：它同時帶著「這一頁的內容」和「總筆數 / 總頁數」，
    //     Spring Data 會自動幫你多發一個 count 查詢。回 List 的話總筆數要自己再查一次。
    //
    //
    /**
     * 某個狀態目前有幾張工單。首頁四個 tab 括號裡的數字用的就是這一支。
     * <p>
     * 這是衍生查詢：方法名叫 {@code countByStatus}，Spring Data 就自動生出
     * {@code SELECT COUNT(*) FROM tickets WHERE status = ?}，不用寫實作。
     * <p>
     * 三種狀態就呼叫三次，總共三個 count 查詢。也可以用
     * {@code GROUP BY status} 一次查完，但那樣要處理 {@code Object[]} 或投影，
     * 而且 GROUP BY <b>不會回傳件數為 0 的狀態</b>（那一列根本不存在），
     * 組 Map 時還是得自己補 0。tickets 是小表、狀態只有三種，
     * 這裡選可讀性優先。之後資料量大到有感再換。
     *
     * @param status {@code String}——IN_PROGRESS / PENDING / RESOLVED
     * @return {@code long}——筆數。沒有任何一張也會回 0，不會是 null
     */
    long countByStatus(String status);

}
