package com.poz.CustomerService.dto.agent;

import com.poz.CustomerService.entity.Agents;

/**
 * 客服資訊，對外可見的部分，不含 {@code passwordHash}。
 *
 * @param agentId 客服代號，最長 10 字。例：CSC00001
 * @param name    客服姓名，最長 50 字。例：林曉明
 * @param status  工作狀態，六種之一：
 *                ONLINE / ON_CALL / BREAK / RESTROOM / LUNCH / MEETING
 */
public record AgentResponse(
        String agentId,
        String name,
        String status
) {
    /**
     * 從 entity 轉成 DTO。
     *
     * @param agent 來源 entity，不可為 null
     * @return 只含 agentId / name / status，passwordHash 不會帶出去
     */
    public static AgentResponse from(Agents agent) {
        return new AgentResponse(
                agent.getAgentId(),
                agent.getName(),
                agent.getStatus()
        );
    }
}
