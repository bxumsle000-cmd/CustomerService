package com.poz.CustomerService.repository;

import com.poz.CustomerService.entity.Tickets;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
