package com.poz.CustomerService;

import com.poz.CustomerService.dto.ticket.TicketPageResponse;
import com.poz.CustomerService.entity.Tickets;
import com.poz.CustomerService.repository.TicketsRepository;
import com.poz.CustomerService.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** 暫時的驗證測試：首頁列表的篩選，Repository 與 Service 兩層都跑。 */
@SpringBootTest
@Transactional
class TicketsRepositorySearchTest {

    @Autowired
    TicketsRepository repo;

    @Autowired
    TicketService ticketService;

    private static final Sort SORT =
            Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.DESC, "ticketId"));

    private static final PageRequest PAGE = PageRequest.of(0, 50, SORT);

    private void insert(String name, String phone, String title, String status) {
        repo.save(Tickets.builder()
                .customerName(name).contactPhone(phone).title(title)
                .description("測試資料").status(status)
                .category("帳號問題").channel("PHONE").assigneeId("CSC00001")
                .build());
    }

    /** 3 筆固定的測試資料，姓名照前端的做法連稱謂一起存。 */
    private void seed() {
        insert("測試甲先生", "0911-000-111", "測試單A", "IN_PROGRESS");
        insert("測試甲先生", "0911-000-222", "測試單B", "PENDING");
        insert("測試乙小姐", "0922-000-333", "測試單C", "RESOLVED");
        repo.flush();
    }

    // ------------------------------------------------------------------
    // Repository 層
    // ------------------------------------------------------------------

    @Test
    void allNull_noFilter() {
        Page<Tickets> p = repo.search(null, null, null, null, null, null, PageRequest.of(0, 10, SORT));
        System.out.println("[allNull] totalElements=" + p.getTotalElements());
        assertTrue(p.getTotalElements() >= 0);
    }

    @Test
    void everyFilterWorks() {
        seed();

        // 姓名精確：連稱謂一起打才會中，兩筆同名
        assertEquals(2, repo.search(null, "測試甲先生", null, null, null, null, PAGE).getTotalElements());
        // 電話精確：完整號碼只會中一筆
        assertEquals(1, repo.search(null, null, "0911-000-111", null, null, null, PAGE).getTotalElements());
        // 姓名 + 狀態一起下
        assertEquals(1, repo.search(null, "測試甲先生", null, null, "PENDING", null, PAGE).getTotalElements());
        // 客服代號精確
        assertTrue(repo.search(null, "測試甲先生", null, "CSC00001", null, null, PAGE).getTotalElements() == 2);
        // 時間：未來的時間點一筆都撈不到
        assertEquals(0, repo.search(null, "測試甲先生", null, null, null,
                LocalDateTime.now().plusDays(1), PAGE).getTotalElements());
    }

    @Test
    void 打一半查不到_這是精確比對的預期行為() {
        seed();
        assertEquals(0, repo.search(null, "測試甲", null, null, null, null, PAGE).getTotalElements(),
                "姓名少了稱謂就查不到");
        assertEquals(0, repo.search(null, null, "0911", null, null, null, PAGE).getTotalElements(),
                "電話只打前四碼查不到");
    }

    @Test
    void 定序不分大小寫_小寫的客服代號一樣撈得到() {
        seed();
        // 不寫死筆數：資料庫裡本來就可能有其他 CSC00001 的工單。
        // 只比對「大寫查」跟「小寫查」的結果一不一樣。
        long upper = repo.search(null, null, null, "CSC00001", null, null, PAGE).getTotalElements();
        long lower = repo.search(null, null, null, "csc00001", null, null, PAGE).getTotalElements();
        System.out.println("[collation] CSC00001=" + upper + " csc00001=" + lower);
        assertTrue(upper >= 3, "種子資料至少 3 筆");
        assertEquals(upper, lower);
    }

    // ------------------------------------------------------------------
    // Service 層
    // ------------------------------------------------------------------

    @Test
    void 空字串與空白要當成沒填() {
        seed();
        long all = ticketService.search(null, null, null, null, null, null, 1, 50).totalElements();

        // 空字串
        assertEquals(all, ticketService.search("", "", "", "", "", null, 1, 50).totalElements());
        // 全是空白
        assertEquals(all, ticketService.search("  ", "  ", "  ", "  ", "  ", null, 1, 50).totalElements());
    }

    @Test
    void 前後空白會被去掉() {
        seed();
        TicketPageResponse trimmed =
                ticketService.search(null, "  測試甲先生  ", null, null, null, null, 1, 50);
        assertEquals(2, trimmed.totalElements(), "前後空白不該影響比對結果");
    }

    @Test
    void 不合法的狀態要被擋下來而不是安靜回0筆() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> ticketService.search(null, null, null, null, "FOO", null, 1, 50));
        System.out.println("[bad status] " + e.getMessage());
    }

    @Test
    void 分頁參數的邊界() {
        assertThrows(RuntimeException.class,
                () -> ticketService.search(null, null, null, null, null, null, 0, 10));
        assertThrows(RuntimeException.class,
                () -> ticketService.search(null, null, null, null, null, null, 1, 51));
        // 上限 50 剛好可以
        assertDoesNotThrow(
                () -> ticketService.search(null, null, null, null, null, null, 1, 50));
    }

    @Test
    void 分頁切割正確() {
        seed();
        TicketPageResponse p1 = ticketService.search(null, "測試甲先生", null, null, null, null, 1, 1);
        TicketPageResponse p2 = ticketService.search(null, "測試甲先生", null, null, null, null, 2, 1);
        assertEquals(2, p1.totalElements());
        assertEquals(2, p1.totalPages());
        assertEquals(1, p1.content().size());
        assertEquals(1, p2.content().size());
        assertNotEquals(p1.content().get(0).ticketNo(), p2.content().get(0).ticketNo(),
                "第一頁跟第二頁不該是同一筆");
    }
}
