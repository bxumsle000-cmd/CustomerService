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
-- 注意：這支檔案跑過之後就不可再修改，Flyway 會比對 checksum。
--       要改結構請新增 V3__xxx.sql。
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
    CONSTRAINT [PK_agents] PRIMARY KEY CLUSTERED ([agent_id] ASC)
)
GO

ALTER TABLE [dbo].[agents] ADD CONSTRAINT [DF_agents_status]
    DEFAULT (N'ONLINE') FOR [status]
GO
ALTER TABLE [dbo].[agents] ADD CONSTRAINT [DF_agents_created_at]
    DEFAULT (SYSDATETIME()) FOR [created_at]
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
CREATE TABLE [dbo].[tickets](
    [ticket_id]     INT IDENTITY(1,1) NOT NULL,  -- 工單流水號（內部主鍵，不對外）
    [ticket_no]     NVARCHAR(10)  NOT NULL,      -- 對外顯示的工單編號，格式 TK-XXXXXX
    [customer_name] NVARCHAR(50)      NULL,      -- 通話中向客戶確認的姓名，未提供可為 NULL
    [contact_phone] NVARCHAR(30)      NULL,      -- 客戶提供的聯絡電話，用來查歷史紀錄
    [title]         NVARCHAR(200) NOT NULL,      -- 工單主旨
    [description]   NVARCHAR(MAX)     NULL,      -- 問題描述內容
    [status]        NVARCHAR(20)  NOT NULL,      -- 處理狀態，見下方 CHECK
    [category]      NVARCHAR(30)  NOT NULL,      -- 問題分類，例如 帳號問題 / 付款、發票
    [channel]       NVARCHAR(10)  NOT NULL,      -- 進線管道，見下方 CHECK
    [assignee_id]   NVARCHAR(10)  NOT NULL,      -- 負責處理的客服代號
    [follow_up_at]  DATETIME2(0)      NULL,      -- 排定的跟進／回電時間，行事曆用
    [created_at]    DATETIME2(0)  NOT NULL,      -- 建立時間
    [updated_at]    DATETIME2(0)  NOT NULL,      -- 最後更新時間，由 JPA @PreUpdate 維護
    CONSTRAINT [PK_tickets] PRIMARY KEY CLUSTERED ([ticket_id] ASC),
    CONSTRAINT [UQ_tickets_ticket_no] UNIQUE NONCLUSTERED ([ticket_no] ASC)
)
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

ALTER TABLE [dbo].[tickets] WITH CHECK ADD CONSTRAINT [CK_tickets_channel]
    CHECK ([channel] IN (N'PHONE', N'EMAIL'))
GO
ALTER TABLE [dbo].[tickets] CHECK CONSTRAINT [CK_tickets_channel]
GO

-- 索引：對應 docs/api.md 的 GET /api/tickets 查詢條件
-- 首頁列表預設就是「我的工單、依建立時間新到舊」，所以做成複合索引。
CREATE NONCLUSTERED INDEX [IX_tickets_assignee_created]
    ON [dbo].[tickets] ([assignee_id] ASC, [created_at] DESC)
GO
-- 狀態 tab 快篩
CREATE NONCLUSTERED INDEX [IX_tickets_status]
    ON [dbo].[tickets] ([status] ASC)
GO
-- 通話工作台：依進線號碼查歷史紀錄
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
    [author_id]  NVARCHAR(10)      NULL,      -- 留言的客服代號，系統事件為 NULL
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
    FOREIGN KEY ([author_id]) REFERENCES [dbo].[agents] ([agent_id])
GO
ALTER TABLE [dbo].[ticket_comments] CHECK CONSTRAINT [FK_ticket_comments_agents]
GO

-- 工單詳情頁要撈整串 timeline，依時間排序
CREATE NONCLUSTERED INDEX [IX_ticket_comments_ticket]
    ON [dbo].[ticket_comments] ([ticket_id] ASC, [created_at] ASC)
GO
