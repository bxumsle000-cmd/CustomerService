package com.poz.CustomerService;

import com.poz.CustomerService.repository.AgentsRepository;
import com.poz.CustomerService.repository.TicketCommentsRepository;
import com.poz.CustomerService.repository.TicketsRepository;
import com.poz.CustomerService.entity.Agents;
import com.poz.CustomerService.entity.TicketComments;
import com.poz.CustomerService.entity.Tickets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA 語法練習場。先 {@code docker compose up -d} 把資料庫開起來再執行各個測試方法。
 */
@SpringBootTest
@Transactional   // 跑完自動回滾；想讓資料真的存進去就把這一行註解掉
class JpaTest {

    @Autowired
    AgentsRepository agentsRepository;

//==========================================================================
// agentsRepository   Test
//==========================================================================
    @Test
    void prac() {
        // ---- 以下隨便改 ----
        agentsRepository.findById("CSC00001").ifPresent(System.out::println);
    }

    @Test
    @Commit
    void insert(){
        Agents agents = new Agents();
        agents.setAgentId("CSC004");
        agents.setName("陳小美");
        agents.setPasswordHash("pass");

        Agents saveAgnets =   agentsRepository.save(agents);
        System.out.println(saveAgnets);
    }

    @Test
    void delete(){
        agentsRepository.deleteById("CSC004");
    }

    @Test
    void pageable(){
        agentsRepository.findAll(PageRequest.of(1,2)).forEach(System.out::println);
    }

//==========================================================================
// TicketRepository   Test
//==========================================================================
    @Autowired
    TicketsRepository ticketsRepository;

    @Test
    void insertTicket() {
        Tickets tickets = Tickets.builder()
                .customerName("陳小美")
                .contactPhone("0973862551")
                .title("詢問帳單")
                .description("這次帳單異常高額，詢問原因")
                .status("RESOLVED")
                .category("帳單問題")
                .channel("Phone")
                .assigneeId("CSC00001")
                .build();

        Tickets saveTickets = ticketsRepository.save(tickets);
        System.out.println(saveTickets);
    }

    @Test
    void findTicket_id(){
        ticketsRepository.findById(4).ifPresent(System.out::println);
    }


//==========================================================================
// TicketCommentsRepository   Test
//==========================================================================

    @Autowired
    TicketCommentsRepository ticketCommentsRepository;

    // ticket_id 與 agent_id 都要是資料庫裡真的存在的值，外鍵會擋
    private static final Integer TEST_TICKET_ID = 4;
    private static final String  TEST_AGENT_ID  = "CSC00001";

    /** 新增一則留言，印出資料庫自動發的 commentId。 */
    @Test
    void insertComment() {
        TicketComments comment = new TicketComments();
        comment.setTicketId(TEST_TICKET_ID);
        comment.setAgentId(TEST_AGENT_ID);
        comment.setContent("已致電客戶說明帳單明細，客戶表示理解。");

        TicketComments saved = ticketCommentsRepository.save(comment);
        System.out.println(saved);
    }


    /** 查單筆 + 刪除。 */
    @Test
    void findAndDeleteComment() {
        Integer id = ticketCommentsRepository.save(newComment("等一下要被刪掉")).getCommentId();

        ticketCommentsRepository.findById(id).ifPresent(System.out::println);

        ticketCommentsRepository.deleteById(id);
        System.out.println("刪除後還在嗎？" + ticketCommentsRepository.existsById(id));
    }

    /**
     * 建立一個測試用的留言物件。
     *
     * @param content 留言內容
     * @return 還沒存進資料庫的留言，ticketId / agentId 已填測試常數
     */
    private TicketComments newComment(String content) {
        TicketComments comment = new TicketComments();
        comment.setTicketId(TEST_TICKET_ID);
        comment.setAgentId(TEST_AGENT_ID);
        comment.setContent(content);
        return comment;
    }
}
