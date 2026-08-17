package com.poz.CustomerService.model;

/**
 * 工單進線管道。
 *
 * 對應 tickets.channel 欄位，以及資料庫的 CK_tickets_channel 白名單。
 */
public enum TicketChannel {

    PHONE("電話"),
    EMAIL("信件");

    private final String label;

    TicketChannel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
