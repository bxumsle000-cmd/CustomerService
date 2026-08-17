package com.poz.CustomerService.model;

import java.util.Set;

/**
 * 工單處理狀態。
 *
 * 對應 tickets.status 欄位，以及資料庫的 CK_tickets_status 白名單。
 */
public enum TicketStatus {

    IN_PROGRESS("處理中"),
    PENDING("待客戶回覆"),
    RESOLVED("已解決");

    private final String label;

    TicketStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 狀態機：這個狀態可以變更成哪些狀態。
     *
     * 規則跟前端 index.html 的 TRANSITIONS 一致，但前端那份只是第一道防線，
     * 擋不住直接打 API 的人，所以後端必須自己再檢查一次。
     *
     * 注意：這裡不能寫成 enum 的欄位，因為 enum 常數在建構時彼此還沒建立完成，
     * 互相引用會拿到 null。所以改用 switch 在呼叫當下才計算。
     */
    public Set<TicketStatus> allowedTransitions() {
        return switch (this) {
            case IN_PROGRESS -> Set.of(PENDING, RESOLVED);
            case PENDING -> Set.of(IN_PROGRESS, RESOLVED);
            case RESOLVED -> Set.of(IN_PROGRESS);
        };
    }

    /** 能不能從目前狀態變更為 next */
    public boolean canTransitionTo(TicketStatus next) {
        return allowedTransitions().contains(next);
    }
}
