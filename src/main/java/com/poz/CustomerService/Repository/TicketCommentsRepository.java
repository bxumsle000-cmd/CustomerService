package com.poz.CustomerService.Repository;

import com.poz.CustomerService.domain.TicketComments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketCommentsRepository extends JpaRepository<TicketComments,Integer> {

}