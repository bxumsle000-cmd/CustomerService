-- =====================================================================
-- V5 把 tickets.title 縮到 NVARCHAR(50)，讓資料庫的字串長度與
--    dto/CreateTicketRequest.java 的驗證規則完全一致
--
-- 為什麼開 V5 而不是回頭改 V4？
--   Flyway 對「已經跑過的 migration 檔案」算 checksum 並比對，
--   連改一個空白都會讓下次啟動噴 Migration checksum mismatch。
--   既有 migration 一律視為唯讀，結構要調整就往後加版本。
--
-- ---------------------------------------------------------------------
-- 為什麼還需要這一支？V4 不是已經做過了嗎？
-- ---------------------------------------------------------------------
-- V4 的第 1 節原本要把 title 由 NVARCHAR(200) 縮到 NVARCHAR(50)，
-- 但那段 ALTER COLUMN 在檔案裡是被 `--` 註解掉的狀態，只有前面那句
-- 「UPDATE ... SET title = LEFT(title, 50)」真的執行了。
-- 結果是：既有資料已經被截短到 50 字，欄位宣告卻還停在 200。
--
-- 實際查過資料庫確認（INFORMATION_SCHEMA.COLUMNS）：
--   ticket_no      NVARCHAR(10)  NOT NULL   ← 格式 TK-XXXXXX 共 9 字，夠用
--   customer_name  NVARCHAR(255) NULL       ← V4 已放寬，DTO 不驗長度，相符
--   contact_phone  NVARCHAR(50)  NULL       ← V4 已放寬，DTO 不驗長度，相符
--   title          NVARCHAR(200) NOT NULL   ← ★ 只有這一欄跟 DTO 對不上
--   description    NVARCHAR(MAX) NULL       ← DTO 不驗長度，相符
--   status         NVARCHAR(20)  NOT NULL   ← 白名單最長 IN_PROGRESS（11 字），夠用
--   category       NVARCHAR(255) NOT NULL   ← V4 已放寬，DTO 不驗長度，相符
--   channel        NVARCHAR(10)  NOT NULL   ← 白名單 PHONE / Agent（5 字），夠用
--   assignee_id    NVARCHAR(10)  NOT NULL   ← FK 指向 agents.agent_id，DTO 註明維持不動
--
-- 所以本檔只做 title 這一欄，其餘欄位「已經是對的」，不需要也不應該再動。
--
-- ---------------------------------------------------------------------
-- 兩邊都要擋，不是只擋一邊
-- ---------------------------------------------------------------------
-- DTO 的 @Size(max = 50) 是為了讓使用者看到「主旨長度不可超過 50」這種看得懂的
-- 400 錯誤，而不是撞到資料庫長度限制之後變成 500「系統發生錯誤」。
-- 但驗證只擋得住走 API 進來的請求，擋不住直接對資料庫下 SQL 的人，
-- 所以欄位本身也要收到 50，兩道防線的數字必須一致。
--
-- 注意：這支檔案跑過之後就不可再修改，要改請新增 V6__xxx.sql。
-- =====================================================================

SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO

-- ---------------------------------------------------------------------
-- 1. 先把超過 50 字的主旨截掉
-- ---------------------------------------------------------------------
-- 「縮小」欄位跟「放寬」不一樣：只要表裡有任何一列的主旨超過 50 個字，
-- 下一步的 ALTER COLUMN 就會直接失敗
-- （String or binary data would be truncated），整支 migration 回滾、
-- 應用程式起不來。
--
-- V4 已經跑過一次同樣的 UPDATE，但那之後又過了一段時間，
-- 期間新建的工單仍然可能超過 50 字（因為欄位當時還是 200，資料庫不擋）。
-- 這一步保留著才安全，而且對已經合規的資料是零影響（WHERE 篩不到任何列）。
--
-- 這一步會「真的改到資料」。目前庫裡只有開發測試資料所以無所謂；
-- 若之後要在有正式資料的環境跑，請先備份或改成人工確認。
UPDATE [dbo].[tickets]
   SET [title] = LEFT([title], 50)
 WHERE LEN([title]) > 50
GO

-- ---------------------------------------------------------------------
-- 2. title：NVARCHAR(200) → NVARCHAR(50)
-- ---------------------------------------------------------------------
-- ALTER COLUMN 是「整欄重新宣告」，不是只改長度：
-- NOT NULL 沒有跟著寫上去的話，欄位會被改成允許 NULL，
-- 等於偷偷把必填欄位變成選填。所以這裡一定要把 NOT NULL 照抄回來。
--
-- title 沒有被任何索引或約束參照（查過 sys.indexes 與 sys.objects：
-- tickets 上的索引都在 assignee_id / status / created_at / contact_phone，
-- 約束則是 PK / UQ_ticket_no / 兩個 DF / FK_agents / CK_status / CK_channel），
-- 所以不必先 DROP 再重建，可以直接改。
ALTER TABLE [dbo].[tickets] ALTER COLUMN [title] NVARCHAR(50) NOT NULL
GO
