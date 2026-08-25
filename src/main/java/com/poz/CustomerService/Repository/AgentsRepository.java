package com.poz.CustomerService.Repository;

import com.poz.CustomerService.domain.Agents;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Repository
public interface AgentsRepository extends JpaRepository<Agents,String> {

    List<Agents> findAllByName(String name);

    List<Agents> findAllByNameAndStatus(String name,String status);

    @Query("SELECT a FROM Agents a WHERE a.status =?1 ")
    List<Agents> findName(String status);

    @Modifying
    @Transactional
    @Query("UPDATE Agents a SET a.name = ?1 where a.agentId =?2")
    int updateName(String name, String agentId);
}
