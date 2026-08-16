# 電話客服工單系統 — 資料庫 Schema

依據 `index.html` 原型整理出的資料表設計。核心表有 3 張（客服人員、工單、工單處理記錄），另外 3 張是選用的稽核／報表用表。

## 一、核心資料表

### 1. 客服人員 agents

```sql
CREATE TABLE agents (
  agent_id      VARCHAR(10)  PRIMARY KEY,   -- 客服代號，例如 CSC00001
  name          VARCHAR(50)  NOT NULL,      -- 客服姓名
  password_hash VARCHAR(255) NOT NULL,      -- 登入密碼雜湊值
  is_available  BOOLEAN      NOT NULL DEFAULT TRUE,  -- 是否可接聽：TRUE=可接聽／FALSE=離線
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  -- 帳號建立時間
);
```

### 2. 工單 tickets

```sql
CREATE TABLE tickets (
  ticket_id      INT AUTO_INCREMENT PRIMARY KEY,          -- 工單流水號（內部主鍵）
  ticket_no      VARCHAR(10)  NOT NULL UNIQUE,             -- 對外顯示的工單編號，格式 TK-XXXXXX
  customer_name  VARCHAR(50),            -- 通話中向客戶確認的姓名，未提供可為 NULL
  contact_phone  VARCHAR(30),            -- 這通電話實際進線／客戶提供的號碼，用來查歷史紀錄，不代表已核實的客戶身分
  title          VARCHAR(200) NOT NULL,   -- 工單主旨
  description    TEXT,                    -- 問題描述內容
  status         VARCHAR(20)  NOT NULL,   -- 處理狀態：IN_PROGRESS 處理中 / PENDING 待客戶回覆 / RESOLVED 已解決
  category       VARCHAR(30)  NOT NULL,   -- 問題分類，例如 帳號問題 / 付款發票 / ...
  channel        VARCHAR(10)  NOT NULL,   -- 進線管道：PHONE 電話 / EMAIL 信件 /客服工單
  assignee_id    VARCHAR(10)  NOT NULL,   -- 負責處理的客服代號
  follow_up_at   DATETIME,               -- 排定的跟進／回電時間，行事曆用
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 建立時間
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,  -- 最後更新時間，資料庫自動維護
  FOREIGN KEY (assignee_id) REFERENCES agents(agent_id)
);
```


### 3. 工單處理記錄 / 留言 ticket_comments

```sql
CREATE TABLE ticket_comments (
  comment_id   INT AUTO_INCREMENT PRIMARY KEY,   -- 留言／紀錄流水號
  ticket_id    INT          NOT NULL,            -- 所屬工單
  author_id    VARCHAR(10),      -- 留言的客服代號，系統事件可為 NULL
  content      TEXT         NOT NULL,             -- 留言內容或系統事件描述
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 建立時間
  FOREIGN KEY (ticket_id) REFERENCES tickets(ticket_id),
  FOREIGN KEY (author_id) REFERENCES agents(agent_id)
);
```
