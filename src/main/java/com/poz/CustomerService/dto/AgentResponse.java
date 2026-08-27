package com.poz.CustomerService.dto;

import com.poz.CustomerService.entity.Agents;

/**
 * 客服資訊，對外可見的部分。
 *
 * 用在四個地方：登入回應、GET /api/auth/me（側邊欄與右上角狀態）、
 * GET /api/agents（轉派下拉選單）、以及留言裡的 agentName 來源。
 *
 * Agents entity 有 6 個欄位，這裡只放 3 個——{@code passwordHash} 絕對不能外洩，
 * {@code createdAt} / {@code updatedAt} 前端用不到。
 *
 * @param agentId {@code String}——客服代號，最長 10 字。例：CSC00001
 * @param name    {@code String}——客服姓名，最長 50 字。例：林曉明
 * @param status  {@code String}——工作狀態，六種：
 *                ONLINE / ON_CALL / BREAK / RESTROOM / LUNCH / MEETING。
 *                比 {@link UpdateAgentStatusRequest} 多一個 ON_CALL：客服設不了，但要顯示得出來
 */
public record AgentResponse(
        String agentId,
        String name,
        String status
) {
    /**
     * 從 entity 轉成 DTO。
     *
     * 轉換集中寫在這裡，而不是散在各個 Service，是為了讓「哪些欄位可以出去」
     * 只有這一個地方說了算。之後 Agents 再多幾個敏感欄位，也不必擔心某支
     * Service 忘記過濾。
     *
     * @param agent {@link Agents}——來源 entity，不可為 null（查不到請在 Service 就丟 404）
     * @return {@link AgentResponse}——只含 agentId / name / status，passwordHash 不會帶出去
     */
    public static AgentResponse from(Agents agent) {
        return new AgentResponse(
                agent.getAgentId(),
                agent.getName(),
                agent.getStatus()
        );
    }
}
