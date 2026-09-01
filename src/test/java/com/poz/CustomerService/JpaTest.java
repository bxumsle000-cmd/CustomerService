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
 * JPA 語法練習場。
 *
 * <h2>怎麼用</h2>
 * 先 {@code docker compose up -d} 把資料庫開起來，在下面的方法裡寫想試的語法，
 * 點方法左邊的綠色三角形執行，結果看 IDEA 下方的 Run 視窗。
 *
 * <h2>JpaRepository 免費送的方法</h2>
 * {@code findAll()} / {@code findById(主鍵)} → Optional / {@code count()} /
 * {@code existsById(主鍵)} / {@code save(物件)}（新增與更新共用）/ {@code deleteById(主鍵)}。
 * <p>
 * 想要自訂查詢，到 repository 介面加一行方法簽章就好、不用寫實作，
 * 例如 {@code List<Agents> findByStatus(String status)}，Spring Data 會照名稱自動生 SQL。
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

//==========================================================================
// TicketRepository   Test
//==========================================================================
    @Autowired
    TicketsRepository ticketsRepository;

    // Tickets 有 @Builder，所以改用這種一路串下去的寫法，取代原本九行 setter。
    // 好處是整段是一個運算式：物件要嘛完整建好、要嘛還不存在，
    // 不會有「setter 才設到一半」的半成品被別的程式碼看到。
    //
    // 沒填的欄位（ticketId / followUpAt / createdAt / updatedAt）會是 null，
    // 這是對的——ticketId 由資料庫 IDENTITY 發號，時間戳由 @PrePersist 補。
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


    /** 查單筆 + 刪除。findById 回傳 Optional，沒查到不會是 null。 */
    @Test
    void findAndDeleteComment() {
        Integer id = ticketCommentsRepository.save(newComment("等一下要被刪掉")).getCommentId();

        ticketCommentsRepository.findById(id).ifPresent(System.out::println);

        ticketCommentsRepository.deleteById(id);
        System.out.println("刪除後還在嗎？" + ticketCommentsRepository.existsById(id));
    }

    /**
     * 上面測試共用的小工具，省得每次都寫三行。
     *
     * @param content {@code String}——留言內容
     * @return {@link TicketComments}——還<b>沒存進資料庫</b>，ticketId / agentId 已填測試常數；
     *         commentId 和 createdAt 是 null，要 save() 之後才有值
     */
    private TicketComments newComment(String content) {
        TicketComments comment = new TicketComments();
        comment.setTicketId(TEST_TICKET_ID);
        comment.setAgentId(TEST_AGENT_ID);
        comment.setContent(content);
        return comment;
    }
}
