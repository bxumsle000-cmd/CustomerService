package com.poz.CustomerService.repository;

import com.poz.CustomerService.entity.Agents;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 客服資料的存取入口。主鍵型別是 {@code String}（客服代號，例如 CSC00001）。
 * 目前沒有自訂查詢，全部使用 {@link JpaRepository} 內建的方法。
 */
@Repository
public interface AgentsRepository extends JpaRepository<Agents, String> {
}
