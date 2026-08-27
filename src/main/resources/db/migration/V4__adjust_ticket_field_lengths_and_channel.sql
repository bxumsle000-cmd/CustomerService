-- =====================================================================
-- V4 調整 tickets 的欄位長度，並把 channel（派單來源）白名單改成 PHONE / Agent
--
-- 為什麼開 V4 而不是回頭改 V1／V3？
--   Flyway 對「已經跑過的 migration 檔案」算 checksum 並比對，
--   連改一個空白都會讓下次啟動噴 Migration checksum mismatch。
--   既有 migration 一律視為唯讀，結構要調整就往後加版本。
--
-- 本檔做四件事：
--   1. title 由 NVARCHAR(200) 縮到 NVARCHAR(50)
--   2. customer_name / category 放寬到 NVARCHAR(255)、contact_phone 放寬到 NVARCHAR(50)
--   3. channel 的 CHECK 由 PHONE / EMAIL 改成 PHONE / Agent
--   4. 把既有的 EMAIL 資料轉成 Agent（不然新 CHECK 建不起來）
--
-- 沒有動 assignee_id：它是外鍵指向 agents.agent_id NVARCHAR(10)，
-- 要放寬得連 agents 的主鍵一起改（先砍 FK → 改兩邊 → 再建回來），
-- 而客服代號本來就是 CSC00001 這種固定格式，放寬沒有實際好處，所以維持原樣。
--
-- 注意：這支檔案跑過之後就不可再修改，要改請新增 V5__xxx.sql。
-- =====================================================================

SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO

-- ---------------------------------------------------------------------
-- 1. title：NVARCHAR(200) → NVARCHAR(50)
-- ---------------------------------------------------------------------
-- 這是「縮小」欄位，跟放寬不一樣：只要表裡有任何一列的主旨超過 50 個字，
-- ALTER COLUMN 會直接失敗（String or binary data would be truncated），
-- 整支 migration 回滾，應用程式起不來。
--
-- 所以先把超長的主旨自己截掉。這一步會「真的改到資料」，
-- 目前表裡只有開發測試資料所以無所謂；如果之後在有正式資料的環境要跑，
-- 請先自己備份或改成人工確認。
UPDATE [dbo].[tickets]
   SET [title] = LEFT([title], 50)
 WHERE LEN([title]) > 50
GO

-- ALTER COLUMN 是「整欄重新宣告」，不是只改長度：
-- NOT NULL 沒有跟著寫上去的話，欄位會被改成允許 NULL。
-- ALTER TABLE [dbo].[tickets] ALTER COLUMN [title] NVARCHAR(50) NOT NULL
-- GO
--
-- -- ---------------------------------------------------------------------
-- -- 2. 放寬 customer_name / category / contact_phone
-- -- ---------------------------------------------------------------------
-- -- 這三欄改成「實務上不會撞到」的長度，後端就不用再為它們做長度驗證。
-- -- 沒有一路開到 NVARCHAR(MAX) 的原因寫在下面 contact_phone 那段。
--
-- customer_name、contact_phone 原本就允許 NULL，這裡不能補 NOT NULL，
-- 否則等於偷偷把它們變成必填。category 原本是 NOT NULL，要照抄回來。
ALTER TABLE [dbo].[tickets] ALTER COLUMN [customer_name] NVARCHAR(255) NULL
    GO

ALTER TABLE [dbo].[tickets] ALTER COLUMN [category] NVARCHAR(255) NOT NULL
    GO

-- contact_phone 上面有 IX_tickets_contact_phone（通話工作台用進線號碼查歷史工單）。
-- SQL Server 的規則是：欄位若被索引參照，ALTER COLUMN 只有在
-- 「型別不變、長度只增不減、且不是主鍵」時才允許——
-- NVARCHAR(30) → NVARCHAR(50) 剛好符合，所以索引可以原地保留，不必先 DROP 再建。
--
-- 反過來說，NVARCHAR(MAX) 就不行了：MAX 型別根本不能當索引鍵，
-- 要無限長就得永久砍掉這條索引，查號碼會退化成全表掃描，不划算。
ALTER TABLE [dbo].[tickets] ALTER COLUMN [contact_phone] NVARCHAR(50) NULL
GO

-- ---------------------------------------------------------------------
-- 3. channel（派單來源）白名單：PHONE / EMAIL → PHONE / Agent
-- ---------------------------------------------------------------------
-- 語意變了：原本記的是「客戶從哪個管道進線」（電話或 Email），
-- 現在記的是「這張工單怎麼來的」——
--   PHONE = 通話工作台在通話中建立
--   Agent = 客服自己從「＋ 新增派件」手動建立
--
-- 順序很重要：CHECK 一定要先 DROP，
-- 因為下一步要把舊的 EMAIL 資料改掉，而舊 CHECK 只認 PHONE / EMAIL，
-- 反過來先改資料的話，改成 Agent 的那一刻就會被舊 CHECK 擋下來。
ALTER TABLE [dbo].[tickets] DROP CONSTRAINT [CK_tickets_channel]
GO

-- 既有的 EMAIL 資料在新白名單裡沒有位置，統一收斂成 Agent
--（Email 進線本來就不是通話建立的，歸到「客服建立」這一類最接近）。
-- 這一步不能省：只要留下任何一列 EMAIL，
-- 下面帶 WITH CHECK 的新 CONSTRAINT 就會建立失敗。
UPDATE [dbo].[tickets]
   SET [channel] = N'Agent'
 WHERE [channel] = N'EMAIL'
GO

-- 值寫成 Agent（首字大寫）是照需求原文。
-- 提醒：SQL Server 預設定序不分大小寫，所以程式送 AGENT、agent 也會通過這條 CHECK，
-- 資料庫不會幫你統一大小寫，要一致得由後端自己保證。
ALTER TABLE [dbo].[tickets] WITH CHECK ADD CONSTRAINT [CK_tickets_channel]
    CHECK ([channel] IN (N'PHONE', N'Agent'))
GO
ALTER TABLE [dbo].[tickets] CHECK CONSTRAINT [CK_tickets_channel]
GO
