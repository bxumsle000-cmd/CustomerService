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

    // ----------------------------------------------------------------
    // TODO 工單詳情需要「用對外編號 ticket_no 查一張工單」。
    //
    //   Optional<Tickets> findByTicketNo(String ticketNo);
    //
    // 只要宣告，不用寫實作——Spring Data 會照方法名稱自動生 SQL：
    //   findBy + TicketNo  →  WHERE ticket_no = ?
    // 這就是為什麼 entity 的屬性名不能有底線（見 Agents 的說明）。
    //
    // 回傳型別用 Optional 而不是 Tickets：查不到時是回一個空的 Optional，
    // 呼叫端用 .orElseThrow(...) 接，就不會有人忘記檢查 null。
    // 寫法可以參考 AgentService.findAgentOrThrow()。
    //
    // 記得 import java.util.Optional;
    // ----------------------------------------------------------------

}
