package com.poz.CustomerService.Repository;

import com.poz.CustomerService.domain.Tickets;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketsRepository extends JpaRepository<Tickets,Integer> {

    List<Tickets> findByCustomerName(String customerName);
}
