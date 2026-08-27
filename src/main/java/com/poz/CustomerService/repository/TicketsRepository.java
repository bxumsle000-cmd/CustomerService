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

    // TODO 工單詳情要「用對外編號 ticket_no 查一張工單」，這裡缺一支方法。
    //
    // 不用寫實作，Spring Data 會照方法名稱自動生 SQL——
    // 方法名要怎麼取才會變成 WHERE ticket_no = ?，去看 TicketCommentsRepository
    // 的 Javadoc 有個現成範例，規則是一樣的。
    //
    // 回傳型別想一下：查不到的時候要回什麼，才不會有人忘記檢查？
    // AgentService.findAgentOrThrow() 接的是哪一種型別，照著挑就對了。

}
