# 資料庫設計

SQL Server 2022，資料庫 `customer_service`。結構一律由 Flyway 管理（`src/main/resources/db/migration`），
以下是 V1〜V5 全部套用後的**最終結構**。ER 圖見同目錄的 `er-diagram.png`。

## 共通原則

- 字串欄位一律 `NVARCHAR`：mssql-jdbc 以 nvarchar 送參數，用 `VARCHAR` 會隱含轉換、索引失效。
- 時間欄位一律 `DATETIME2(0)`，精度到秒。
- `created_at` 由資料庫預設值 `SYSDATETIME()` 填；`updated_at` 由 JPA `@PreUpdate` 維護。
- Hibernate `ddl-auto=none`，結構只能透過新增 migration 檔改，已跑過的檔案不可再動（Flyway 會比對 checksum）。

## agents — 客服人員

- 主鍵
  - `agent_id` NVARCHAR(10)：客服代號，例如 `CSC00001`
- 欄位
  - `name` NVARCHAR(50)：姓名
  - `password_hash` NVARCHAR(255)：BCrypt 雜湊
  - `status` NVARCHAR(20)：目前工作狀態，預設 `ONLINE`
  - `created_at` DATETIME2(0)
  - `updated_at` DATETIME2(0)
- 約束
  - `CK_agents_status`：`status` 只能是 `ONLINE` / `ON_CALL` / `BREAK` / `RESTROOM` / `LUNCH` / `MEETING`
  - `ON_CALL` 由系統在接聽／掛斷時設定，其餘為客服手動選擇
- 備註
  - 只留最後狀態，不留狀態歷史；要做工時統計得另開表
  - V2 種子資料有三位：`CSC00001`、`CSC00002`、`CSC00003`

## tickets — 工單

- 主鍵
  - `ticket_id` INT IDENTITY：內部流水號，不對外
- 欄位
  - `ticket_no` 計算欄位（PERSISTED）：對外的工單編號，`'TK-' + ticket_id 補零到 6 位`，例如 `TK-000001`；超過六位就原樣接上，不截斷
  - `customer_name` NVARCHAR(255)，可 NULL
  - `contact_phone` NVARCHAR(50)，可 NULL
  - `title` NVARCHAR(50)：主旨，長度與 `CreateTicketRequest` 的 `@Size(max = 50)` 一致
  - `description` NVARCHAR(MAX)，可 NULL
  - `status` NVARCHAR(20)：處理狀態
  - `category` NVARCHAR(255)：問題分類
  - `channel` NVARCHAR(10)：派單來源
  - `assignee_id` NVARCHAR(10)：負責客服，外鍵
  - `created_at` DATETIME2(0)
  - `updated_at` DATETIME2(0)
- 約束
  - `UQ_tickets_ticket_no`：`ticket_no` 唯一（由 `ticket_id` 推導本來就不會重複，這是最後一道防線）
  - `FK_tickets_agents`：`assignee_id` → `agents.agent_id`，NO ACTION
  - `CK_tickets_status`：`IN_PROGRESS` / `PENDING` / `RESOLVED`
  - `CK_tickets_channel`：`PHONE`（通話工作台建立）/ `Agent`（「＋ 新增派件」手動建立）
- 索引
  - `IX_tickets_assignee_status_created` (`assignee_id`, `status`, `created_at` DESC)：首頁列表
  - `IX_tickets_contact_phone` (`contact_phone`)：通話工作台依進線號碼查歷史
- 備註
  - `ticket_no` 後端完全不碰，INSERT 後由 Hibernate 讀回
  - 原本的 `follow_up_at` 欄位與 `IX_tickets_follow_up` 已在 V5 刪除，回電安排改存 `follow_ups`
  - 有計算欄位上的索引，所有寫入都必須在 `QUOTED_IDENTIFIER ON` 的連線下執行；JDBC 預設 ON，`sqlcmd` 預設 OFF

## ticket_comments — 處理記錄 / 留言

- 主鍵
  - `comment_id` INT IDENTITY
- 欄位
  - `ticket_id` INT：所屬工單，外鍵
  - `agent_id` NVARCHAR(10)：留言的客服，外鍵，可 NULL；NULL 代表系統事件（建單、改狀態）
  - `content` NVARCHAR(MAX)：留言內容或系統事件描述
  - `created_at` DATETIME2(0)
- 約束
  - `FK_ticket_comments_tickets`：`ticket_id` → `tickets.ticket_id`，**ON DELETE CASCADE**（工單刪掉，記錄一起刪）
  - `FK_ticket_comments_agents`：`agent_id` → `agents.agent_id`，NO ACTION（留言當稽核紀錄，客服不可隨意刪）
- 索引
  - `IX_ticket_comments_ticket` (`ticket_id`, `created_at`)：詳情頁撈整串 timeline

## follow_ups — 回電安排（行事曆）

- 主鍵
  - `follow_up_id` INT IDENTITY：內部流水號
- 欄位
  - `agent_id` NVARCHAR(10)：這筆安排的主人（誰的行事曆），外鍵
  - `ticket_id` INT：要回電的工單，外鍵
  - `follow_up_at` DATETIME2(0)：排定的回電時間
  - `note` NVARCHAR(200)，可 NULL：個人備註，只有主人看得到
- 約束
  - `FK_follow_ups_tickets`：`ticket_id` → `tickets.ticket_id`，**ON DELETE CASCADE**
  - `FK_follow_ups_agents`：`agent_id` → `agents.agent_id`，NO ACTION
  - `UQ_follow_ups_agent_ticket_time` (`agent_id`, `ticket_id`, `follow_up_at`)：同一人、同一單、同一時間只能排一筆；不同時間可以排多筆
- 索引
  - `IX_follow_ups_agent_time` (`agent_id`, `follow_up_at`)：月檢視
- 備註
  - `follow_ups` 上只能有一條 CASCADE 外鍵（來自 `tickets`）；若把 `agents` 那條也改成 CASCADE，
    `agents → tickets → follow_ups` 與 `agents → follow_ups` 會形成多重串接路徑，SQL Server 不允許

## 關聯總覽

- `agents` 1 ─ n `tickets`（`assignee_id`）
- `agents` 1 ─ n `ticket_comments`（`agent_id`，可 NULL）
- `agents` 1 ─ n `follow_ups`（`agent_id`）
- `tickets` 1 ─ n `ticket_comments`（`ticket_id`，CASCADE）
- `tickets` 1 ─ n `follow_ups`（`ticket_id`，CASCADE）

刪除行為整理：

- 刪工單：連帶刪掉它的處理記錄與回電安排
- 刪客服：任何一張表還參照到它就刪不掉（三條外鍵都是 NO ACTION）

## Flyway 版本演進

- `V1__init_schema.sql`：建 `agents` / `tickets` / `ticket_comments`
- `V2__seed_agents.sql`：種子資料，三位客服
- `V3__create_follow_ups.sql`：新增 `follow_ups`，把 `tickets.follow_up_at` 既有資料搬過去；當時唯一鍵是 (`agent_id`, `ticket_id`)
- `V4__follow_ups_allow_multiple_per_ticket.sql`：唯一鍵改為 (`agent_id`, `ticket_id`, `follow_up_at`)，同一張單可排多次回電
- `V5__drop_tickets_follow_up_at.sql`：刪掉 `tickets.follow_up_at` 與 `IX_tickets_follow_up`
