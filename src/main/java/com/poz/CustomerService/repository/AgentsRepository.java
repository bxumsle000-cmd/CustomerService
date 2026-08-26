package com.poz.CustomerService.repository;

import com.poz.CustomerService.entity.Agents;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// 目前只用得到 JpaRepository 內建的 findById / findAll（見 AgentService）。
// 自訂查詢等真的有需求時再加，一行方法簽章即可，不用寫實作。
@Repository
public interface AgentsRepository extends JpaRepository<Agents, String> {
}
