# 電話客服工單系統 — 資料庫 Schema

依據 `index.html` 原型整理出的資料表設計。核心表有 3 張：客服人員、工單、工單處理記錄。

> **這份文件是「設計說明」，不是實際執行的 DDL。**
>
> 底下的 SQL 是用 MySQL 語法寫的，方便閱讀與討論欄位設計。
> **實際建表的是 Flyway migration**，用的是 SQL Server 語法：
>
> - `src/main/resources/db/migration/V1__init_schema.sql` — 建表、索引、約束
> - `src/main/resources/db/migration/V2__seed_agents.sql` — 開發用客服帳號
> - `src/main/resources/db/migration/V3__refine_indexes_and_agent_updated_at.sql` — 補 `agents.updated_at`、重整狀態查詢索引
>
> 兩者的差異對照（`AUTO_INCREMENT` → `IDENTITY`、`VARCHAR` → `NVARCHAR` 等）
> 寫在 V1 檔案開頭的註解裡。
>
> **要改結構時，請改 migration，不要只改這份文件**，然後回來同步更新說明。
> 已經執行過的 migration 不可修改（Flyway 會比對 checksum），要新增 `V3__xxx.sql`。

## 一、核心資料表

### 1. 客服人員 agents

```sql
CREATE TABLE agents (
  agent_id      VARCHAR(10)  PRIMARY KEY,   -- 客服代號，例如 CSC00001
  name          VARCHAR(50)  NOT NULL,      -- 客服姓名
  password_hash VARCHAR(255) NOT NULL,      -- 登入密碼雜湊值（BCrypt，strength 10）
  status        VARCHAR(20)  NOT NULL DEFAULT 'ONLINE',  -- 目前工作狀態，見下表
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 帳號建立時間
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 最後更新時間，由 JPA @PreUpdate 維護（V3 新增）
  CONSTRAINT CK_agents_status CHECK (
    status IN ('ONLINE','ON_CALL','BREAK','RESTROOM','LUNCH','MEETING')
  )
);
```

`agent_id` 是**業務主鍵**（由人決定的客服代號），不是資料庫自增流水號，
所以 entity 端不可加 `@GeneratedValue`。理由寫在 `Agents.java` 的註解裡。

#### status 可用值

資料庫端由 `CK_agents_status` 把關，填表外的值會被擋下來。

| 值 | 顯示名稱 | 可接聽 | 誰能設定 |
|---|---|---|---|
| `ONLINE` | 線上 | ✔ | 客服手動 |
| `ON_CALL` | 通話中 | ✘ | **系統自動**（接聽時設定，通話結束時還原為 `ONLINE`） |
| `BREAK` | 休息 | ✘ | 客服手動 |
| `RESTROOM` | 廁所 | ✘ | 客服手動 |
| `LUNCH` | 午休 | ✘ | 客服手動 |
| `MEETING` | 簡報 | ✘ | 客服手動 |

- 登入後預設為 `ONLINE`。
- `ON_CALL` 不允許由客服手動選擇，只能由通話事件觸發；同理，狀態為 `ON_CALL` 時也不接受手動變更，必須等通話結束。
- 這裡只存「目前狀態」＋`updated_at`（最後一次變更的時間點）。
  能答出「這個人掛在午休狀態多久了」，但**答不出「他今天午休累計幾分鐘」**——
  要做工時統計得另開一張 `agent_status_logs` 一列一列記錄每次變更，本表無法回推歷史。

#### 種子資料

`V2__seed_agents.sql` 會建三個開發用帳號（`CSC00001` 林曉明 / `CSC00002` 陳美芳 /
`CSC00003` 黃志豪），**密碼都是 `pass1234`**。
`tickets.assignee_id` 有外鍵指向 `agents`，這張表是空的就一張工單都建不出來，
所以這批種子資料是跑起來的前提，不是可有可無的裝飾。正式環境請勿沿用。

### 2. 工單 tickets

```sql
CREATE TABLE tickets (
  ticket_id      INT AUTO_INCREMENT PRIMARY KEY,          -- 工單流水號（內部主鍵，不對外）
  ticket_no      VARCHAR(10)  NOT NULL UNIQUE,            -- 對外顯示的工單編號，格式 TK-XXXXXX
  customer_name  VARCHAR(50),             -- 通話中向客戶確認的姓名，未提供可為 NULL
  contact_phone  VARCHAR(30),             -- 這通電話實際進線／客戶提供的號碼，用來查歷史紀錄，不代表已核實的客戶身分
  title          VARCHAR(200) NOT NULL,   -- 工單主旨
  description    TEXT,                    -- 問題描述內容
  status         VARCHAR(20)  NOT NULL,   -- 處理狀態，見下方對照表
  category       VARCHAR(30)  NOT NULL,   -- 問題分類，例如 帳號問題 / 付款、發票 / ...
  channel        VARCHAR(10)  NOT NULL,   -- 進線管道：PHONE 電話 / EMAIL 信件
  assignee_id    VARCHAR(10)  NOT NULL,   -- 負責處理的客服代號
  follow_up_at   DATETIME,                -- 排定的跟進／回電時間，行事曆用
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 建立時間
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 最後更新時間，由 JPA @PreUpdate 維護
  FOREIGN KEY (assignee_id) REFERENCES agents(agent_id),
  CONSTRAINT CK_tickets_status  CHECK (status  IN ('IN_PROGRESS','PENDING','RESOLVED')),
  CONSTRAINT CK_tickets_channel CHECK (channel IN ('PHONE','EMAIL'))
);
```

#### status 可用值

| 值 | 顯示名稱 | 允許轉換為 |
|---|---|---|
| `IN_PROGRESS` | 處理中 | `PENDING`、`RESOLVED` |
| `PENDING` | 待客戶回覆 | `IN_PROGRESS`、`RESOLVED` |
| `RESOLVED` | 已解決 | `IN_PROGRESS` |

資料庫的 `CK_tickets_status` 只檢查「值合不合法」，**檢查不了「轉換合不合法」**
（CHECK 看不到舊值）。狀態機必須由後端 Service 把關，見 `docs/api.md`。

#### category（問題分類）沒有約束

`category` 是自由字串，**沒有 CHECK、也沒有分類參照表**——這點跟 `status` / `channel`
不一樣，後兩者都有 CHECK 把關。

`index.html` 原型的分類固定是這 5 種：帳號問題 / 付款、發票 / 課程內容 / 退款 / 其他，
但資料庫不擋，塞任何 30 字內的字串都會成功。
現階段刻意保持彈性（分類還可能調整），代價是**得由後端自己驗證**，
否則會出現「帳號問題」「帳號的問題」「登入問題」這種同義但不同字串的髒資料，
之後想做分類統計會很痛。

#### channel 可用值

`PHONE`（電話進線）/ `EMAIL`（信件進線），由 `CK_tickets_channel` 把關。

> 目前 `index.html` 原型沒有任何入口能建立 `EMAIL` 工單，只有假資料裡出現過。
> 「新增派件」建出來的也是 `PHONE`。是否真的有信件進線來源，待確認。

#### updated_at 為什麼不是資料庫自動維護

MySQL 可以寫 `ON UPDATE CURRENT_TIMESTAMP` 讓資料庫自己更新，
**但 SQL Server 沒有這個語法**，所以實際上是由 `Tickets.java` 的 `@PreUpdate` 負責。

`created_at` / `updated_at` 雖然有 `DEFAULT`，但那個預設值**實際上不會生效**：
DEFAULT 只在 INSERT 完全沒提到該欄位時才套用，而 Hibernate 產生的 INSERT
會列出所有映射欄位（等於明確送一個 NULL 進去），DEFAULT 被跳過，直接撞上 NOT NULL。
所以這兩欄改由 `@PrePersist` 補值。

`status` 則是**資料庫端根本沒有預設值**（只有 `agents.status` 有 `DEFAULT 'ONLINE'`），
新建工單一律是 `IN_PROGRESS`，這條規則只存在於 `Tickets.java` 的 `@PrePersist`。

### 3. 工單處理記錄 / 留言 ticket_comments

```sql
CREATE TABLE ticket_comments (
  comment_id   INT AUTO_INCREMENT PRIMARY KEY,   -- 留言／紀錄流水號
  ticket_id    INT          NOT NULL,            -- 所屬工單
  agent_id     VARCHAR(10),                      -- 留言的客服代號，系統事件為 NULL
  content      TEXT         NOT NULL,            -- 留言內容或系統事件描述
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 建立時間
  FOREIGN KEY (ticket_id) REFERENCES tickets(ticket_id) ON DELETE CASCADE,
  FOREIGN KEY (agent_id)  REFERENCES agents(agent_id)
);
```

兩個外鍵刻意採不同策略：

| 外鍵 | 刪除行為 | 原因 |
|---|---|---|
| `ticket_id` → `tickets` | `ON DELETE CASCADE` | 工單刪掉時，底下的處理記錄一起刪 |
| `agent_id` → `agents` | 無（預設 NO ACTION） | 留言要留著當稽核紀錄，所以客服不可隨意刪除 |

`agent_id` 可為 NULL，代表「系統事件」（建單、狀態變更、轉派等由後端自動寫入的紀錄），
畫面上顯示為「系統」。

## 二、索引

實際建立的索引在 `V1__init_schema.sql` 最後幾段，每一條都對應一個具體畫面：

| 索引 | 欄位 | 對應用途 |
|---|---|---|
| `IX_tickets_assignee_created` | `assignee_id`, `created_at DESC` | 首頁列表 tab=ALL：「我的工單、建立時間新到舊」 |
| `IX_tickets_assignee_status_created` | `assignee_id`, `status`, `created_at DESC` | 首頁列表帶狀態 tab 時的主要查詢；前兩欄同時供 `tabCounts` 的 `GROUP BY status` 使用（V3 新增） |
| `IX_tickets_contact_phone` | `contact_phone` | 通話工作台依進線號碼查歷史紀錄（等值比對） |
| `IX_tickets_follow_up` | `assignee_id`, `follow_up_at` | 行事曆查某人某個月的跟進安排 |
| `IX_ticket_comments_ticket` | `ticket_id`, `created_at ASC` | 工單詳情頁撈整串 timeline，依時間排序 |

`IX_tickets_assignee_status_created` 的欄位順序是有講究的：`assignee_id` 等值比對、
選擇性最高，放第一刀砍掉最多資料；`status` 同為等值比對，對應四個 tab；
`created_at` 只用來排序放最後，寫 `DESC` 是為了讓 SQL Server 照索引順序直接讀出來，
省掉一次 Sort。

> V3 一併移除了原本的 `IX_tickets_status`（單欄 `status`）。
> 理由是 status 只有三種值、選擇性太低，最佳化工具多半寧可掃全表也不走它，
> 卻仍要在每次寫入時付出維護成本；而實際畫面上的列表一定會帶 `assignee_id`，
> 那個情境已由上面的複合索引涵蓋。之後若真的出現跨客服的全域狀態查詢
> （例如主管看板），再開 V4 加回來即可。

`IX_tickets_contact_phone` 只在**等值或前綴**比對時有效。
通話工作台拿進線號碼查歷史紀錄屬於等值比對，吃得到；
但列表篩選欄的電話「模糊查」若實作成 `LIKE '%0912%'`，前面帶萬用字元
就無法利用索引的排序結構做二分搜尋，這條索引會派不上用場。
那是模糊查本身的限制，不是索引沒用。`ticket_no` / `customer_name` 的模糊查同理。

`IX_tickets_follow_up` 刻意**不**用篩選索引（`WHERE follow_up_at IS NOT NULL`）。
篩選索引雖然省空間，但 SQL Server 規定：資料表只要有篩選索引，
所有 INSERT/UPDATE/DELETE 都必須在 `QUOTED_IDENTIFIER ON` 的連線下執行。
JDBC 驅動預設是 ON 沒問題，但 sqlcmd 預設是 OFF，
用命令列或 SSMS 手動改資料時會噴一個很難懂的錯誤，不值得。

## 三、目前沒有的東西

以下是原型畫面上有、但這份 schema 刻意沒做的，若之後要補要開 `V3__xxx.sql`：

| 項目 | 說明 |
|---|---|
| `tickets.priority` | 原型有「高／中／低」優先級，本表沒有這個欄位 |
| `customers` 表 | 客戶資訊直接存在 `tickets.customer_name` / `contact_phone`，沒有獨立客戶表，也沒有公司／Email／客戶等級 |
| `tickets.due_at` | 原型的假資料有塞，但畫面上沒有任何地方用到 |
| `agent_status_logs` | 客服狀態只存目前值，不留歷史，無法做工時統計 |

詳細的取捨理由見 `docs/api.md` 第九節「待確認事項」。
