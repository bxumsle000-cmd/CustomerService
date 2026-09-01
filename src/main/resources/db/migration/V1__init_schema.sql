-- =====================================================================
-- V1 初始 schema：agents / tickets / ticket_comments
--
-- 對應文件：sql/schema.md（該文件用 MySQL 語法描述，這裡翻譯成 SQL Server）
--
-- 翻譯時的幾個重點：
--   MySQL                          →  SQL Server
--   INT AUTO_INCREMENT             →  INT IDENTITY(1,1)
--   VARCHAR（要存中文）            →  NVARCHAR   ※ 用 VARCHAR 中文會變問號
--   TEXT                           →  NVARCHAR(MAX)   ※ TEXT 已被 SQL Server 淘汰
--   DATETIME                       →  DATETIME2(0)
--   DEFAULT CURRENT_TIMESTAMP      →  DEFAULT SYSDATETIME()
--   ON UPDATE CURRENT_TIMESTAMP    →  （沒有對應語法，改由 JPA @PreUpdate 處理）
--
-- 全部字串欄位一律用 NVARCHAR：mssql-jdbc 預設就是以 NVARCHAR 送出參數，
-- 欄位若宣告成 VARCHAR 會產生隱含轉換，導致索引失效。
--
-- 注意：這支檔案跑過之後就不可再修改，Flyway 會比對 checksum，
--       連改一個空白都會讓下次啟動噴 Migration checksum mismatch。
--       要改結構請新增 V3__xxx.sql（V2 是種子資料）。
-- =====================================================================

SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO

-- ---------------------------------------------------------------------
-- 1. 客服人員
-- ---------------------------------------------------------------------
CREATE TABLE [dbo].[agents](
    [agent_id]      NVARCHAR(10)  NOT NULL,   -- 客服代號，例如 CSC00001
    [name]          NVARCHAR(50)  NOT NULL,   -- 客服姓名
    [password_hash] NVARCHAR(255) NOT NULL,   -- 登入密碼雜湊值（BCrypt）
    [status]        NVARCHAR(20)  NOT NULL,   -- 目前工作狀態，見下方 CHECK
    [created_at]    DATETIME2(0)  NOT NULL,   -- 帳號建立時間
    [updated_at]    DATETIME2(0)  NOT NULL,   -- 最後更新時間，由 JPA @PreUpdate 維護
    CONSTRAINT [PK_agents] PRIMARY KEY CLUSTERED ([agent_id] ASC)
)
GO

ALTER TABLE [dbo].[agents] ADD CONSTRAINT [DF_agents_status]
    DEFAULT (N'ONLINE') FOR [status]
GO
ALTER TABLE [dbo].[agents] ADD CONSTRAINT [DF_agents_created_at]
    DEFAULT (SYSDATETIME()) FOR [created_at]
GO
-- 客服狀態（ONLINE / LUNCH / ...）會一直變，這裡只留「最後更新時間」，不留狀態歷史。
-- 要做工時統計（每個狀態累計多久）得另開 agent_status_logs 一列一列記，那是另一個題目。
ALTER TABLE [dbo].[agents] ADD CONSTRAINT [DF_agents_updated_at]
    DEFAULT (SYSDATETIME()) FOR [updated_at]
GO

-- 狀態白名單。ON_CALL 由系統在通話事件時設定，其餘為客服手動選擇。
ALTER TABLE [dbo].[agents] WITH CHECK ADD CONSTRAINT [CK_agents_status]
    CHECK ([status] IN (N'ONLINE', N'ON_CALL', N'BREAK', N'RESTROOM', N'LUNCH', N'MEETING'))
GO
ALTER TABLE [dbo].[agents] CHECK CONSTRAINT [CK_agents_status]
GO

-- ---------------------------------------------------------------------
-- 2. 工單
-- ---------------------------------------------------------------------
--
-- ticket_no 是「計算欄位」（computed column），不是一般欄位：
-- 值由 ticket_id 推導，PERSISTED 代表算完真的存進磁碟（不是每次查詢才算），
-- 所以可以建索引、查起來也快。
--
-- 這樣寫的用意，是讓「編號重複」在資料庫層變成不可能發生：
-- 後端完全不碰這一欄，自然也沒有「先塞一個暫時的隨機號、拿到 id 之後再改掉」
-- 那種會撞唯一約束的空窗期。
--
-- 運算式有三個細節：
--   1. 字串一律加 N 前綴、用 CONVERT(NVARCHAR(10), ...)，算出來才會是 nvarchar。
--      寫成 'TK-' 會得到 varchar，而 mssql-jdbc 送參數時用的是 nvarchar，
--      型別對不上就會產生隱含轉換、索引失效。
--   2. 補零用 CASE 分兩段，而不是一律 RIGHT(..., 6)：
--      ticket_id 破百萬之後 RIGHT 會從左邊砍掉一位（1000001 → 000001），
--      跟第 1 號工單撞號。分段之後六位以內補零、超過就原樣接上，永遠不會截斷。
--   3. 計算欄位不能自己宣告長度，型別由運算式推導。
--      這順便解掉了原本「TK- 加七位數就塞不進 NVARCHAR(10)」的隱憂。
--
-- 提醒：資料表只要有「建在計算欄位上的索引」（下面的 UQ_tickets_ticket_no 就是），
--       所有 INSERT / UPDATE / DELETE 都必須在 QUOTED_IDENTIFIER ON 的連線下執行。
--       JDBC 驅動預設是 ON 沒問題，但 sqlcmd 預設是 OFF，
--       用命令列手動改資料時會噴一個很難懂的錯誤。
CREATE TABLE [dbo].[tickets](
    [ticket_id]     INT IDENTITY(1,1) NOT NULL,  -- 工單流水號（內部主鍵，不對外）
    [ticket_no]     AS (N'TK-' + CASE
                            WHEN [ticket_id] <= 999999
                            THEN RIGHT(N'000000' + CONVERT(NVARCHAR(10), [ticket_id]), 6)
                            ELSE CONVERT(NVARCHAR(10), [ticket_id])
                        END) PERSISTED NOT NULL,  -- 對外顯示的工單編號，格式 TK-000001
    [customer_name] NVARCHAR(255)     NULL,      -- 通話中向客戶確認的姓名，未提供可為 NULL
    [contact_phone] NVARCHAR(50)      NULL,      -- 客戶提供的聯絡電話，用來查歷史紀錄
    [title]         NVARCHAR(50)  NOT NULL,      -- 工單主旨，長度與 CreateTicketRequest 的 @Size(max = 50) 一致
    [description]   NVARCHAR(MAX)     NULL,      -- 問題描述內容
    [status]        NVARCHAR(20)  NOT NULL,      -- 處理狀態，見下方 CHECK
    [category]      NVARCHAR(255) NOT NULL,      -- 問題分類，例如 帳號問題 / 付款、發票
    [channel]       NVARCHAR(10)  NOT NULL,      -- 派單來源，見下方 CHECK
    [assignee_id]   NVARCHAR(10)  NOT NULL,      -- 負責處理的客服代號
    [follow_up_at]  DATETIME2(0)      NULL,      -- 排定的跟進／回電時間，行事曆用
    [created_at]    DATETIME2(0)  NOT NULL,      -- 建立時間
    [updated_at]    DATETIME2(0)  NOT NULL,      -- 最後更新時間，由 JPA @PreUpdate 維護
    CONSTRAINT [PK_tickets] PRIMARY KEY CLUSTERED ([ticket_id] ASC)
)
GO

-- ticket_no 由 ticket_id 推導、已經不可能重複，這條唯一約束是最後一道防線：
-- 萬一哪天有人改了運算式而算出重複值，會在寫入當下就爆，而不是等到出貨才發現。
ALTER TABLE [dbo].[tickets] ADD CONSTRAINT [UQ_tickets_ticket_no]
    UNIQUE NONCLUSTERED ([ticket_no] ASC)
GO

ALTER TABLE [dbo].[tickets] ADD CONSTRAINT [DF_tickets_created_at]
    DEFAULT (SYSDATETIME()) FOR [created_at]
GO
ALTER TABLE [dbo].[tickets] ADD CONSTRAINT [DF_tickets_updated_at]
    DEFAULT (SYSDATETIME()) FOR [updated_at]
GO

ALTER TABLE [dbo].[tickets] WITH CHECK ADD CONSTRAINT [FK_tickets_agents]
    FOREIGN KEY ([assignee_id]) REFERENCES [dbo].[agents] ([agent_id])
GO
ALTER TABLE [dbo].[tickets] CHECK CONSTRAINT [FK_tickets_agents]
GO

ALTER TABLE [dbo].[tickets] WITH CHECK ADD CONSTRAINT [CK_tickets_status]
    CHECK ([status] IN (N'IN_PROGRESS', N'PENDING', N'RESOLVED'))
GO
ALTER TABLE [dbo].[tickets] CHECK CONSTRAINT [CK_tickets_status]
GO

-- 派單來源：PHONE = 通話工作台在通話中建立，Agent = 客服從「＋ 新增派件」手動建立。
-- 值寫成 Agent（首字大寫）是照需求原文。
-- 提醒：SQL Server 預設定序不分大小寫，所以程式送 AGENT、agent 也會通過這條 CHECK，
--       資料庫不會幫你統一大小寫，要一致得由後端自己保證。
ALTER TABLE [dbo].[tickets] WITH CHECK ADD CONSTRAINT [CK_tickets_channel]
    CHECK ([channel] IN (N'PHONE', N'Agent'))
GO
ALTER TABLE [dbo].[tickets] CHECK CONSTRAINT [CK_tickets_channel]
GO

-- 索引：對應 GET /api/tickets 的查詢條件
--
-- 首頁列表最主要的查詢長這樣：
--     WHERE assignee_id = ? AND status = ? ORDER BY created_at DESC
--
-- 欄位順序是有講究的，不能隨便排：
--   assignee_id 放第一 → 等值比對，選擇性最高，一刀砍掉最多資料
--   status      放第二 → 等值比對，對應狀態 tab
--   created_at  放最後 → 只用來排序；寫 DESC 是為了讓 SQL Server
--                        直接照索引順序讀出來，省掉一次 Sort 運算
--
-- 不分狀態的「我的工單」查詢，以及 tabCounts 的 GROUP BY status，
-- 都吃得到這條索引的前綴，所以不必再單獨開一條 (assignee_id, created_at)。
-- 也沒有單獨的 status 索引：只有三種值、選擇性太低，最佳化工具通常不會選它，
-- 卻仍要在每次寫入時付出維護成本。
CREATE NONCLUSTERED INDEX [IX_tickets_assignee_status_created]
    ON [dbo].[tickets] ([assignee_id] ASC, [status] ASC, [created_at] DESC)
GO
-- 通話工作台：依進線號碼查歷史紀錄。那是等值比對，這條索引用得到。
-- （列表篩選欄的電話模糊查 LIKE '%0912%' 吃不到索引——前面帶萬用字元就無法
--   從索引的排序結構做二分搜尋——但那是模糊查本身的限制，不是這條索引沒用。）
CREATE NONCLUSTERED INDEX [IX_tickets_contact_phone]
    ON [dbo].[tickets] ([contact_phone] ASC)
GO
-- 行事曆：查某人某個月的跟進安排。
-- 這裡刻意「不」用篩選索引（WHERE follow_up_at IS NOT NULL）。
-- 篩選索引雖然省空間，但 SQL Server 規定：資料表只要有篩選索引，
-- 所有 INSERT/UPDATE/DELETE 都必須在 QUOTED_IDENTIFIER ON 的連線下執行。
-- JDBC 驅動預設是 ON 沒問題，但 sqlcmd 預設是 OFF，
-- 用命令列或 SSMS 手動改資料時會噴一個很難懂的錯誤，不值得。
CREATE NONCLUSTERED INDEX [IX_tickets_follow_up]
    ON [dbo].[tickets] ([assignee_id] ASC, [follow_up_at] ASC)
GO

-- ---------------------------------------------------------------------
-- 3. 工單處理記錄 / 留言
-- ---------------------------------------------------------------------
CREATE TABLE [dbo].[ticket_comments](
    [comment_id] INT IDENTITY(1,1) NOT NULL,  -- 留言／紀錄流水號
    [ticket_id]  INT           NOT NULL,      -- 所屬工單
    [agent_id]   NVARCHAR(10)      NULL,      -- 留言的客服代號，系統事件為 NULL
    [content]    NVARCHAR(MAX) NOT NULL,      -- 留言內容或系統事件描述
    [created_at] DATETIME2(0)  NOT NULL,      -- 建立時間
    CONSTRAINT [PK_ticket_comments] PRIMARY KEY CLUSTERED ([comment_id] ASC)
)
GO

ALTER TABLE [dbo].[ticket_comments] ADD CONSTRAINT [DF_ticket_comments_created_at]
    DEFAULT (SYSDATETIME()) FOR [created_at]
GO

-- 工單刪掉時，底下的處理記錄一起刪
ALTER TABLE [dbo].[ticket_comments] WITH CHECK ADD CONSTRAINT [FK_ticket_comments_tickets]
    FOREIGN KEY ([ticket_id]) REFERENCES [dbo].[tickets] ([ticket_id])
    ON DELETE CASCADE
GO
ALTER TABLE [dbo].[ticket_comments] CHECK CONSTRAINT [FK_ticket_comments_tickets]
GO

-- 客服不可隨意刪除（留言要留著當稽核紀錄），所以這條不設 CASCADE
ALTER TABLE [dbo].[ticket_comments] WITH CHECK ADD CONSTRAINT [FK_ticket_comments_agents]
    FOREIGN KEY ([agent_id]) REFERENCES [dbo].[agents] ([agent_id])
GO
ALTER TABLE [dbo].[ticket_comments] CHECK CONSTRAINT [FK_ticket_comments_agents]
GO

-- 工單詳情頁要撈整串 timeline，依時間排序
CREATE NONCLUSTERED INDEX [IX_ticket_comments_ticket]
    ON [dbo].[ticket_comments] ([ticket_id] ASC, [created_at] ASC)
GO
