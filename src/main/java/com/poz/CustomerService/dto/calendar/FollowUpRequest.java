package com.poz.CustomerService.dto.calendar;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 新增或修改回電安排時前端送上來的內容。
 *
 * @param followUpAt 排定的回電時間，必填
 * @param note       個人備註，選填，最長 200 字
 */
public record FollowUpRequest(

        @NotNull(message = "回電時間不可為空")
        LocalDateTime followUpAt,

        @Size(max = 200, message = "備註不可超過 200 個字")
        String note
) {
}
