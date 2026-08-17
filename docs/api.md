# 電話客服工單系統 — API 規格

依據 `index.html` 原型與 `sql/schema.md` 整理出的後端 API 清單。

---

## 一、共通約定

### 路徑前綴

一律使用 `/api`。

### 認證方式

登入後由後端發 token（JWT 或 Session 皆可），之後每個請求帶著。**後端從 token 取得「目前登入的客服」**，也就是原型裡寫死的 `ME = 'CSC00001'`；前端不需要（也不應該）在請求裡自己送 `agentId` 來表明身分。

```
Authorization: Bearer <token>
```

### 識別碼

對外一律使用 `ticket_no`（格式 `TK-XXXXXX`），**不要把 `ticket_id` 這個內部流水號露出去**。

### 錯誤回應格式

```json
{
  "code": "INVALID_STATUS_TRANSITION",
  "message": "無法從「已解決」變更為「待客戶回覆」"
}
```

| HTTP 狀態碼 | 使用時機 |
|---|---|
| 400 | 參數格式錯誤、非法的狀態轉換 |
| 401 | 未登入或 token 失效 |
| 403 | 已登入但無權限操作該筆資料 |
| 404 | 工單／客服不存在 |

---

## 二、認證與登入客服

對應畫面：登入頁、側邊欄底部的客服資訊。

### POST /api/auth/login

登入。

Request：

```json
{
  "agentId": "CSC00001",
  "password": "..."
}
```

Response：

```json
{
  "token": "...",
  "agent": {
    "agentId": "CSC00001",
    "name": "林曉明",
    "status": "ONLINE"
  }
}
```

密碼比對 `agents.password_hash`。失敗回 `401`，訊息不要區分「帳號不存在」和「密碼錯誤」。

**登入成功時後端要把 `agents.status` 重設為 `ONLINE`**，這樣才符合前端「登入後預設狀態是線上」的行為；否則上次下班前留下的「午休」會被帶到今天。

### POST /api/auth/logout

登出。

### GET /api/auth/me

取得目前登入客服，供側邊欄與右上角狀態選單顯示。

```json
{
  "agentId": "CSC00001",
  "name": "林曉明",
  "status": "ONLINE"
}
```

### PATCH /api/agents/me/status

變更自己的工作狀態，對應 `agents.status`（前端右上角頭像的下拉選單）。

Request：

```json
{ "status": "LUNCH" }
```

可用值與規則見 `sql/schema.md` 的 status 對照表。後端要擋兩件事，回 `400`：

| 情況 | 原因 |
|---|---|
| 送 `status: "ON_CALL"` | `ON_CALL` 只能由通話事件觸發，不接受手動設定 |
| 目前狀態已是 `ON_CALL` | 通話中不允許變更，必須等通話結束 |

前端已經在 UI 上把這兩件事鎖住了（選單裡沒有「通話中」這個選項，且通話中整份選單變灰），但這只是第一道防線，**擋不住直接打 API 的人**。

### 通話中的狀態切換

`ON_CALL` 的進入與離開由通話事件驅動，不走上面那支 API：

- **接聽時** → `ON_CALL`
- **通話結束時** → 還原為 `ONLINE`

現階段「模擬來電」是純前端的，所以這兩個切換也只發生在前端記憶體裡，**還不需要 API**。等之後真的串接電話系統（CTI）時，才需要一支給系統呼叫的內部端點（例如 `PATCH /api/agents/{agentId}/status`，走另一組機器對機器的認證），由 CTI 事件推動。

> 待決：目前前端的行為是「掛斷電話 → 立刻回到 `ONLINE`」。但原型規定客服在建立工單前不能接下一通電話，所以這段後處理時間顯示「線上」其實不精確。若要精確表達，需要再加一個 `WRAP_UP`（後處理）狀態。**本文件與 schema 目前都採不加的版本。**

---

## 三、工單列表（首頁）

### GET /api/tickets

列表查詢 + 分頁。Query 參數對應原型的 `state.list`：

| 參數 | 對應原型 | 說明 |
|---|---|---|
| `status` | `tab` | `ALL` / `IN_PROGRESS` / `PENDING` / `RESOLVED` |
| `ticketNo` | `fNo` | 單號模糊查 |
| `customerName` | `fName` | 姓名模糊查 |
| `phone` | `fPhone` | 電話模糊查 |
| `assigneeId` | `fAssignee` | 客服代號（前端預設帶自己） |
| `timeRange` | `fTime` | `ALL` / `D1` / `D7` / `D30` |
| `page` | `page` | 頁碼，從 1 開始 |
| `size` | `pageSize` | 每頁筆數：10 / 20 / 50 |

Response：

```json
{
  "content": [
    {
      "ticketNo": "TK-084215",
      "customerName": "王經理",
      "contactPhone": "02-2345-6789",
      "title": "帳號無法登入",
      "status": "IN_PROGRESS",
      "assigneeId": "CSC00001",
      "createdAt": "2026-08-17T09:12:00"
    }
  ],
  "page": 1,
  "size": 10,
  "totalElements": 28,
  "totalPages": 3,
  "tabCounts": { "ALL": 28, "IN_PROGRESS": 12, "PENDING": 6, "RESOLVED": 10 }
}
```

**`tabCounts` 建議一併回傳**，否則前端為了畫四個 tab 上的數字要多打 4 次 API。

`timeRange` 若之後需要更彈性，可改成 `createdFrom` / `createdTo` 兩個日期參數。

### GET /api/tickets/stats

側邊欄「首頁」旁的未結案數字。

```json
{ "openCount": 18 }
```

若 `GET /api/tickets` 已經回傳 `tabCounts`，這支也可以省略，由前端從列表結果取。

---

## 四、工單詳情

### GET /api/tickets/{ticketNo}

取得單張工單完整內容，含處理記錄 timeline。

```json
{
  "ticketNo": "TK-084215",
  "title": "帳號無法登入",
  "description": "客戶重設密碼後仍無法登入，初判帳號遭鎖定。",
  "status": "IN_PROGRESS",
  "category": "帳號問題",
  "channel": "PHONE",
  "customerName": "王經理",
  "contactPhone": "02-2345-6789",
  "assigneeId": "CSC00001",
  "followUpAt": "2026-08-17T15:00:00",
  "createdAt": "2026-08-17T09:12:00",
  "updatedAt": "2026-08-17T10:03:00",
  "allowedTransitions": ["PENDING", "RESOLVED"],
  "comments": [
    {
      "commentId": 1,
      "authorId": null,
      "authorName": "系統",
      "content": "工單經電話進線建立",
      "createdAt": "2026-08-17T09:12:00"
    },
    {
      "commentId": 2,
      "authorId": "CSC00001",
      "authorName": "林曉明",
      "content": "已致電客戶，確認為帳號被鎖定，正在協助解鎖。",
      "createdAt": "2026-08-17T10:03:00"
    }
  ]
}
```

`allowedTransitions` 由後端依狀態機算好回傳，前端就不用自己維護一份 `TRANSITIONS` 常數。

### POST /api/tickets

建立工單。**「新增派件」與「通話中建單」共用這一支**，差別只在 `channel`。

Request：

```json
{
  "title": "帳號無法登入",
  "customerName": "王先生",
  "contactPhone": "02-2345-6789",
  "category": "帳號問題",
  "description": "客戶重設密碼後仍無法登入。",
  "status": "IN_PROGRESS",
  "channel": "PHONE",
  "assigneeId": "CSC00002"
}
```

| 欄位 | 必填 | 說明 |
|---|---|---|
| `title` | ✔ | 主旨 |
| `customerName` | | 通話中確認的姓名，原型是「姓名 + 稱謂」組起來（如「王先生」） |
| `contactPhone` | | 客戶提供的聯絡電話 |
| `category` | ✔ | 問題分類 |
| `description` | | 問題描述 / 通話摘要 |
| `status` | ✔ | 建單時的通話結果：`RESOLVED` / `IN_PROGRESS` / `PENDING` |
| `channel` | ✔ | `PHONE` / `EMAIL` |
| `assigneeId` | | 轉派對象；未給則指派給目前登入的客服 |

**`ticketNo` 由後端產生，不接受前端指定。**

建單時後端要在同一個交易裡寫入系統事件留言：

- 「工單經電話進線建立」或「工單以新增派件建立」
- 若有 `description`，寫成一筆該客服的留言
- 「狀態設定為「處理中」」
- 若 `assigneeId` 不是自己，再加一筆「由 CSC00001 轉派給 CSC00002」

回傳 `201` 與建立好的工單（格式同 `GET /api/tickets/{ticketNo}`）。

### PATCH /api/tickets/{ticketNo}/status

變更狀態。

```json
{ "status": "RESOLVED" }
```

**狀態機必須在後端把關**，非法轉換回 `400`：

| 目前狀態 | 允許變更為 |
|---|---|
| `IN_PROGRESS` | `PENDING`、`RESOLVED` |
| `PENDING` | `IN_PROGRESS`、`RESOLVED` |
| `RESOLVED` | `IN_PROGRESS` |

原型的 `alert('非法的狀態轉換')` 只是第一道防線，擋不住直接打 API 的人。

成功後由後端寫入系統留言：「狀態由「處理中」變更為「已解決」」。

### PATCH /api/tickets/{ticketNo}/assignee

轉派給其他客服。

```json
{ "assigneeId": "CSC00002" }
```

- 代號不存在回 `404`（原型是讓人**手打**代號的，打錯會直接違反 FK 限制）
- 與目前負責人相同則不做事，直接回成功
- 成功後寫入系統留言：「由 CSC00001 轉派給 CSC00002」

### POST /api/tickets/{ticketNo}/comments

新增處理記錄。

```json
{ "content": "已協助客戶完成解鎖。" }
```

`author_id` 取自 token，**不由前端指定**。

> 系統事件留言（狀態變更、轉派、建單）一律由後端在對應操作中自動寫入，前端不應該、也不需要送這種紀錄。

---

## 五、行事曆

原型的規則是「一張工單只排一天，重排即覆蓋」，所以直接覆寫 `tickets.follow_up_at` 即可，**不需要獨立的行事曆資料表**。

### GET /api/tickets/follow-ups?year=2026&month=8

該月的跟進安排，供月曆格子顯示件數、右側清單顯示明細。只回傳**目前登入客服自己的**工單。

```json
{
  "days": [
    {
      "date": "2026-08-17",
      "count": 2,
      "items": [
        {
          "ticketNo": "TK-084215",
          "title": "帳號無法登入",
          "customerName": "王經理",
          "contactPhone": "02-2345-6789",
          "status": "IN_PROGRESS",
          "followUpAt": "2026-08-17T15:00:00"
        }
      ]
    }
  ]
}
```

### GET /api/tickets/followable

可排入行事曆的案件：**自己的**、且狀態為 `IN_PROGRESS` 或 `PENDING`。供右側下拉選單使用。

```json
[
  { "ticketNo": "TK-084215", "title": "帳號無法登入", "status": "IN_PROGRESS" }
]
```

### PATCH /api/tickets/{ticketNo}/follow-up

設定或移除跟進時間。

```json
{ "followUpAt": "2026-08-18T10:30:00" }
```

送 `{ "followUpAt": null }` 代表移除（對應原型日程項目上的 ✕）。

---

## 六、通話工作台

### GET /api/tickets?phone={進線號碼}&page=1&size=4

依進線號碼查歷史紀錄，顯示在右側面板（原型一頁 4 筆）。**沿用工單列表那支 API 即可**，不需要另開。

查無資料時前端顯示「查無此號碼的歷史紀錄（可能是新客戶）」。

### 不需要 API 的部分

- **模擬來電、通話計時器**：純前端 demo。真正串接電話系統（CTI）是另一個題目，現階段不處理。
- **通話中的表單暫存**（原型的 `state.callDraft`）：屬於前端狀態，不用存後端。

---

## 七、下拉選單 / 驗證

### GET /api/agents

客服清單，供轉派時驗證代號或做成下拉選單。

```json
[
  { "agentId": "CSC00001", "name": "林曉明", "status": "ONLINE" },
  { "agentId": "CSC00002", "name": "陳小華", "status": "LUNCH" }
]
```

回傳 `status` 是為了之後轉派時能提示「這位客服目前在午休」。目前前端的轉派是**手打代號**，還沒用到這份清單。

### GET /api/meta/categories

問題分類清單。

```json
["帳號問題", "付款/發票", "課程內容", "退款", "其他"]
```

分類目前在原型裡是前端常數。**如果確定不會異動，前端寫死也可以，這支就省下來。**

---

## 八、實作優先順序

先做這 8 支就能跑通主流程（登入 → 看列表 → 開工單 → 處理 → 建單）：

1. `POST /api/auth/login`
2. `GET /api/auth/me`
3. `GET /api/tickets`（含分頁篩選）
4. `GET /api/tickets/{ticketNo}`
5. `POST /api/tickets`
6. `PATCH /api/tickets/{ticketNo}/status`
7. `POST /api/tickets/{ticketNo}/comments`
8. `PATCH /api/tickets/{ticketNo}/assignee`

第二批：行事曆三支、`PATCH /api/agents/me/status`、`GET /api/agents`、`GET /api/tickets/stats`。

客服狀態選單前端已經做好了（`index.html` 的 `statusWidget()`），目前狀態只存在瀏覽器記憶體裡，重整就沒了。接上 `PATCH /api/agents/me/status` 和 `GET /api/auth/me` 之後才會真正持久化。

---

## 九、待確認事項

以下是 `index.html` 原型與 `sql/schema.md` 對不上的地方，會直接影響 API 的欄位，建議先決定：

### 1. 優先級（priority）

原型的儀表板與工單詳情都顯示「優先級」（`HIGH` / `MEDIUM` / `LOW`），但 `tickets` 表沒有這個欄位。

- 要保留 → schema 需加 `priority VARCHAR(10)`，各工單 API 需加欄位
- 不保留 → 工單詳情的「優先級」那一列要移除

**本文件目前的 API 規格採「不保留」。**

### 2. 客戶（customers）

原型有獨立的 `customers` 陣列（含公司、Email、客戶等級 tier），以及「客戶列表 / 客戶詳情」兩個畫面。但：

- schema 沒有 customers 表，客戶資訊直接存在 `tickets.customer_name` / `contact_phone`
- 側邊欄 nav 只剩「首頁」和「工作台」，客戶頁**已經沒有入口**

推測是刻意簡化掉的，因此本文件**未列出任何客戶相關 API**。若之後要恢復，需補一張 `customers` 表與一組 CRUD。

### 3. dueAt（到期時間）

原型的 `mk()` 函式有塞這個值，但畫面上沒有任何地方使用，schema 也沒有對應欄位。**判定為可直接忽略。**

### 4. 儀表板

`renderDashboard()` 函式還在，但 nav 已經沒有入口進得去。若確定不做，就不需要 `GET /api/dashboard/stats`。

### 5. channel 的 EMAIL

schema 註解寫 `PHONE 電話 / EMAIL 信件 / 客服工單` 三種，但原型沒有任何地方能建立 EMAIL 工單（只出現在假資料裡）。需確認是否真的有信件進線來源，以及「客服工單」是不是指「新增派件」這個入口。
