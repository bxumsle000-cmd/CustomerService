package com.poz.CustomerService.security;

import org.springframework.stereotype.Component;

/**
 * 回答「目前登入的客服是誰」，全專案只有這裡知道答案。
 * 之後接 JWT 只要改 {@link #currentAgentId()} 一支。
 */
@Component
public class CurrentAgentProvider {

    /** 開發階段寫死的客服代號，對應 V2__seed_agents.sql 建的「林曉明」。 */
    private static final String DEV_CURRENT_AGENT_ID = "CSC00001";

    /**
     * 目前登入的客服代號。
     *
     * @return 客服代號，例如 CSC00001；現階段固定回傳寫死的值，不會是 null
     */
    public String currentAgentId() {
        return DEV_CURRENT_AGENT_ID;
    }
}
