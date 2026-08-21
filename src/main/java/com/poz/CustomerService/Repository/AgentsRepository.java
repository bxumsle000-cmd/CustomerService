package com.poz.CustomerService.Repository;

import com.poz.CustomerService.domain.Agents;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentsRepository extends JpaRepository<Agents,String> {

    List<Agents> findAllByName(String name);

    List<Agents> findAllByNameAndStatus(String name,String status);
}
