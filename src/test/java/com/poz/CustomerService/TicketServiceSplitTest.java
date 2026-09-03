package com.poz.CustomerService;

import com.poz.CustomerService.dto.ticket.CreateTicketRequest;
import com.poz.CustomerService.dto.ticket.TicketDetailResponse;
import com.poz.CustomerService.dto.ticket.TicketListItemResponse;
import com.poz.CustomerService.service.TicketDetailService;
import com.poz.CustomerService.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/** 暫時的驗證測試：確認 TicketService / TicketDetailService 拆開後行為沒變。 */
@SpringBootTest
@Transactional
class TicketServiceSplitTest {

    @Autowired
    TicketService ticketService;

    @Autowired
    TicketDetailService ticketDetailService;

    private TicketListItemResponse createOne(String status) {
        return ticketService.create(new CreateTicketRequest(
                "拆分驗證用工單", "測試客戶", "0955-000-999", "帳號問題",
                null, "這是建單當下的通話摘要", status, "PHONE"));
    }

    @Test
    void 建單會在同一個交易裡寫進三筆記錄() {
        TicketListItemResponse created = createOne("IN_PROGRESS");
        assertNotNull(created.ticketNo(), "ticketNo 應該由資料庫算好讀回來");

        TicketDetailResponse d = ticketDetailService.detail(created.ticketNo());
        d.comments().forEach(c -> System.out.println("  [" + c.agentId() + "] " + c.content()));

        // 進線來源 + 通話摘要 + 狀態設定，指派給自己所以沒有轉派那筆
        assertEquals(3, d.comments().size());
        assertNull(d.comments().get(0).agentId(), "第一筆是系統事件，agentId 應該是 null");
        assertEquals("工單經電話進線建立", d.comments().get(0).content());
        assertEquals("CSC00001", d.comments().get(1).agentId(), "通話摘要掛在建單的客服身上");
        assertEquals("狀態設定為「處理中」", d.comments().get(2).content());
    }

    @Test
    void 詳情頁的三個動作都要各補一筆記錄() {
        String no = createOne("IN_PROGRESS").ticketNo();

        TicketDetailResponse afterStatus = ticketDetailService.changeStatus(no, "PENDING");
        assertEquals("PENDING", afterStatus.status());
        assertEquals(4, afterStatus.comments().size());
        assertEquals("狀態由「處理中」變更為「待客戶回覆」",
                afterStatus.comments().get(3).content());

        TicketDetailResponse afterComment = ticketDetailService.submitContent(no, "已致電客戶");
        assertEquals(5, afterComment.comments().size());
        assertEquals("CSC00001", afterComment.comments().get(4).agentId());

        TicketDetailResponse afterAssign = ticketDetailService.assign(no, "CSC00001");
        assertEquals(6, afterAssign.comments().size());
        assertTrue(afterAssign.comments().get(5).content().contains("轉派給"));
    }

    @Test
    void 非法的狀態轉換要被擋下來() {
        String no = createOne("RESOLVED").ticketNo();
        // RESOLVED 只允許回到 IN_PROGRESS
        assertThrows(RuntimeException.class,
                () -> ticketDetailService.changeStatus(no, "PENDING"));
    }
}
