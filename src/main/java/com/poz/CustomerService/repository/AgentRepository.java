package com.poz.CustomerService.repository;

import com.poz.CustomerService.model.Agent;
import com.poz.CustomerService.model.AgentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 客服人員資料存取。
 *
 * 【為什麼只有介面、沒有實作類別？】
 * Spring Data JPA 會在啟動時掃到這個介面，自動幫你生一個實作出來注入。
 * 你要做的只是「宣告需要什麼查詢」，SQL 由方法名稱推導。
 *
 * 【JpaRepository&lt;Agent, String&gt; 的兩個型別參數】
 *   Agent  → 這個 repository 管哪個 Entity
 *   String → 那個 Entity 的主鍵型別（agentId 是 String）
 *
 * 繼承之後就免費得到一堆方法，常用的有：
 *   findById(agentId)   查一筆，回傳 Optional
 *   existsById(agentId) 只問存不存在，不撈資料（轉派時驗證代號用）
 *   save(agent)         新增或更新
 *   findAll()           查全部
 *   count()             計數
 * 這些都不用自己宣告，下面只寫「內建沒有」的查詢。
 */
@Repository
public interface AgentRepository extends JpaRepository<Agent, String> {

    /**
     * 客服清單，依代號排序。對應 GET /api/agents。
     *
     * 方法名稱的拆法：findAll + ByOrderBy + AgentId + Asc
     * 其實就是 findAll() 加上排序條件，Spring 會自動生成
     * SELECT ... FROM agents ORDER BY agent_id ASC
     */
    List<Agent> findAllByOrderByAgentIdAsc();

    /**
     * 依狀態查客服，例如找出目前所有「線上」可接聽的人。
     * 目前 API 還沒用到，但派工功能會需要。
     */
    List<Agent> findByStatus(AgentStatus status);

    /**
     * 依狀態計數，例如統計現在有幾個人在午休。
     */
    long countByStatus(AgentStatus status);
}
