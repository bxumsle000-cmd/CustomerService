package com.poz.CustomerService;

import com.poz.CustomerService.Repository.AgentsRepository;
import com.poz.CustomerService.Repository.TicketCommentsRepository;
import com.poz.CustomerService.Repository.TicketsRepository;
import com.poz.CustomerService.domain.Agents;
import com.poz.CustomerService.domain.TicketComments;
import com.poz.CustomerService.domain.Tickets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA 語法練習場。
 *
 * 【怎麼用】
 *   1. 先確認資料庫開著：docker compose up -d
 *   2. 在下面 練習() 方法裡隨便寫你想試的 JPA 語法
 *   3. 點方法左邊的綠色三角形 ▶ 執行
 *   4. 結果看 IDEA 下方的 Run 視窗（System.out.println 印的東西會出現在那）
 *
 * 【JpaRepository 免費送你的方法，直接就能玩】
 *   findAll()              查全部，回傳 List<Agents>
 *   findById("CSC00001")   依主鍵查，回傳 Optional<Agents>
 *   count()                總筆數
 *   existsById("CSC00001") 存不存在，回傳 boolean
 *   save(物件)              新增或更新
 *   deleteById("CSC00001") 依主鍵刪除
 *
 * 【想試自訂查詢】
 *   到 AgentsRepository 介面裡加一行方法簽章就好，不用寫實作，例如：
 *       List<Agents> findByStatus(String status);
 *       List<Agents> findByNameContaining(String keyword);
 *   Spring Data 會自動照方法名稱幫你生 SQL。回來這裡就能呼叫。
 */
@SpringBootTest
@Transactional   // 跑完自動回滾，怎麼玩都不會弄髒資料庫。
                 // 想讓資料真的存進去（例如要去 SSMS 看），就把這一行前面加 // 註解掉。
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

    @Test
    void findName(){
        agentsRepository.findAllByName("黃志豪").forEach(System.out::println);
    }

    @Test
    void findNameAndStatus(){
        agentsRepository.findAllByNameAndStatus("林曉明","ONLINE")
                .forEach(System.out::println);
    }

    @Test
    void findAgentName(){
        agentsRepository.findName("ONLINE").forEach(System.out::println);
    }
//==========================================================================
// TicketRepository   Test
//==========================================================================
    @Autowired
    TicketsRepository ticketsRepository;

    @Test
    void insertTicket() {
        Tickets tickets = new Tickets();
        tickets.setTicketNo("TK-12345");
        tickets.setCustomerName("陳小美");
        tickets.setContactPhone("0973862551");
        tickets.setTitle("詢問帳單");
        tickets.setDescription("這次帳單異常高額，詢問原因");
        tickets.setStatus("RESOLVED");
        tickets.setCategory("帳單問題");
        tickets.setChannel("Phone");
        tickets.setAssigneeId("CSC00001");

        Tickets saveTickets = ticketsRepository.save(tickets);
        System.out.println(saveTickets);
    }

    @Test
    void findTicket_id(){
        ticketsRepository.findById(4).ifPresent(System.out::println);
    }

    @Test
    void  findByCustomerName(){
        ticketsRepository.findByCustomerName("陳小美").forEach(System.out::println);
    }


//==========================================================================
// TicketCommentsRepository   Test
//==========================================================================

    @Autowired
    TicketCommentsRepository ticketCommentsRepository;

    // 下面這些測試都靠 class 上的 @Transactional 自動回滾，
    // 所以「先塞資料、再查出來看」可以重複跑，不會把 ticket_comments 愈跑愈髒。
    //
    // 注意：ticket_id 要填資料庫裡真的存在的工單（外鍵 FK_ticket_comments_tickets 會擋），
    //       agent_id 同理要是真的客服代號，或乾脆填 null（系統事件）。
    private static final Integer TEST_TICKET_ID = 4;           // tickets 表裡既有的那筆 TK-12345
    private static final String  TEST_AGENT_ID  = "CSC00001";  // agents 表裡的林曉明

    /** 新增一則留言。重點看印出來的 commentId —— 那是資料庫自動發的號碼。 */
    @Test
    void insertComment() {
        TicketComments comment = new TicketComments();
        comment.setTicketId(TEST_TICKET_ID);
        comment.setAgentId(TEST_AGENT_ID);
        comment.setContent("已致電客戶說明帳單明細，客戶表示理解。");
        // createdAt 不用填，@PrePersist 會自動補現在時間

        TicketComments saved = ticketCommentsRepository.save(comment);
        System.out.println(saved);
    }


    /** 撈出某張工單的整串 timeline，由舊到新。 */
    @Test
    void findCommentsByTicket() {
        // 先塞兩筆，這樣就算資料庫本來是空的也看得到東西
        ticketCommentsRepository.save(newComment("第一則：客戶來電詢問"));
        ticketCommentsRepository.save(newComment("第二則：已回覆客戶"));

        ticketCommentsRepository.findByTicketIdOrderByCreatedAtAsc(TEST_TICKET_ID)
                .forEach(System.out::println);
    }

    /** 算某張工單有幾則記錄。count 系列回傳 long，不用自己撈出來再算 size()。 */
    @Test
    void countCommentsByTicket() {
        ticketCommentsRepository.save(newComment("測試用留言"));

        long count = ticketCommentsRepository.countByTicketId(TEST_TICKET_ID);
        System.out.println("工單 " + TEST_TICKET_ID + " 目前有 " + count + " 則記錄");
    }

    /** 查單筆 + 刪除。findById 回傳 Optional，沒查到不會是 null。 */
    @Test
    void findAndDeleteComment() {
        Integer id = ticketCommentsRepository.save(newComment("等一下要被刪掉")).getCommentId();

        ticketCommentsRepository.findById(id).ifPresent(System.out::println);

        ticketCommentsRepository.deleteById(id);
        System.out.println("刪除後還在嗎？" + ticketCommentsRepository.existsById(id));
    }

    /** 上面幾個測試共用的小工具，省得每次都寫三行。 */
    private TicketComments newComment(String content) {
        TicketComments comment = new TicketComments();
        comment.setTicketId(TEST_TICKET_ID);
        comment.setAgentId(TEST_AGENT_ID);
        comment.setContent(content);
        return comment;
    }
}
