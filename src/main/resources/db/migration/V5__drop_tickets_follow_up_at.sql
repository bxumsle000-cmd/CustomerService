-- =====================================================================
-- V5 收縮：移除 tickets.follow_up_at
--
-- 【為什麼現在才刪】
-- V3 檔頭寫過的三步驟「先擴張、再搬移、最後收縮」，這支是最後一步：
--   V3  建 follow_ups + 把 tickets.follow_up_at 的資料搬過去（兩邊並存）
--   之後 Java 全面改吃 follow_ups
--   V5  確認沒人再讀舊欄位，才 DROP
-- （V3 原本把這步預留為 V4，後來 V4 被拿去改唯一鍵，這支順延成 V5。）
--
-- 【確認過沒人再讀】
-- Tickets entity 已經沒有 followUpAt 欄位對映，TicketsRepository 也沒有
-- findByAssigneeIdAndFollowUpAt... 那支查詢，CalendarService 整支改走
-- FollowUpsRepository。ddl-auto=none，Hibernate 不會自己去碰結構，
-- 所以刪掉之後不會有任何 SQL 再提到這個欄位。
--
-- 【為什麼一定要先 DROP INDEX】
-- IX_tickets_follow_up 是 (assignee_id, follow_up_at) 兩欄的索引，
-- SQL Server 不允許刪掉「被索引引用中」的欄位，直接 DROP COLUMN 會噴：
--   The index 'IX_tickets_follow_up' is dependent on column 'follow_up_at'.
-- 順序反過來就對了：先拆掉相依的索引，再刪欄位。
-- 這個欄位沒有 DEFAULT / CHECK / FK 之類的其他相依物件，所以只有索引要處理。
--
-- 【資料會不會不見】
-- 會，而且是刻意的——這個欄位的值 V3 已經整批搬進 follow_ups 了，
-- 從那之後唯一的真相就是 follow_ups，舊欄位只是還沒清掉的空殼。
-- DROP COLUMN 不可逆，正式環境跑之前照慣例先備份。
--
-- 注意：這支檔案跑過之後就不可再修改，Flyway 會比對 checksum，
--       連改一個空白都會讓下次啟動噴 Migration checksum mismatch。
--       要改結構請新增 V6__xxx.sql。
-- =====================================================================

SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO

-- 1. 先拆掉引用這個欄位的索引
DROP INDEX [IX_tickets_follow_up] ON [dbo].[tickets]
GO

-- 2. 再刪欄位
ALTER TABLE [dbo].[tickets] DROP COLUMN [follow_up_at]
GO
