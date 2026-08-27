package com.poz.CustomerService.repository;

import com.poz.CustomerService.entity.Agents;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 客服資料的存取入口。主鍵型別是 {@code String}（客服代號，例如 CSC00001）。
 *
 * <h2>目前有哪些方法可用</h2>
 * 這個介面自己一支方法都沒有，全部來自 {@link JpaRepository}。常用的幾支：
 * <ul>
 *   <li>{@code findById(String agentId)} → {@code Optional<Agents>}——查不到是空的 Optional，不是 null</li>
 *   <li>{@code findAll()} → {@code List<Agents>}；也可傳 {@code Sort} 或 {@code Pageable}</li>
 *   <li>{@code save(Agents)} → 存回去的物件。<b>新增和修改共用這一支</b></li>
 *   <li>{@code existsById(String)} → {@code boolean}、{@code count()} → {@code long}</li>
 *   <li>{@code deleteById(String)}——查無此人不會報錯</li>
 * </ul>
 * 要自訂查詢的話，在這裡加一行方法簽章就好、不用寫實作，
 * Spring Data 會照方法名稱自動生 SQL，例如：
 * <pre>
 * List&lt;Agents&gt; findByStatus(String status);
 * </pre>
 */
@Repository
public interface AgentsRepository extends JpaRepository<Agents, String> {
}
