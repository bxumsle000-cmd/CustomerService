-- =====================================================================
-- V3 調整既有設計：補 agents.updated_at、重整 tickets 的狀態查詢索引
--
-- 為什麼要開 V3 而不是直接改 V1？
--   V1 已經執行過，Flyway 會對「整個檔案」算 checksum 並比對，
--   連改一個註解、動一格空白都會讓下次啟動噴 Migration checksum mismatch。
--   所以既有 migration 一律視為唯讀，任何結構調整都往後新增版本。
--
-- 本檔做三件事：
--   1. agents 補上 updated_at（原本改了狀態卻不知道何時改的）
--   2. tickets 新增 (assignee_id, status, created_at DESC) 複合索引
--   3. 移除 IX_tickets_status（選擇性太低，且已被上面那條涵蓋）
--
-- 注意：這支檔案跑過之後就不可再修改，要改請新增 V4__xxx.sql。
-- =====================================================================

SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO

-- ---------------------------------------------------------------------
-- 1. agents 補 updated_at
-- ---------------------------------------------------------------------
-- 原本的 agents 只有 created_at。客服狀態（ONLINE / LUNCH / ...）會一直變，
-- 但改了之後完全查不到是什麼時候改的，連「這個人掛在午休多久了」都答不出來。
--
-- 這裡只補「最後更新時間」，仍然不留狀態歷史。
-- 要做工時統計（每個狀態累計多久）得另開 agent_status_logs 一列一列記，
-- 那是另一個題目，本次不做。
--
-- NOT NULL + DEFAULT 的寫法：SQL Server 在 ADD 欄位時會用 DEFAULT 把
-- 既有資料列一次補滿，所以就算表裡已經有 V2 塞的三個客服也不會失敗。
ALTER TABLE [dbo].[agents] ADD [updated_at] DATETIME2(0) NOT NULL
    CONSTRAINT [DF_agents_updated_at] DEFAULT (SYSDATETIME())
GO

-- 上一步會把既有資料列的 updated_at 填成「執行這支 migration 的當下」，
-- 但那些帳號其實從建立後就沒被改過，填現在時間語意是錯的。
-- 回填成 created_at，讓「從未更新過的資料列，updated_at 等於 created_at」成立。
UPDATE [dbo].[agents] SET [updated_at] = [created_at]
GO

-- ---------------------------------------------------------------------
-- 2. tickets 新增複合索引，支援「我的工單 + 狀態篩選 + 時間排序」
-- ---------------------------------------------------------------------
-- 首頁列表最主要的查詢長這樣（見 docs/api.md 的 GET /api/tickets）：
--     WHERE assignee_id = ? AND status = ? ORDER BY created_at DESC
--
-- 欄位順序是有講究的，不能隨便排：
--   assignee_id 放第一 → 等值比對，選擇性最高，一刀砍掉最多資料
--   status      放第二 → 等值比對，對應四個狀態 tab
--   created_at  放最後 → 只用來排序；順序寫 DESC 是為了讓 SQL Server
--                        直接照索引順序讀出來，省掉一次 Sort 運算
--
-- 順帶一提，tabCounts（四個 tab 上的數字）要的是
--     WHERE assignee_id = ? GROUP BY status
-- 剛好吃得到這條索引的前兩欄，不必另外再開一條。
CREATE NONCLUSTERED INDEX [IX_tickets_assignee_status_created]
    ON [dbo].[tickets] ([assignee_id] ASC, [status] ASC, [created_at] DESC)
GO

-- ---------------------------------------------------------------------
-- 3. 移除 IX_tickets_status
-- ---------------------------------------------------------------------
-- 兩個理由：
--
-- (a) 選擇性太低。status 只有三種值（IN_PROGRESS / PENDING / RESOLVED），
--     等於整張表大約各佔三分之一。查詢最佳化工具遇到這種索引，通常會判斷
--     「與其走索引再逐筆回表查其他欄位，不如直接掃全表」，結果就是這條索引
--     幾乎不會被選中，卻仍要在每次 INSERT / UPDATE / DELETE 時付出維護成本。
--
-- (b) 已被涵蓋。實際畫面上幾乎不存在「不分負責人、只看某個狀態」的查詢，
--     列表一定會帶 assignee_id，而那個情境上面那條複合索引已經處理掉了。
--
-- 之後若真的出現跨客服的全域狀態查詢（例如主管看板），再開 V4 加回來即可。
DROP INDEX [IX_tickets_status] ON [dbo].[tickets]
GO

-- ---------------------------------------------------------------------
-- 沒有動的東西，以及原因
-- ---------------------------------------------------------------------
-- IX_tickets_contact_phone（保留）
--   通話工作台是拿「進線號碼」去查歷史紀錄，那是等值比對，這條索引用得到。
--   列表篩選欄的電話模糊查（LIKE '%0912%'）確實吃不到索引——前面帶萬用字元
--   就無法從索引的排序結構做二分搜尋——但那是模糊查本身的限制，
--   不是這條索引沒用，所以留著。
--
-- tickets.category（維持自由字串，不加 CHECK）
--   分類還可能異動，寫死成 CHECK 之後每次調整都要再開一支 migration。
--   代價是資料庫不擋髒資料，必須由後端自己驗證。
