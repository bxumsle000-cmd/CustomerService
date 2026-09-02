package com.poz.CustomerService.dto.calendar;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 *
 * @param followUpAt {@code LocalDateTime}——排定的回電時間，<b>必填</b>。
 *                   秒以下的位數會被截掉，因為 {@code follow_ups.follow_up_at} 是 DATETIME2(0)。
 *                   不截的話「這個時間排過了嗎」會拿帶奈秒的值去比而查不到，
 *                   放行之後才撞上資料庫的唯一約束，使用者拿到的是 500 不是 409
 * @param note       {@code String}——個人備註，<b>選填</b>。
 *                   前端清空備註時送的是空字串而不是 null，兩種都會被正規化成 null，
 *                   之後查出來才不必分辨空字串和 null 哪個代表沒寫。
 *                   長度上限 200 要跟 {@code follow_ups.note} 的 NVARCHAR(200) 一致
 */
public record FollowUpRequest(

        @NotNull(message = "回電時間不可為空")
        LocalDateTime followUpAt,

        @Size(max = 200, message = "備註不可超過 200 個字")
        String note
) {

    public FollowUpRequest {
        // 先轉換再驗證：@Size 檢查的會是 trim 過的長度，
        // 「200 個字後面多打了幾個空白」不會被當成超長。
        followUpAt = (followUpAt == null) ? null : followUpAt.withNano(0);
        note = (note == null || note.isBlank()) ? null : note.trim();
    }
}
