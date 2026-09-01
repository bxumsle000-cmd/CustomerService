-- =====================================================================
-- V3 行事曆回電安排：follow_ups
--
-- 【這張表在解什麼問題】
-- 在這之前，「排定的回電時間」是 tickets.follow_up_at 一個欄位。
-- 一個欄位只放得下一個值，而且那個值沒有主人——它屬於工單，不屬於某個客服。
-- 這張表把「回電安排」獨立成一筆資料，讓它有三件單欄位表達不了的東西：
--   1. 主人（agent_id）：這是「誰的」行事曆安排，不會因為工單轉派就跟著換人
--   2. 個人備註（note）：只有主人看得到，不寫進 ticket_comments 那條共用時間軸
--   3. 一張工單可以有多個人各自的安排（每人一筆）
--
-- 【顯示用的欄位為什麼不存在這裡】
-- 行事曆格子上要顯示的 ticket_no / title / status，一律查 tickets 拿，不複製一份進來。
-- 尤其是 status：它會從 IN_PROGRESS 變成 RESOLVED，前端還要靠它決定事件顏色。
-- 複製進來就變成快照，工單結案了行事曆那格還停在「處理中」，而且永遠不會自己更新。
-- 通則：會變的東西用查的，不會變的才複製。
--
-- 【tickets.follow_up_at 為什麼還留著】
-- 這支 migration 只「新增」，不動舊欄位，因為 Java 那邊還在讀它：
-- Tickets entity 有 followUpAt 欄位對映、TicketsRepository 有
-- findByAssigneeIdAndFollowUpAt... 這支查詢、CalendarService 整支都靠它。
-- 現在就 DROP 的話，ddl-auto=none 不會在啟動時擋下來，而是等到有人查工單列表
-- 才噴「Invalid column name 'follow_up_at'」——連工單首頁都會壞，不只行事曆。
--
-- 正確順序是「先擴張、再搬移、最後收縮」：
--   V3（這支）  建新表 + 把舊資料搬過來，兩邊並存
--   接著        改 Java：entity / repository / service / DTO 改用 follow_ups
--   V4（之後）  確認沒人再讀舊欄位，才 DROP INDEX + DROP COLUMN
-- 中間這段並存期間，唯一的真相是 follow_ups，舊欄位不要再寫入。
--
-- 注意：這支檔案跑過之後就不可再修改，Flyway 會比對 checksum，
--       連改一個空白都會讓下次啟動噴 Migration checksum mismatch。
--       要改結構請新增 V4__xxx.sql。
-- =====================================================================

SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO

-- ---------------------------------------------------------------------
-- 1. 回電安排
-- ---------------------------------------------------------------------
CREATE TABLE [dbo].[follow_ups](
    [follow_up_id] INT IDENTITY(1,1) NOT NULL,  -- 流水號（內部主鍵，不對外）
    [agent_id]     NVARCHAR(10)  NOT NULL,      -- 這筆安排的主人，也就是「誰的行事曆」
    [ticket_id]    INT           NOT NULL,      -- 要跟進的工單（內部 id，不是 TK-000001）
    [follow_up_at] DATETIME2(0)  NOT NULL,      -- 排定的回電時間，精度到秒
    [note]         NVARCHAR(200)     NULL,      -- 個人備註，只有主人看得到
    CONSTRAINT [PK_follow_ups] PRIMARY KEY CLUSTERED ([follow_up_id] ASC)
)
GO

-- follow_up_at 是 NOT NULL：沒有時間就不成其為一筆行事曆安排。
-- 所以「取消排定」在這個設計裡是 DELETE 掉整列，不是把欄位設成 NULL。
-- （舊設計是單欄位，只能用 NULL 表示取消，CalendarService.updateFollowUp()
--   那個「傳 null 代表取消」的特殊規則，改用這張表之後可以拆成獨立的刪除方法。）

-- note 長度 200（約 100 個中文字）要跟前端輸入框、DTO 的 @Size(max = 200) 對齊，
-- 三個地方寫同一個數字。作法同 tickets.title 的 NVARCHAR(50) ←→ @Size(max = 50)。

-- ---------------------------------------------------------------------
-- 2. 外鍵
-- ---------------------------------------------------------------------

-- 工單被刪掉時，掛在它底下的回電安排一起刪：工單都不在了，行事曆上那格點進去會 404。
-- 這跟 ticket_comments 的作法一致。
ALTER TABLE [dbo].[follow_ups] WITH CHECK ADD CONSTRAINT [FK_follow_ups_tickets]
    FOREIGN KEY ([ticket_id]) REFERENCES [dbo].[tickets] ([ticket_id])
    ON DELETE CASCADE
GO
ALTER TABLE [dbo].[follow_ups] CHECK CONSTRAINT [FK_follow_ups_tickets]
GO

-- 客服這條刻意不設 CASCADE，理由同 ticket_comments：帳號本來就不該被隨意刪除，
-- 真要刪也應該先處理掉他手上的東西，而不是靜悄悄連帶刪掉一批資料。
--
-- 提醒：follow_ups 上只有「一條」CASCADE 外鍵（來自 tickets），另一條是 NO ACTION，
-- 所以不會踩到 SQL Server 的 multiple cascade paths 限制。
-- 之後若把這條也改成 CASCADE，agents → tickets → follow_ups 和 agents → follow_ups
-- 會形成兩條刪除路徑，建立約束當下就會被擋下來。
ALTER TABLE [dbo].[follow_ups] WITH CHECK ADD CONSTRAINT [FK_follow_ups_agents]
    FOREIGN KEY ([agent_id]) REFERENCES [dbo].[agents] ([agent_id])
GO
ALTER TABLE [dbo].[follow_ups] CHECK CONSTRAINT [FK_follow_ups_agents]
GO

-- ---------------------------------------------------------------------
-- 3. 約束與索引
-- ---------------------------------------------------------------------

-- 同一個人對同一張工單只排一次：重排就是改這一列的時間，不是再插一列。
-- 這條約束讓「一張單在我的行事曆上出現兩次」在資料庫層變成不可能，
-- 後端不必自己先查一次再決定要 INSERT 還是 UPDATE。
--
-- 注意欄位順序是 (agent_id, ticket_id) 不是反過來：唯一性兩種寫法都成立，
-- 但索引前綴只有第一個欄位能單獨用，而這張表所有查詢都是以 agent_id 起手。
--
-- 之後若要支援「同一張單排多次回電」（先週三初步回覆、再週五確認結果），
-- 拿掉這條約束就行，其他都不用動。
ALTER TABLE [dbo].[follow_ups] ADD CONSTRAINT [UQ_follow_ups_agent_ticket]
    UNIQUE NONCLUSTERED ([agent_id] ASC, [ticket_id] ASC)
GO

-- 行事曆月檢視的主要查詢：
--     WHERE agent_id = ? AND follow_up_at >= ? AND follow_up_at < ? ORDER BY follow_up_at
--
-- 欄位順序：
--   agent_id     放第一 → 等值比對，一刀砍到只剩自己的資料
--   follow_up_at 放第二 → 範圍比對兼排序；照索引順序讀出來就已經排好，省掉 Sort
--
-- 上面那條 UQ 的前綴雖然也是 agent_id，但第二欄是 ticket_id，
-- 幫不上 follow_up_at 的範圍查詢，所以這條索引不能省。
CREATE NONCLUSTERED INDEX [IX_follow_ups_agent_time]
    ON [dbo].[follow_ups] ([agent_id] ASC, [follow_up_at] ASC)
GO

-- ---------------------------------------------------------------------
-- 4. 把 tickets.follow_up_at 既有的資料搬過來
-- ---------------------------------------------------------------------
--
-- 舊欄位沒有主人的概念，只能認定「排這筆回電的就是當時的負責客服」，
-- 所以 agent_id 取 assignee_id。舊資料本來就沒有備註，note 留 NULL。
--
-- 目前 CalendarController 還沒接上，這批資料通常是零筆或少數手動測試資料；
-- 就算是零筆，這段也必須留著——先在別的環境跑過 V1/V2 並塞了資料的資料庫，
-- 跑到這支 migration 時就靠它把資料接過來。
INSERT INTO [dbo].[follow_ups] ([agent_id], [ticket_id], [follow_up_at])
SELECT [assignee_id], [ticket_id], [follow_up_at]
FROM [dbo].[tickets]
WHERE [follow_up_at] IS NOT NULL
GO
