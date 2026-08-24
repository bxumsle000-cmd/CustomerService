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
 *       List<Agents> findByNameContaining(String 關鍵字);
 *   Spring Data 會自動照方法名稱幫你生 SQL。回來這裡就能呼叫。
 */
@SpringBootTest
@Transactional   // 跑完自動回滾，怎麼玩都不會弄髒資料庫。
                 // 想讓資料真的存進去（例如要去 SSMS 看），就把這一行前面加 // 註解掉。
class JpaTest {

    @Autowired
    AgentsRepository agentsRepository;
    TicketCommentsRepository ticketCommentsRepository;

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
        agents.setAgent_id("CSC004");
        agents.setName("陳小美");
        agents.setPassword_hash("pass");

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

//==========================================================================
// TicketRepository   Test
//==========================================================================
    @Autowired
    TicketsRepository ticketsRepository;

    @Test
    void insertTicket() {
        Tickets tickets = new Tickets();
        tickets.setTicket_no("TK-12345");
        tickets.setCustomer_name("陳小美");
        tickets.setContact_phone("0973862551");
        tickets.setTitle("詢問帳單");
        tickets.setDescription("這次帳單異常高額，詢問原因");
        tickets.setStatus("RESOLVED");
        tickets.setCategory("帳單問題");
        tickets.setChannel("Phone");
        tickets.setAssignee_id("CSC00001");

        Tickets saveTickets = ticketsRepository.save(tickets);
        System.out.println(saveTickets);
    }

    @Test
    void findTicket_id(){
        ticketsRepository.findById(4).ifPresent(System.out::println);
    }

    @Test
    void  findByName(){
        ticketsRepository.findByName("陳小美").forEach(System.out::println);
    }
}
