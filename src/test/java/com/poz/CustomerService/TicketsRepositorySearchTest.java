package com.poz.CustomerService;

import com.poz.CustomerService.entity.Tickets;
import com.poz.CustomerService.repository.TicketsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** 暫時的驗證測試：確認兩支 @Query 真的跑得起來、條件真的有作用。 */
@SpringBootTest
@Transactional
class TicketsRepositorySearchTest {

    @Autowired
    TicketsRepository repo;

    private static final Sort SORT =
            Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.DESC, "ticketId"));

    private Tickets insert(String name, String phone, String title, String status) {
        return repo.save(Tickets.builder()
                .customerName(name).contactPhone(phone).title(title)
                .description("測試資料").status(status)
                .category("帳號問題").channel("PHONE").assigneeId("CSC00001")
                .build());
    }

    @Test
    void allNull_noFilter() {
        Page<Tickets> p = repo.search(null, null, null, null, null, null, PageRequest.of(0, 10, SORT));
        System.out.println("[allNull] totalElements=" + p.getTotalElements());
        assertTrue(p.getTotalElements() >= 0);
    }

    @Test
    void everyFilterWorks() {
        insert("測試甲", "0911-000-111", "測試單A", "IN_PROGRESS");
        insert("測試甲", "0911-000-222", "測試單B", "PENDING");
        insert("測試乙", "0922-000-333", "測試單C", "RESOLVED");
        repo.flush();

        var page = PageRequest.of(0, 50, SORT);

        // 姓名模糊
        assertEquals(2, repo.search(null, "%測試甲%", null, null, null, null, page).getTotalElements());
        // 電話模糊（中間片段也要中）
        assertEquals(2, repo.search(null, null, "%0911-000%", null, null, null, page).getTotalElements());
        // 狀態精確
        assertEquals(1, repo.search(null, "%測試甲%", null, null, "PENDING", null, page).getTotalElements());
        // 客服模糊
        assertTrue(repo.search(null, "%測試%", null, "%CSC000%", null, null, page).getTotalElements() >= 3);
        // 時間：未來的時間點應該一筆都撈不到
        assertEquals(0, repo.search(null, "%測試%", null, null, null,
                LocalDateTime.now().plusDays(1), page).getTotalElements());

    }


    @Test
    void ticketNoLike_andPaging() {
        Page<Tickets> p = repo.search("%TK-%", null, null, null, null, null, PageRequest.of(0, 3, SORT));
        System.out.println("[單號 TK-] total=" + p.getTotalElements() + " 本頁=" + p.getNumberOfElements()
                + " 總頁數=" + p.getTotalPages());
        assertTrue(p.getNumberOfElements() <= 3);
    }
}
