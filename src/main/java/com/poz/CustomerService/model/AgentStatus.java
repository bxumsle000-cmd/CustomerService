package com.poz.CustomerService.model;

/**
 * 客服工作狀態。
 *
 * 對應 agents.status 欄位，以及資料庫的 CK_agents_status 白名單。
 * 用 enum 而不是 String 的好處：打錯字編譯階段就會被抓出來，
 * 不會等到跑起來才被資料庫的 CHECK 約束擋下。
 */
public enum AgentStatus {

    ONLINE("線上", true),
    /** 接聽時由系統設定，通話結束還原為 ONLINE；客服不能自己選 */
    ON_CALL("通話中", false),
    BREAK("休息", true),
    RESTROOM("廁所", true),
    LUNCH("午休", true),
    MEETING("簡報", true);

    private final String label;
    private final boolean manuallySelectable;

    AgentStatus(String label, boolean manuallySelectable) {
        this.label = label;
        this.manuallySelectable = manuallySelectable;
    }

    /** 給前端顯示的中文名稱 */
    public String getLabel() {
        return label;
    }

    /** 客服能不能自己在下拉選單裡選這個狀態 */
    public boolean isManuallySelectable() {
        return manuallySelectable;
    }
}
