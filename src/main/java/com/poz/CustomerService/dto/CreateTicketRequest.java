package com.poz.CustomerService.dto;

/*
 * =====================================================================
 * CreateTicketRequest —— 建立工單時，前端送上來的表單內容
 * =====================================================================
 *
 * ### 畫面上的哪裡
 *
 * 兩個入口共用這一支 API，所以也共用這一個 DTO：
 *
 *   入口一　通話工作台（index.html:458 renderConsole）
 *           側邊欄「工作台」→ 模擬來電 → 左欄「本次通話工單」表單
 *           → 按「✓ 建立工單並結束通話」（createFromCall，第 530 行）
 *
 *   入口二　新增派件（index.html:871 renderNewTicket）
 *           首頁右上角「＋ 新增派件」→ 表單 → 按「建立工單」（saveNew，第 902 行）
 *
 * 表單欄位怎麼對到這個 DTO：
 *
 *   主旨 *                        → title
 *   姓名（稱謂下拉 + 名字）        → customerName
 *                                   前端會把「王」+「先生」組成「王先生」才送上來，
 *                                   後端收到的是組好的整串
 *   聯絡電話                       → contactPhone
 *                                   （旁邊那顆「↙ 帶入進線號碼」只是幫使用者填欄位）
 *   分類                          → category
 *   通話摘要 / 描述                → description
 *   通話結果 / 狀態（三選一）       → status
 *                                   已解決 RESOLVED、需再追蹤 IN_PROGRESS、
 *                                   待客戶回覆 PENDING
 *   轉派給其他客服（勾選 + 代號）    → assigneeId
 *                                   只有通話工作台有這一區；沒勾就不送
 *   （前端寫死）                    → channel，通話工作台送 PHONE
 *
 * ---------------------------------------------------------------------
 * 產出契約（規格出自 docs/api.md「POST /api/tickets」那張欄位表）
 * ---------------------------------------------------------------------
 * 八個欄位：title、customerName、contactPhone、category、description、
 *           status、channel、assigneeId
 *
 * 必填：title、category、status、channel
 * 選填：customerName、contactPhone、description、assigneeId
 *
 * ---------------------------------------------------------------------
 * 【重點】不可以有的東西
 * ---------------------------------------------------------------------
 * - ticketNo：api.md 明寫「由後端產生，不接受前端指定」。
 *   （前端原型自己產了一個 TK-0001，那是 demo 用的，而且格式跟
 *     schema 的 TK-XXXXXX 根本對不上，接後端之後那段要刪掉。）
 * - ticketId：內部流水號，由資料庫發。
 * - 「是誰建立的」：那要從 token 取，不能讓前端自己說。
 *   （現在還沒有 JWT，AgentService.currentAgentId() 是寫死的，但規則一樣。）
 *   注意 assigneeId 是「轉派給誰」，跟「誰建的」是兩件事。
 *
 * ---------------------------------------------------------------------
 * 驗證要求
 * ---------------------------------------------------------------------
 * 這個 DTO 要能擋掉三類壞資料，Controller 加 @Valid 之後才會生效：
 *
 *   1. 必填欄位空白
 *   2. 字串超過資料庫欄位長度
 *      各欄位上限自己去 entity/Tickets.java 的 Javadoc 查，每一欄都標了。
 *      為什麼要在這裡擋？不擋的話會撞到資料庫的長度限制，
 *      使用者看到的是 500「系統發生錯誤」而不是「主旨太長」。
 *   3. status 和 channel 不在白名單內
 *      白名單是什麼，去看 V1__init_schema.sql 的 CK_tickets_status
 *      和 CK_tickets_channel。
 *
 * 寫法參考 dto/LoginRequest.java（必填 + 長度）和
 * dto/UpdateAgentStatusRequest.java（白名單）。
 *
 * 順帶一提，category 在資料庫刻意沒有加 CHECK（V3 最後一段有寫原因），
 * 所以分類的合法值後端擋不了——這件事之後在 Service 要處理，先知道就好。
 *
 * ---------------------------------------------------------------------
 * 要自己決定的事
 * ---------------------------------------------------------------------
 * 1. 前端在使用者沒填姓名時，會送字串「（未提供）」上來（第 537、907 行）。
 *    後端要原封不動存進 customer_name，還是視為沒填（null）？
 *    想一下：之後要用姓名查工單時，資料庫裡有一堆「（未提供）」會怎樣？
 *
 * 2. 兩個入口的系統留言文案不一樣：
 *    通話工作台寫「工單經電話進線建立」，新增派件寫「工單以新增派件建立」。
 *    但原型的新增派件送的 channel 也是 PHONE（第 917 行），
 *    所以光看 channel 分不出來是哪個入口。
 *    要多一個欄位告訴後端嗎？還是兩邊文案統一就好？
 *    （這是 api.md 沒講清楚的地方，你的決定就是答案，記得寫成註解。）
 *
 * 3. assigneeId 沒送的時候，工單要指派給誰？api.md 有答案，找出來。
 *
 * ---------------------------------------------------------------------
 * 完成檢查表
 * ---------------------------------------------------------------------
 *   [ ] 八個欄位齊全，名稱與 api.md 一致
 *   [ ] 沒有 ticketNo、沒有 ticketId、沒有「誰建立的」
 *   [ ] 四個必填欄位空白時會被擋下
 *   [ ] 超長字串會被擋下
 *   [ ] status / channel 送白名單以外的值會被擋下
 *   [ ] ./mvnw compile 通過
 * =====================================================================
 */
import com.poz.CustomerService.entity.Tickets;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
    // 主旨對應 tickets.title NVARCHAR(50)（V4 由 200 縮下來），所以上限是 50
    @NotBlank(message = "不可以沒有主旨")
    @Size(max = 50, message = "主旨長度不可超過 50")
    String title,

    // 以下四欄刻意不加 @Size。
    // V4 已經把 customer_name / category 放寬到 NVARCHAR(255)、contact_phone 放寬到 NVARCHAR(50)，
    // 正常輸入撞不到，就不必再讓使用者面對長度錯誤訊息。
    // 唯一的例外是 assigneeId：它對應 agents.agent_id NVARCHAR(10)，
    // 資料庫端維持 10 沒放寬（外鍵限制），但客服代號是 CSC00001 這種固定格式，
    // 由前端下拉選單決定，不是使用者自由打字，所以這裡一樣不驗長度。
    @NotBlank(message = "不可以沒有姓名")
    String customerName,

    @NotBlank(message = "不可以沒有聯絡電話")
    String contactPhone,

    @NotBlank(message = "不可以沒有分類")
    String category,

    String assigneeId,

    // description 對應 NVARCHAR(MAX)，沒有實際上限，不加 @Size
    @NotBlank(message = "不可以沒有通話摘要")
    String description,

    // 白名單同時管住了長度，所以 status / channel 不必再加 @Size
    @NotBlank(message = "不可以沒有通話結果")
    @Pattern(
            regexp = "IN_PROGRESS|PENDING|RESOLVED",
            message = "通話結果只能是 IN_PROGRESS / PENDING / RESOLVED"
    )
    String status,

    // 派單來源：PHONE = 通話工作台在通話中建立、Agent = 客服從「＋ 新增派件」手動建立。
    // 白名單要跟 V4 的 CK_tickets_channel 一致，兩邊有一邊漏改就會變成 500。
    @NotBlank(message = "不可以沒有派單來源")
    @Pattern(
            regexp = "PHONE|Agent",
            message = "派單來源只能是 PHONE / Agent"
    )
    String channel
) {
}
