-- =====================================================================
-- V2 種子資料：三個開發用客服帳號
--
-- 為什麼一定要有這筆？因為 tickets.assignee_id 有外鍵指向 agents，
-- agents 是空的話連一張工單都建不出來，登入也無從測起。
--
-- 【開發用密碼：pass1234】三個帳號都一樣。
--   下面的 password_hash 是用 BCryptPasswordEncoder（strength 10）實際
--   算出來的，並已用 matches() 驗證過可以通過比對。
--   BCrypt 每次加鹽都不同，所以三行的雜湊值長得不一樣是正常的。
--
-- ⚠️ 這是開發／demo 用的假帳號，正式環境請務必改密碼或不要跑這支 migration。
--
-- 注意：這支檔案跑過之後就不可再修改，Flyway 會比對 checksum。
-- =====================================================================

SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO

-- status 不指定，讓它吃 DF_agents_status 的預設值 ONLINE。
-- 字串前面的 N 前綴代表 Unicode 字面值，存中文姓名一定要加。
INSERT INTO [dbo].[agents] ([agent_id], [name], [password_hash]) VALUES
    (N'CSC00001', N'林曉明', N'$2a$10$jAbfr7dQChMTXY9PMHZIT.r4qNz1ye8FB7xdEbHmTJHDczGYI4W.e'),
    (N'CSC00002', N'陳美芳', N'$2a$10$WQLaWHKTPR69yqT8S9SgkOHyuLu49ezfXNYwlDUQZWcZQcSo5FnOq'),
    (N'CSC00003', N'黃志豪', N'$2a$10$gQ5/bFNZ0vNwCl956c6dne8UvPdJqx8KSVoRPYmJrmSjbTrNf2nZu')
GO
