package com.poz.CustomerService.repository;

import com.poz.CustomerService.entity.TicketComments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// 還沒有處理記錄相關的 Service，所以先保持空的。
@Repository
public interface TicketCommentsRepository extends JpaRepository<TicketComments, Integer> {
}
