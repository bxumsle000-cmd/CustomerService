package com.poz.CustomerService.Repository;

import com.poz.CustomerService.domain.Tickets;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface TicketsRepository extends JpaRepository<Tickets,Integer> {

    List<Tickets> findByCustomerName(String customerName);


    @Modifying
    @Transactional
    @Query("DELETE FROM Tickets t WHERE t.ticketId = ?1")
    int deleteByticketId(int ticketId);
}
