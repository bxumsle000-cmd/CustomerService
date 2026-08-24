package com.poz.CustomerService.Repository;

import com.poz.CustomerService.domain.TicketComments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketCommentsRepository extends JpaRepository<TicketComments,Integer> {

    // 依工單撈出整串處理記錄，並照建立時間由舊到新排序（工單詳情頁的 timeline）。
    // 方法名稱裡的 OrderByCreatedAtAsc 會被 Spring Data 翻成 SQL 的 ORDER BY created_at ASC，
    // 剛好用得到 V1__init_schema.sql 建的 IX_ticket_comments_ticket 索引。
    List<TicketComments> findByTicketIdOrderByCreatedAtAsc(Integer ticketId);

    // 這張工單目前有幾則記錄
    long countByTicketId(Integer ticketId);
}
