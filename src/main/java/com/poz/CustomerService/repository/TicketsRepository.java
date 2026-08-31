package com.poz.CustomerService.repository;

import com.poz.CustomerService.entity.Tickets;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

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
    Optional<Tickets> findByTicketNo(String ticketNo);
}
