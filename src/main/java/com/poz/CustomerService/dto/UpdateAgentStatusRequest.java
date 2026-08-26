package com.poz.CustomerService.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 變更自己的工作狀態，對應 PATCH /api/agents/me/status。
 *
 * <b>注意 @Pattern 裡面沒有 ON_CALL。</b>
 * 通話中這個狀態只能由通話事件觸發，不接受客服手動設定，
 * 所以在 DTO 這一層就把它排除掉。
 *
 * 但這只擋得住「值本身不合法」。另一條規則——
 * <em>目前狀態已經是 ON_CALL 時不允許變更</em>——必須看資料庫裡的現況才判斷得出來，
 * DTO 看不到那個，所以要由 Service 負責。
 *
 * @param status 要切換到的狀態，必填。只能是
 *               ONLINE / BREAK / RESTROOM / LUNCH / MEETING，送 ON_CALL 會被擋回 400。
 *               不必傳 agentId——「我是誰」由 Service 的 currentAgentId() 決定，
 *               讓呼叫端指定要改誰，等於開一個「任何人都能改別人」的洞
 */
public record UpdateAgentStatusRequest(
        @NotBlank(message = "狀態不可空白")
        @Pattern(
                regexp = "ONLINE|BREAK|RESTROOM|LUNCH|MEETING",
                message = "狀態只能是 ONLINE / BREAK / RESTROOM / LUNCH / MEETING"
        )
        String status
) {
}
