package com.poz.CustomerService.repository;

import com.poz.CustomerService.entity.Tickets;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// 還沒有 TicketService，所以先保持空的。
@Repository
public interface TicketsRepository extends JpaRepository<Tickets, Integer> {
}
