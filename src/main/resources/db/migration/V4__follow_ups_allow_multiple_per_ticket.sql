-- =====================================================================
-- V4 讓同一張工單可以排多筆回電
--
-- 【改了什麼】
-- V3 建表時放了 UQ_follow_ups_agent_ticket (agent_id, ticket_id)，
-- 意思是「同一個人對同一張單只排一次，重排就是改那一列的時間」。
-- 實際用起來這個限制太緊：一張單常常需要先週三初步回覆、再週五確認結果，
-- 甚至同一天上午下午各排一次。
--
-- 所以把唯一鍵從兩欄改成三欄，多帶 follow_up_at：
--   同一個人 + 同一張單 + 同一個時間點  → 仍然不行（重複排定，通常是誤按）
--   同一個人 + 同一張單 + 不同時間點    → 可以，不限同天或跨天
--
-- 【欄位順序為什麼是 agent_id 在前】
-- 理由同 V3：唯一性怎麼排都成立，但索引前綴只有最前面的欄位能單獨用，
-- 而這張表所有查詢都是以 agent_id 起手。
--
-- 【對既有資料安全嗎】
-- 安全。舊約束比新約束嚴格（兩欄唯一 ⊃ 三欄唯一），
-- 能通過舊約束的資料一定也通過新的，不可能在建立時失敗。
--
-- 【連帶影響】
-- 「一筆安排的身分」從 (agent_id, ticket_id) 變成 follow_up_id，
-- 因為單號不再唯一指向一筆安排。所以 follow_up_id 從純內部主鍵
-- 變成要對外露出的識別碼，Java 那邊 repository / service / DTO 一起改了。
--
-- 註：V3 的檔頭把「DROP tickets.follow_up_at」預留為 V4，那支順延成 V5。
--     版本號只要遞增且不重複即可，Flyway 不在意中間的編號拿去做了什麼。
-- =====================================================================

SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO

-- 舊的兩欄唯一鍵。DROP CONSTRAINT 會連它背後那條索引一起移除，不必另外 DROP INDEX。
ALTER TABLE [dbo].[follow_ups] DROP CONSTRAINT [UQ_follow_ups_agent_ticket]
GO

-- 新的三欄唯一鍵。名字跟著改，之後看 constraint 名稱就知道包含時間。
ALTER TABLE [dbo].[follow_ups] ADD CONSTRAINT [UQ_follow_ups_agent_ticket_time]
    UNIQUE NONCLUSTERED ([agent_id] ASC, [ticket_id] ASC, [follow_up_at] ASC)
GO

-- IX_follow_ups_agent_time (agent_id, follow_up_at) 不動：
-- 它服務的是月檢視的範圍查詢，上面這條 UQ 的第二欄是 ticket_id，幫不上忙。
