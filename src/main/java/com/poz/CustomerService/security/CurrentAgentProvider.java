package com.poz.CustomerService.security;

import org.springframework.stereotype.Component;

/**
 * 回答「目前登入的客服是誰」這一個問題，全專案只有這裡知道答案。
 *
 * <h2>為什麼要抽成一支獨立的元件</h2>
 * 原本這段寫死的邏輯放在 {@code AgentService} 的 private 方法裡。
 * 但工單相關的功能也需要知道「我是誰」——建單要決定預設指派給誰、
 * 留言要掛在誰名下——如果 {@code TicketService} 自己也複製一份寫死的常數，
 * 之後接上 JWT 時就得記得兩個地方一起改，漏掉一邊不會編譯錯誤、
 * 測試多半也會過，等到上線才發現「留言全部掛在 CSC00001 名下」。
 * <p>
 * 抽出來之後，接 JWT 只要改 {@link #currentAgentId()} 這一支的內容，
 * 所有 Service 自動跟著變。
 *
 * <h2>之後怎麼接 JWT</h2>
 * 把方法內容換成：
 * <pre>
 * return SecurityContextHolder.getContext().getAuthentication().getName();
 * </pre>
 * 其他地方一行都不用動。
 *
 * <h2>為什麼是 {@code @Component} 而不是 {@code static} 工具方法</h2>
 * 因為之後接 JWT 時它會需要 Spring Security 的東西，
 * 而且做成 Spring 管理的元件，測試時才能換成假的（例如讓它回傳 CSC00002），
 * {@code static} 方法沒辦法。
 */
@Component
public class CurrentAgentProvider {

    /** 開發階段寫死的客服代號，對應 V2__seed_agents.sql 建的「林曉明」。 */
    private static final String DEV_CURRENT_AGENT_ID = "CSC00001";

    /**
     * 目前登入的客服代號。
     *
     * @return {@code String}——客服代號，例如 CSC00001。
     *         現階段固定回傳寫死的值，不會是 null
     */
    public String currentAgentId() {
        return DEV_CURRENT_AGENT_ID;
    }
}
