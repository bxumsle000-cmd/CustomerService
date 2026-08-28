# 客服工單系統 — 流程說明

整理日期：2026-08-28

依據兩份東西寫成：
- 前端原型 `src/main/resources/static/index.html`（純前端 demo，資料全在瀏覽器記憶體）
- 目前後端已寫好的程式碼（`controller` / `service` / `dto` / `entity` / `repository` + Flyway V1～V5）

規格來源是 `docs/api.md`（該檔目前在工作區被刪掉了，內容仍留在 git HEAD，可用 `git show HEAD:docs/api.md` 取回）。

每個流程都分三段寫：**畫面上發生什麼** → **該對應哪支 API** → **後端現在做到哪**。

標記說明：
- ✅ 後端已完成，可直接呼叫
- 🔨 有一部分（DTO 或 entity 寫好了，但 Service / Controller 還沒有）
- ⬜ 完全還沒做

---

## 0. 現況總覽

### 後端已完成
- 資料表：`agents` / `tickets` / `ticket_comments`，Flyway V1～V5 都跑得起來
- entity 三支（`Agents`、`Tickets`、`TicketComments`），時間欄位由 `@PrePersist` / `@PreUpdate` 維護
- repository 三支，目前都只繼承 `JpaRepository`，沒有自訂查詢方法
- 錯誤處理：`ApiException` + `GlobalExceptionHandler`，統一回 `{ code, message }`
- 密碼：`PasswordConfig` 提供 BCrypt 的 `PasswordEncoder`
- 客服相關四支 API 全部可用（登入、查自己、客服清單、改工作狀態）

### 後端還沒做
- `TicketService`、`TicketController` —— 工單的所有 API 一支都還沒有
- 工單相關 DTO 只寫了兩個：`CreateTicketRequest`、`TicketListItemResponse`；
  詳情頁要的 `TicketDetailResponse`、`TicketCommentResponse` 還沒有
  （`TicketCommentResponse` 曾經存在，目前已從工作區刪除）
- JWT：`AgentService.currentAgentId()` 寫死回 `CSC00001`，登入回的 token 是
  `DEV-TOKEN-NOT-A-REAL-JWT` 這個假字串
- 登出 `POST /api/auth/logout`

### 前端現況
`index.html` 完全沒有呼叫任何 API，`tickets` / `customers` 都是 JS 陣列，重整就恢復原狀。
也就是說：**現在前後端是兩條平行線，還沒有任何一條接起來。**

另外有兩個前端獨有、後端沒有對應的概念：
- `customers`（客戶主檔）：資料庫裡沒有這張表，工單只存 `customer_name` / `contact_phone`
- `pri`（優先級 HIGH / MEDIUM / LOW）：`tickets` 表沒有這個欄位

這兩個之後要嘛砍掉、要嘛補資料表，先知道有這個落差就好。

---

## 1. 登入流程

### 畫面
1. 開啟頁面 → 顯示登入卡片（`#login`）
2. 按「登入」→ `doLogin()`（index.html:435）
3. 隱藏登入卡、顯示主畫面、把 `state.agentStatus` 設成 `ONLINE`、跳到首頁

### 對應 API
- `POST /api/auth/login` —— 送 `{ agentId, password }`，回 `{ token, agent }`
- `GET /api/auth/me` —— 進主畫面後補撈自己的資料，畫側邊欄與右上角狀態

### 後端狀態
- ✅ `POST /api/auth/login`（`AuthController.login` → `AgentService.login`）
  - BCrypt 比對 `password_hash`
  - 成功後把 `status` 重設為 `ONLINE`（不必呼叫 `save()`，`@Transactional` 結束時 Hibernate 自動 UPDATE）
  - 帳號不存在與密碼錯誤回**完全相同**的 401 / `INVALID_CREDENTIALS`
- ✅ `GET /api/auth/me`（`AuthController.me`）
- ⬜ `POST /api/auth/logout`
- ⬜ JWT：現在 token 是假的，「我是誰」由寫死的 `currentAgentId()` 決定

### 接線時要注意
前端目前是「按下去就進去」，接上 API 之後：
- 密碼欄的預設值要清掉
- 401 要顯示錯誤訊息，不能直接進主畫面
- token 要存起來（localStorage 或記憶體），之後每支請求帶 `Authorization: Bearer <token>`

開發帳號（V2 種子資料）：`CSC00001` 林曉明、`CSC00002` 陳美芳、`CSC00003` 黃志豪，密碼都是 `pass1234`。

---

## 2. 客服工作狀態

### 畫面
右上角頭像的下拉選單（`statusWidget()`，index.html:379）。
可選 ONLINE / BREAK / RESTROOM / LUNCH / MEETING 五個，
`ON_CALL`（通話中）**不在選單裡**，只由接聽／掛斷事件自動切換。

### 對應 API
- `PATCH /api/agents/me/status` —— 送 `{ status }`

### 後端狀態
- ✅ 已完成（`AgentController.updateMyStatus` → `AgentService.updateMyStatus`）
- 兩道防線：
  - `UpdateAgentStatusRequest` 的 `@Pattern` 擋「值本身不合法」（含送 `ON_CALL`）
  - Service 再擋一次白名單，並額外檢查「目前已經是 `ON_CALL` 就不准改」
    —— 這一關 DTO 做不到，因為它看不到資料庫現況
- 路徑寫死 `me`、不吃 `agentId`：能指定 `agentId` 就等於開放改別人的狀態

### 落差
`ON_CALL` 的進出目前只發生在前端記憶體（`setAgentStatusAuto()`），
因為「模擬來電」是純前端的。等真的串電話系統（CTI）才需要一支給系統呼叫的端點。

---

## 3. 首頁（派件列表）

### 畫面
`renderTickets()`（index.html:641）。組成：
- 四個狀態 tab：全部 / 已處理 / 處理中 / 等待客戶回復，每個後面帶件數
- 篩選列：單號、姓名、電話、狀態、客服、時間（全部 / 今天 / 近 7 天 / 近 30 天）
- 表格七欄：單號、姓名、電話、狀態、客服、建立時間、更新時間
- 分頁：每頁 10 / 20 / 50
- 排序：`updatedAt` 由新到舊
- 右上角按鈕：「＋ 新增派件」、「📞 接聽電話」、「📅 行事曆」

### 對應 API
- `GET /api/tickets?status=&ticketNo=&customerName=&phone=&assigneeId=&timeRange=&page=&size=`
- 回應要含 `content` / `page` / `size` / `totalElements` / `totalPages` / `tabCounts`
  （`tabCounts` 一起回，前端才不用為了四個數字多打四次）

### 後端狀態
- 🔨 `TicketListItemResponse` 已寫好（七個欄位剛好對上表格七欄，全部來自 `tickets` 單表，不用 join）
- ⬜ `TicketsRepository` 還沒有查詢方法 —— 這種多條件動態篩選要用
  `JpaSpecificationExecutor` 或自己寫 `@Query`
- ⬜ `TicketService.list()`、`TicketController` 都還沒有
- ⬜ 分頁包裝的 DTO（`content` + `tabCounts` 那一層）還沒有

### 資料庫已經準備好的部分
V3 建的 `IX_tickets_assignee_status_created (assignee_id, status, created_at DESC)`
就是為這支查詢設計的；`tabCounts` 的 `WHERE assignee_id = ? GROUP BY status`
也吃得到這條索引的前兩欄。

### 注意
前端的時間篩選用 `createdAt`、排序用 `updatedAt`，兩個不是同一欄，後端別寫混了。

---

## 4. 通話工作台（建單主流程）

這是整個系統最核心的一條路。

### 畫面
1. 按「📞 接聽電話」→ `simulateCall()`（index.html:569）
   - 隨機挑一個號碼、開始計時、頂端出現通話列
   - 自動把客服狀態切成 `ON_CALL`
   - 跳到工作台頁
2. `renderConsole()`（index.html:465）左右兩欄：
   - 左欄「本次通話工單」表單：主旨（必填）、姓名（稱謂下拉＋名字）、聯絡電話（可按「↙ 帶入進線號碼」）、分類、轉派勾選＋客服代號、通話摘要、通話結果三選一
   - 右欄：進線號碼 ＋ 該號碼的歷史工單（一頁 4 筆，可分頁）
3. 中途切到別頁 → `snapshotCall()` 把表單內容存進 `state.callDraft`，回來不會白填
4. 客戶先掛斷 → `endCall()`：只停計時、狀態回 `ONLINE`，**表單完全不動**，讓客服做完後處理再建單
5. 按「✓ 建立工單並結束通話」→ `createFromCall()`（index.html:537）
   - 主旨空白就擋下來、把輸入框變紅
   - 姓名沒填時送「（未提供）」
   - 建立工單、寫三～四筆處理記錄、收掉通話、跳到該工單詳情

### 建單時要寫進去的處理記錄（順序照原型）
1. 系統：「工單經電話進線建立」
2. 若有通話摘要 → 該客服的一筆留言，內容就是摘要
3. 系統：「狀態設定為「◯◯」」
4. 若有轉派 → 系統：「由 CSC00001 轉派給 CSC00002」

### 對應 API
- `POST /api/tickets` —— 建單，**「通話中建單」與「新增派件」共用這一支**
- `GET /api/tickets?phone={進線號碼}&page=1&size=4` —— 右欄歷史紀錄，沿用列表那支

### 後端狀態
- 🔨 `CreateTicketRequest` 已寫好，八個欄位：
  `title`、`customerName`、`contactPhone`、`category`、`assigneeId`、`description`、`status`、`channel`
  - `title` 有 `@Size(max = 50)`，跟 V5 把 `tickets.title` 縮到 `NVARCHAR(50)` 是配套的，兩邊數字必須一致
  - `status` 白名單 `IN_PROGRESS|PENDING|RESOLVED`
  - `channel` 白名單 `PHONE|Agent`（V4 把原本的 `PHONE|EMAIL` 改掉了）
  - 沒有 `ticketNo`、沒有 `ticketId`、沒有「誰建立的」—— 這三個都不接受前端指定
- ⬜ `TicketService.create()`、`TicketController` 都還沒有
- ⬜ `ticketNo`（`TK-XXXXXX`）的產生規則還沒決定，也還沒實作
- ⬜ 建單同時寫系統留言的那段邏輯還沒有（要跟建單放在同一個交易裡）

### 目前 DTO 與前端對不上的地方（接線前要先決定）
- `CreateTicketRequest` 把 `customerName` / `contactPhone` / `assigneeId` / `description` 都設成 `@NotBlank`，
  但 `docs/api.md` 和前端原型都當它們是**選填**（沒轉派時根本不送 `assigneeId`）。
  兩邊要挑一邊改：不是放寬 DTO，就是前端一定要填。
- `channel` 白名單是 `PHONE / Agent`，但原型的「新增派件」送的也是 `PHONE`（index.html:917），
  所以光看 `channel` 分不出是哪個入口，兩邊的系統留言文案卻不一樣。前端要改送 `Agent`。
- 姓名沒填時前端送「（未提供）」這個字串。要原封不動存進去，還是視為 `null`？
  （之後用姓名查工單時，庫裡一堆「（未提供）」會很難處理）

---

## 5. 新增派件

### 畫面
`renderNewTicket()`（index.html:883）→ 按「建立工單」→ `saveNew()`（index.html:914）。
欄位跟通話工作台幾乎一樣，差別：
- 沒有「轉派給其他客服」那一區
- 沒有進線通話時，「帶入進線號碼」按鈕是灰的
- 狀態預設「處理中」（通話工作台預設「已解決」）
- 系統留言文案是「工單以新增派件建立」

### 對應 API
同上，`POST /api/tickets`，差別只在 `channel` 要送 `Agent`。

### 後端狀態
⬜ 同第 4 節，尚未實作。

---

## 6. 工單詳情

### 畫面
`renderTicket()`（index.html:707）左右兩欄：
- 左欄：主旨、描述、狀態轉換按鈕、轉派區、處理記錄 timeline（新的在上）、新增留言輸入框
- 右欄：狀態、優先級、客服、分類、進線管道、客戶、聯絡電話、建立時間

三個操作：
- `changeStatus()` —— 依 `TRANSITIONS` 狀態機決定按鈕，切換後補一筆系統留言
- `reassign()` —— 手打客服代號、按「轉派」，補一筆系統留言
- `addComment()` —— 送出留言（Enter 也可以）

狀態機規則：
- `IN_PROGRESS` → `PENDING`、`RESOLVED`
- `PENDING` → `IN_PROGRESS`、`RESOLVED`
- `RESOLVED` → `IN_PROGRESS`

### 對應 API
- `GET /api/tickets/{ticketNo}` —— 完整內容 ＋ `comments` timeline ＋ `allowedTransitions`
- `PATCH /api/tickets/{ticketNo}/status`
- `PATCH /api/tickets/{ticketNo}/assignee`
- `POST /api/tickets/{ticketNo}/comments`

### 後端狀態
⬜ 四支全部還沒做，`TicketDetailResponse` 與 `TicketCommentResponse` 也還沒有。

### 實作時的重點
- **狀態機一定要在後端把關**。前端那個「非法的狀態轉換」提示只是第一道防線，
  擋不住直接打 API 的人，非法轉換要回 400
- `allowedTransitions` 由後端算好回傳，前端就不用自己維護一份 `TRANSITIONS`
- 轉派的代號不存在要回 404 —— 原型是讓人**手打**代號的，打錯會直接撞上 `FK_tickets_agents`，
  不先擋就會變成 500
- 留言的 `agent_id` 取自 token，不由前端指定；`agent_id` 是 NULL 代表系統事件，
  顯示用的 `agentName` 回「系統」（資料庫沒有這一欄，是後端算出來的）
- 系統事件留言一律由後端在對應操作裡自動寫入，前端不該送這種紀錄
- `TicketCommentResponse` 的 `agentName` 要另外查 `agents`，
  所以它不能像 `TicketListItemResponse.from()` 那樣只靠單一 entity 完成轉換

---

## 7. 行事曆

### 畫面
`renderCalendar()`（index.html:815）：左邊月曆格子顯示當日件數，右邊清單顯示明細，
下方可挑一張案件 ＋ 選時間加入。規則是「一張案件只排一天，重排即覆蓋」，
下拉只列出自己「處理中 / 等待客戶回覆」的案件。

### 對應 API
- `GET /api/tickets/follow-ups?year=&month=` —— 當月安排
- `GET /api/tickets/followable` —— 可排入的案件
- `PATCH /api/tickets/{ticketNo}/follow-up` —— 設定；送 `{ "followUpAt": null }` 代表移除（對應 ✕）

### 後端狀態
- ⬜ 三支都還沒做
- `tickets.follow_up_at` 欄位已存在，V1 也建了 `IX_tickets_follow_up (assignee_id, follow_up_at)`
- 因為「重排即覆蓋」，直接覆寫 `follow_up_at` 就好，**不需要另開行事曆資料表**

---

## 8. 建議的實作順序

先把主流程打通（登入 → 看列表 → 開工單 → 處理 → 建單）：

1. ✅ `POST /api/auth/login`（已完成）
2. ✅ `GET /api/auth/me`（已完成）
3. ⬜ `GET /api/tickets`（含分頁篩選 ＋ `tabCounts`）
4. ⬜ `GET /api/tickets/{ticketNo}`
5. ⬜ `POST /api/tickets`
6. ⬜ `PATCH /api/tickets/{ticketNo}/status`
7. ⬜ `POST /api/tickets/{ticketNo}/comments`
8. ⬜ `PATCH /api/tickets/{ticketNo}/assignee`

第二批：行事曆三支、`GET /api/tickets/stats`。
（`PATCH /api/agents/me/status` 和 `GET /api/agents` 已經先做完了。）

第三批：JWT 換掉寫死的 `currentAgentId()`、`POST /api/auth/logout`、前端整份改成打 API。

### 下一步具體要動的檔案
1. 先把 `CreateTicketRequest` 的必填／選填跟 `docs/api.md` 對齊（見第 4 節）
2. 補 `TicketDetailResponse`、`TicketCommentResponse` 兩個 DTO
3. `TicketsRepository` 加動態查詢（`JpaSpecificationExecutor` 或 `@Query`）
4. 寫 `TicketService`：`list` / `findByTicketNo` / `create` / `changeStatus` / `reassign` / `addComment`
   —— 身分一律走 `currentAgentId()`，方法簽章不要吃 `agentId`（比照 `AgentService` 的寫法）
5. 寫 `TicketController`，記得參數加 `@Valid`，不寫 try-catch（交給 `GlobalExceptionHandler`）
