> 📌 本文件僅供個人參考閱讀，AI 寫 Code 時請勿參考此檔案內容。

# JPQL 語法整理

> 本文所有 JPQL 範例都拿這個專案的 `Agents` / `Tickets` / `TicketComments` 三個 Entity，
> 實際跑過 SQL Server（`docker compose up -d` 那台）驗證通過，不是憑印象寫的。
> 驗證方法見文末「附錄」。

---

## 一、JPQL 是什麼

**一句話：JPQL 長得像 SQL，但操作的是「Java 物件」，不是「資料表」。**

| | SQL | JPQL |
|---|---|---|
| 操作對象 | 資料表 `tickets` | Entity 類別 `Tickets` |
| 欄位名 | `customer_name`（snake_case） | 屬性名 `customerName`（camelCase） |
| 誰翻譯 | 直接送給資料庫 | Hibernate 翻成該資料庫的 SQL |
| 換資料庫 | 可能要改語法 | 通常不用改 |

```sql
-- SQL：認得的是資料表跟欄位
SELECT * FROM tickets WHERE customer_name = '陳小美';
```

```java
// JPQL：認得的是 Entity 類別跟屬性
@Query("SELECT t FROM Tickets t WHERE t.customerName = :name")
```

⚠️ **最常踩的第一個雷**：JPQL 裡寫 `t.customer_name` 會直接報錯。
`@Column(name = "customer_name")` 只負責告訴 Hibernate「這個屬性對到哪個欄位」，
JPQL 這一層完全看不到資料庫欄位名，只認 Java 屬性名。

> **給 Python 背景的類比**：
> 這跟 Django ORM 的 `Ticket.objects.filter(customer_name="陳小美")` 是同一個概念——
> 你寫的是模型的屬性，ORM 幫你翻成 SQL。差別只在 JPQL 長得比較像 SQL 字串。

---

## 二、寫查詢的三種方式（先搞清楚什麼時候用 JPQL）

在 Spring Data JPA 裡，你有三條路可以走：

### 1. 衍生查詢（Derived Query）— 靠方法名稱

```java
List<Tickets> findByCustomerName(String customerName);
List<TicketComments> findByTicketIdOrderByCreatedAtAsc(Integer ticketId);
long countByTicketId(Integer ticketId);
```

不用寫任何 SQL，Spring Data 照方法名稱自動生。**簡單查詢優先用這個。**

缺點：條件一多，方法名會變成
`findByStatusAndChannelAndCategoryOrderByCreatedAtDesc(...)` 這種怪物。

### 2. `@Query` + JPQL — 本文主角

```java
@Query("SELECT a FROM Agents a WHERE a.status = ?1")
List<Agents> findName(String status);
```

條件複雜、要 GROUP BY、要只撈部分欄位的時候用。

### 3. `@Query` + native SQL — 直接寫原生 SQL

```java
@Query(value = "SELECT TOP 1 ticket_no FROM tickets ORDER BY ticket_id DESC",
       nativeQuery = true)
String findLatestTicketNo();
```

要用資料庫專屬語法（SQL Server 的 `TOP`、`ROW_NUMBER()` 等）才用。
注意 native 用的是**資料表名跟欄位名**（`tickets`、`ticket_no`），不是 Entity 屬性名。

**選擇順序建議：衍生查詢 → JPQL → native SQL**，能用簡單的就別用複雜的。

---

## 三、基本結構

```
SELECT   <要什麼>
FROM     <Entity 類別> <別名>
WHERE    <條件>
GROUP BY ...
HAVING   ...
ORDER BY ...
```

**別名（alias）幾乎是必填的**，而且後面全部要靠它：

```java
@Query("SELECT t FROM Tickets t")          // t 就是別名
List<Tickets> findAllTickets();
```

`FROM Tickets t` 的意思是「把 Tickets 這個 Entity 拿出來，暫時叫它 `t`」，
之後 `t.status`、`t.customerName` 都是從這個 `t` 出發。

⚠️ `Tickets` 要用 **Entity 的類別名**（`@Entity` 那個 class 的名字），
不是 `@Table(name = "tickets")` 裡的資料表名。這個專案剛好兩者長得像，容易混。
大小寫有差：`FROM tickets t` 會找不到 Entity。

---

## 四、參數綁定

### 位置參數 `?1` `?2`

```java
@Query("SELECT a FROM Agents a WHERE a.status = ?1")
List<Agents> findByStatus(String status);
```

數字**從 1 開始**（不是 0），對應方法的第 1 個參數。

### 具名參數 `:name`（建議用這個）

```java
@Query("SELECT t FROM Tickets t WHERE t.status = :status AND t.channel = :channel")
List<Tickets> search(@Param("status") String status,
                     @Param("channel") String channel);
```

參數一多，`?1 ?2 ?3` 很容易數錯位置；具名參數看名字就知道對到誰，**重構也不怕**。

`@Param` 在有開 `-parameters` 編譯選項時可以省略（Spring Boot 預設有開），
但建議還是寫上去，意圖比較清楚，也不怕哪天編譯設定變了。

### ⚠️ 絕對不要用字串拼接

```java
// ❌ 這是 SQL Injection，永遠不要這樣寫
@Query("SELECT t FROM Tickets t WHERE t.status = '" + status + "'")
```

參數綁定除了安全，還有效能好處：資料庫可以重用執行計畫。

---

## 五、WHERE 條件

### 比較運算子

| 運算子 | 意思 | 範例 |
|---|---|---|
| `=` | 等於 | `t.status = :s` |
| `<>` 或 `!=` | 不等於 | `t.status <> :s` |
| `>` `>=` `<` `<=` | 大小比較 | `t.createdAt >= :from` |

```java
@Query("SELECT t FROM Tickets t WHERE t.status <> :s AND t.title NOT LIKE :p")
List<Tickets> q(@Param("s") String s, @Param("p") String p);
```

### 邏輯運算子 `AND` / `OR` / `NOT`

⚠️ **混用 AND 跟 OR 一定要加括號**，`AND` 的優先度比 `OR` 高：

```java
// 意思是：(A AND B) OR C —— 通常不是你要的
"WHERE t.status = 'PENDING' AND t.channel = 'PHONE' OR t.category = '帳單問題'"

// 加括號講清楚
"WHERE t.status = 'PENDING' AND (t.channel = 'PHONE' OR t.category = '帳單問題')"
```

### `LIKE` 模糊查詢

`%` 代表任意長度字串，`_` 代表任一個字元。

```java
// 推薦寫法：% 由 CONCAT 在 JPQL 裡加，呼叫端只傳關鍵字
@Query("SELECT t FROM Tickets t WHERE t.title LIKE CONCAT('%', :kw, '%')")
List<Tickets> searchTitle(@Param("kw") String kw);
```

呼叫時：`searchTitle("帳單")`。

另一種寫法是呼叫端自己包 `%`：

```java
@Query("SELECT t FROM Tickets t WHERE t.title LIKE :kw")
List<Tickets> searchTitle(@Param("kw") String kw);
// 呼叫：searchTitle("%帳單%")
```

兩種都能動，但第一種呼叫端比較不容易忘記加 `%`。

> 效能提醒：`LIKE '%xxx%'`（前面有 `%`）**用不到索引**，資料量大會慢。
> `LIKE 'TK-%'`（只有後面有 `%`）才吃得到索引。

### `IN` 清單比對

```java
@Query("SELECT t FROM Tickets t WHERE t.status IN :list")
List<Tickets> findByStatuses(@Param("list") List<String> list);
```

呼叫：`findByStatuses(List.of("RESOLVED", "PENDING"))`

⚠️ 傳空的 List 進去有些資料庫會炸（產生 `IN ()`），呼叫前先擋掉空清單。

### `BETWEEN` 區間

```java
@Query("SELECT t FROM Tickets t WHERE t.createdAt BETWEEN :from AND :to")
List<Tickets> findInRange(@Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to);
```

`BETWEEN` 是**頭尾都含**（closed interval），等同 `>= from AND <= to`。

### `IS NULL` / `IS NOT NULL`

```java
@Query("SELECT t FROM Tickets t WHERE t.assigneeId IS NULL")
List<Tickets> findUnassigned();
```

⚠️ **不能寫 `= NULL`**，`NULL = NULL` 在 SQL 世界裡結果是「不知道」，不是 true。
這點跟 Python 的 `x is None` 概念一樣，一定要用 `IS`。

---

## 六、排序與去重

### `ORDER BY`

```java
@Query("SELECT t FROM Tickets t ORDER BY t.status ASC, t.createdAt DESC")
List<Tickets> findAllSorted();
```

- `ASC` 由小到大（預設，可省略）
- `DESC` 由大到小
- 多欄位用逗號分隔，**由左往右**依序比

### `DISTINCT` 去除重複

```java
@Query("SELECT DISTINCT t.category FROM Tickets t")
List<String> findAllCategories();
```

---

## 七、回傳型別怎麼決定（新手最容易卡的地方）

**規則：`SELECT` 後面寫什麼，回傳型別就要是什麼。**

### 1. 撈整個 Entity → `List<Entity>`

```java
@Query("SELECT t FROM Tickets t")
List<Tickets> q();
```

### 2. 撈單一欄位 → `List<該欄位型別>`

```java
@Query("SELECT DISTINCT t.category FROM Tickets t")
List<String> q();
```

### 3. 撈多個欄位 → `List<Object[]>`

```java
@Query("SELECT t.ticketNo, t.customerName FROM Tickets t")
List<Object[]> q();
```

用起來很難看，每個元素要自己轉型：

```java
for (Object[] row : repo.q()) {
    String ticketNo = (String) row[0];
    String name     = (String) row[1];
}
```

> **Python 對比**：這就像 `cursor.fetchall()` 拿到一堆 tuple，
> 只是 Java 沒有 tuple unpacking，要自己 `row[0]` `row[1]` 加轉型。

### 4. 撈多個欄位 → DTO 投影（推薦）

比 `Object[]` 好用太多。先準備一個有對應建構子的類別：

```java
public class AgentStat {
    private final String status;
    private final long total;

    public AgentStat(String status, long total) {   // 建構子的參數順序、型別要對得上
        this.status = status;
        this.total = total;
    }
    // getter 略
}
```

JPQL 用 `new` + **完整套件路徑**：

```java
@Query("SELECT new com.poz.CustomerService.dto.AgentStat(a.status, COUNT(a)) "
     + "FROM Agents a GROUP BY a.status")
List<AgentStat> countByStatus();
```

⚠️ 三個常見錯誤：
1. **套件路徑一定要寫全**，只寫 `new AgentStat(...)` 會找不到類別
2. **建構子參數型別要完全對得上**，`COUNT()` 回傳的是 `Long`，不能宣告成 `int`
3. 這個 DTO 類別**不要**加 `@Entity`

---

## 八、聚合函數

| 函數 | 意思 | 回傳型別 |
|---|---|---|
| `COUNT(x)` | 筆數 | `Long` |
| `SUM(x)` | 總和 | 視欄位型別 |
| `AVG(x)` | 平均 | `Double` |
| `MAX(x)` / `MIN(x)` | 最大 / 最小 | 同欄位型別 |

```java
@Query("SELECT COUNT(t), MIN(t.createdAt), MAX(t.createdAt) FROM Tickets t")
List<Object[]> stats();
```

⚠️ **`COUNT` 一定回傳 `Long`**，宣告成 `int` 會噴型別轉換錯誤。

### `GROUP BY` + `HAVING`

```java
@Query("SELECT t.status, COUNT(t) FROM Tickets t "
     + "GROUP BY t.status HAVING COUNT(t) > :n")
List<Object[]> countByStatus(@Param("n") long n);
```

**`WHERE` 跟 `HAVING` 的差別（很常搞混）**：

```
WHERE    → 分組「前」先篩掉某些「資料列」
GROUP BY
HAVING   → 分組「後」再篩掉某些「群組」
```

所以 `HAVING` 裡面才可以用 `COUNT()` 這種聚合結果，`WHERE` 裡不行。

⚠️ **`SELECT` 裡沒被聚合的欄位，全部都要出現在 `GROUP BY`**。
上例 `SELECT t.status, COUNT(t)`，`t.status` 沒被聚合，所以必須 `GROUP BY t.status`。

### `ORDER BY` 用聚合結果排序

```java
@Query("SELECT t.category, COUNT(t) FROM Tickets t "
     + "GROUP BY t.category ORDER BY COUNT(t) DESC")
List<Object[]> topCategories();
```

---

## 九、CASE / COALESCE / NULLIF

### `CASE WHEN` — 在查詢裡做 if/else

**簡單型**（拿一個值去比對）：

```java
@Query("SELECT t.ticketNo, CASE t.status "
     + "WHEN 'IN_PROGRESS' THEN '處理中' "
     + "WHEN 'PENDING' THEN '待客戶回覆' "
     + "ELSE '已解決' END FROM Tickets t")
List<Object[]> statusLabel();
```

**搜尋型**（每個 WHEN 各自寫完整條件，比較彈性）：

```java
"SELECT CASE WHEN t.followUpAt < CURRENT_TIMESTAMP THEN '逾期' "
+ "WHEN t.followUpAt IS NULL THEN '無期限' "
+ "ELSE '正常' END FROM Tickets t"
```

### `COALESCE` — 取第一個非 NULL 的值

```java
@Query("SELECT COALESCE(t.assigneeId, '未指派') FROM Tickets t")
List<String> assigneeOrDefault();
```

> **Python 對比**：類似 `x if x is not None else '未指派'`，
> 或 `x or '未指派'`（但 `or` 對 `0`、空字串也會生效，`COALESCE` 只看 NULL）。

### `NULLIF` — 兩者相等就回 NULL

```java
@Query("SELECT NULLIF(t.category, '') FROM Tickets t")
List<String> categoryOrNull();
```

`NULLIF(a, b)`：`a == b` 就回 `NULL`，否則回 `a`。
常用來把「空字串」正規化成 NULL。

---

## 十、內建函數

以下是實測跑過、Hibernate 會翻成 SQL Server 語法的常用函數：

### 字串

| JPQL | 說明 | 實際翻成的 SQL Server 語法 |
|---|---|---|
| `UPPER(s)` | 轉大寫 | `upper(...)` |
| `LOWER(s)` | 轉小寫 | `lower(...)` |
| `LENGTH(s)` | 字串長度 | `len(...)` |
| `TRIM(s)` | 去頭尾空白 | `trim(...)` |
| `CONCAT(a, b)` | 字串相接 | `concat(...)` |
| `SUBSTRING(s, 起, 長)` | 取子字串 | `substring(...)` |

⚠️ **`SUBSTRING` 的起始位置從 1 開始，不是 0**。
`SUBSTRING(t.ticketNo, 1, 2)` 取的是前兩個字（`TK`）。
這跟 Java 的 `String.substring(0, 2)` 跟 Python 的 `s[0:2]` 都不一樣，很容易寫錯。

### 數字

| JPQL | 說明 |
|---|---|
| `ABS(x)` | 絕對值 |
| `MOD(a, b)` | 取餘數（翻成 SQL Server 的 `a % b`） |
| `SQRT(x)` | 平方根 |

### 日期時間

| JPQL | 說明 |
|---|---|
| `CURRENT_TIMESTAMP` | 現在的日期時間 |
| `CURRENT_DATE` | 今天的日期 |
| `CURRENT_TIME` | 現在的時間 |

```java
@Query("SELECT t FROM Tickets t WHERE t.createdAt <= CURRENT_TIMESTAMP")
List<Tickets> notFuture();
```

⚠️ **日期加減（例如「三天前」）JPQL 標準沒有可攜寫法**。
建議在 Java 這邊算好再當參數傳進去，比較單純：

```java
LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
repo.findCreatedAfter(threeDaysAgo);
```

---

## 十一、子查詢

### `IN` + 子查詢

```java
@Query("SELECT t FROM Tickets t WHERE t.assigneeId IN "
     + "(SELECT a.agentId FROM Agents a WHERE a.status = 'ONLINE')")
List<Tickets> ticketsOfOnlineAgents();
```

### `EXISTS` + 子查詢

```java
@Query("SELECT t FROM Tickets t WHERE EXISTS "
     + "(SELECT 1 FROM Agents a WHERE a.agentId = t.assigneeId)")
List<Tickets> withValidAgent();
```

`EXISTS` 只在乎「子查詢有沒有撈到東西」，所以裡面 `SELECT 1` 就好，不用真的撈欄位。

**`IN` 跟 `EXISTS` 怎麼選**：
- 子查詢結果**筆數少** → `IN` 比較直覺
- 子查詢要**參照外層的欄位**（上例的 `t.assigneeId`）→ 用 `EXISTS`

⚠️ 子查詢**只能出現在 `WHERE` 跟 `HAVING`**，JPQL 不支援寫在 `FROM` 裡。

---

## 十二、JOIN

### 這個專案的特殊狀況

一般 JPA 教學的 JOIN 長這樣（Entity 之間有 `@ManyToOne` 關聯）：

```java
"SELECT t FROM Tickets t JOIN t.assignee a WHERE a.status = 'ONLINE'"
//                              ^^^^^^^^^^ 直接走屬性
```

**但這個專案刻意沒做關聯**——`Tickets.assigneeId` 只是個普通的 `String`
（原因寫在 `Tickets.java` 的註解裡：避開 lazy loading 跟 toString 無限遞迴）。

所以要用 **entity join + `ON`** 自己接：

```java
@Query("SELECT t.ticketNo, a.name FROM Tickets t "
     + "JOIN Agents a ON a.agentId = t.assigneeId")
List<Object[]> ticketWithAgentName();
```

實測產生的 SQL：

```sql
select t1_0.ticket_no, a1_0.name
from tickets t1_0
join agents a1_0 on a1_0.agent_id = t1_0.assignee_id
```

### `LEFT JOIN`

```java
@Query("SELECT t.ticketNo, a.name FROM Tickets t "
     + "LEFT JOIN Agents a ON a.agentId = t.assigneeId")
List<Object[]> allTicketsWithAgentName();
```

差別：`JOIN`（inner join）只留**兩邊都對得上**的；
`LEFT JOIN` 會保留左邊全部，右邊沒對到就補 `null`。

以這個專案來說：沒指派客服的工單（`assigneeId` 是 NULL），
用 `JOIN` 會消失，用 `LEFT JOIN` 才會出現（`a.name` 是 null）。

### `JOIN FETCH`（有關聯時才用得到）

當 Entity 之間**有**關聯，`JOIN FETCH` 用來一次把關聯物件也撈進來，避免 N+1 查詢：

```java
"SELECT t FROM Tickets t JOIN FETCH t.assignee"
```

> 這個專案目前沒有關聯屬性，所以用不到。等哪天改成 `@ManyToOne` 再回來看這段。

---

## 十三、分頁

方法多收一個 `Pageable` 參數，回傳 `Page<T>` 就好：

```java
@Query("SELECT t FROM Tickets t WHERE t.status = :status")
Page<Tickets> findByStatus(@Param("status") String status, Pageable pageable);
```

呼叫：

```java
Page<Tickets> page = repo.findByStatus("RESOLVED", PageRequest.of(0, 20));
page.getContent();          // 這一頁的資料 List<Tickets>
page.getTotalElements();    // 總筆數
page.getTotalPages();       // 總頁數
```

⚠️ **頁碼從 0 開始**，`PageRequest.of(0, 20)` 是第一頁。

### `countQuery`

用 `Page` 時 Spring Data 會自動再發一次 count 查詢算總筆數。
複雜查詢（有 JOIN、GROUP BY）它可能猜錯，這時自己指定：

```java
@Query(value = "SELECT t FROM Tickets t WHERE t.status = :status",
       countQuery = "SELECT COUNT(t) FROM Tickets t WHERE t.status = :status")
Page<Tickets> findByStatus(@Param("status") String status, Pageable pageable);
```

如果不需要總筆數（例如無限捲動），回傳 `Slice<T>` 就不會發 count 查詢，比較快。

---

## 十四、UPDATE / DELETE

JPQL 也能改資料，但**規矩比較多**：

```java
@Modifying
@Transactional
@Query("UPDATE Tickets t SET t.status = :status, t.updatedAt = CURRENT_TIMESTAMP "
     + "WHERE t.ticketId = :id")
int updateStatus(@Param("status") String status, @Param("id") Integer id);
```

```java
@Modifying
@Transactional
@Query("DELETE FROM Tickets t WHERE t.status = :status")
int deleteByStatus(@Param("status") String status);
```

### 四個必須注意的點

1. **一定要加 `@Modifying`**
   少了它，Spring Data 會當成查詢去跑，直接報錯。

2. **一定要有交易（`@Transactional`）**
   寫在 Repository 上（像 `AgentsRepository.updateName` 那樣）可以動，
   但**正式的做法是寫在 Service 層**，因為交易邊界應該由業務邏輯決定，
   而不是「一個方法一個交易」。

3. **回傳型別是 `int` 或 `void`**
   `int` 是「影響了幾筆」，不是資料本身。

4. ⚠️ **會繞過 Hibernate 的生命週期**
   這是最容易踩的雷：`@Modifying` 的 UPDATE 是直接發 SQL 給資料庫，
   **`@PreUpdate` 不會被觸發**。

   `Tickets` 靠 `@PreUpdate` 自動維護 `updatedAt`，
   所以走 JPQL UPDATE 的話，`updatedAt` 要**自己在 SQL 裡寫**
   （上面範例的 `t.updatedAt = CURRENT_TIMESTAMP` 就是為了這個）。

   同理，這種 UPDATE 也不會更新一級快取裡已載入的物件。
   同一個交易裡如果之後又讀了同一筆資料，可能拿到舊值。
   需要的話加 `@Modifying(clearAutomatically = true, flushAutomatically = true)`。

> **什麼時候該用 JPQL UPDATE？**
> 「一次改很多筆」才用。改單筆的話，正常做法是
> `findById()` 撈出來 → `setXxx()` 改 → 交易結束自動寫回，
> 這樣 `@PreUpdate` 才會正常運作。

---

## 十五、連帶刪除（cascade）：兩種機制別搞混

「刪掉工單時，底下的留言自動一起刪」有**兩條完全不同的路**可以達成，新手很容易以為只有一種。

| | 資料庫 cascade | JPA cascade |
|---|---|---|
| 寫在哪 | DDL 的 `ON DELETE CASCADE` | `@OneToMany(cascade = CascadeType.REMOVE)` |
| 誰執行 | 資料庫引擎 | Hibernate |
| 發幾條 SQL | **1 條**（刪父表就好） | **1 + N 條**（先撈出 N 筆子資料，一筆一筆刪） |
| 需要關聯映射嗎 | ❌ 不用 | ✅ 一定要 `@ManyToOne` / `@OneToMany` |
| 會觸發 `@PreRemove` 嗎 | ❌ 不會 | ✅ 會 |

### 這個專案走的是資料庫那條

`V1__init_schema.sql` 裡已經設好了：

```sql
ALTER TABLE [dbo].[ticket_comments] ADD CONSTRAINT [FK_ticket_comments_tickets]
    FOREIGN KEY ([ticket_id]) REFERENCES [dbo].[tickets] ([ticket_id])
    ON DELETE CASCADE
```

實測（建一張工單 → 塞三筆留言 → `ticketsRepository.deleteById()`）：

```
刪除前，留言數 = 3
刪除後，留言數 = 0
Hibernate 發出的 delete 語句數量：1
```

那唯一一條是 `delete from tickets where ticket_id = ?`。
**Entity 上沒有任何關聯、沒有任何 cascade 設定，留言就自己消失了。**

所以：**「要自動刪留言，所以得加 `@ManyToOne`」這個推論是反過來的**——
不加關聯反而只發 1 條 SQL；加了 JPA cascade，一張有 50 筆留言的工單會變成 51 條 DELETE。

### 但資料庫 cascade 有三個盲點

它繞過 Hibernate，所以**不會觸發 JPA 的生命週期**。這三種情況要特別小心：

1. **子物件上有 `@PreRemove` 要跑**
   例如刪除前要寫稽核 log、要順便刪掉附加檔案。
   資料庫直接把資料清掉，Hibernate 根本不知道發生過這件事，回呼不會被呼叫。

2. **同一個交易裡先讀過那些子資料** ⚠️ 最容易踩
   留言物件還留在 Hibernate 的一級快取裡，資料庫刪掉了但記憶體裡還在，
   之後在同一個交易裡再讀，會拿到已經不存在的「幽靈資料」。

   ```java
   List<TicketComments> before = commentsRepo.findByTicketIdOrderByCreatedAtAsc(id); // 進了快取
   ticketsRepo.deleteById(id);            // 資料庫連帶刪除了
   // 這時 before 裡的物件、以及快取裡的同一批資料，都還是舊的
   em.flush();
   em.clear();                            // 要自己清掉，後續查詢才會回資料庫拿
   ```

3. **`orphanRemoval` 的語意做不到**
   「把某筆留言從工單的 list 裡移除掉，它就該被刪除」——
   這是「解除關係」而不是「刪除父層」，資料庫 cascade 完全處理不到，只能靠 JPA。

### 決策順序

```
需要連帶刪除？
  → 先看資料庫 FK 有沒有 ON DELETE CASCADE
      有  → 結束，跟關聯映射無關
      沒有 → 再問：刪除時需要跑 Java 邏輯，或需要 orphanRemoval 嗎？
              要  → 加關聯 + JPA cascade
              不要 → 補一條 migration 加 ON DELETE CASCADE 就好
```

### 順帶一提：不是每條 FK 都該設 CASCADE

同一張 `ticket_comments` 上的另一條外鍵就刻意**不設**：

```sql
-- 客服不可隨意刪除（留言要留著當稽核紀錄），所以這條不設 CASCADE
ALTER TABLE [dbo].[ticket_comments] ADD CONSTRAINT [FK_ticket_comments_agents]
    FOREIGN KEY ([agent_id]) REFERENCES [dbo].[agents] ([agent_id])
```

差別在於「擁有關係」：留言**屬於**工單，工單沒了留言就沒有意義；
但留言只是**引用**客服，客服離職不代表他寫過的紀錄該消失。
設 CASCADE 前先問這一句：子資料離開父資料還有沒有獨立存在的價值。

---

## 十六、常見錯誤速查

| 錯誤寫法 | 為什麼錯 | 正確寫法 |
|---|---|---|
| `t.customer_name` | JPQL 只認 Java 屬性名 | `t.customerName` |
| `FROM tickets t` | 要用 Entity 類別名，大小寫有差 | `FROM Tickets t` |
| `SELECT * FROM ...` | JPQL 沒有 `*` | `SELECT t FROM Tickets t` |
| `WHERE t.assigneeId = NULL` | NULL 不能用 `=` 比 | `IS NULL` |
| `?0` | 位置參數從 1 開始 | `?1` |
| `COUNT(t)` 宣告成 `int` | COUNT 回傳 Long | 宣告成 `long` / `Long` |
| `new AgentStat(...)` | DTO 要寫完整套件路徑 | `new com.poz.xxx.AgentStat(...)` |
| UPDATE 沒加 `@Modifying` | 會被當查詢跑 | 加 `@Modifying` + 交易 |
| `SUBSTRING(s, 0, 2)` | 起始位置從 1 開始 | `SUBSTRING(s, 1, 2)` |
| JPQL 裡寫 `TOP 1` | 那是 SQL Server 專屬語法 | 用 `Pageable` 或 `nativeQuery = true` |

### 一個好用的除錯技巧

`application.properties` 裡打開 SQL 輸出，就能看到 JPQL 被翻成什麼 SQL：

```properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

JPQL 寫錯時，Hibernate 的錯誤訊息通常會直接指出是哪個 token 解析失敗，
例如 `Could not resolve attribute 'customer_name' of 'Tickets'`——
看到 `Could not resolve attribute` 就是屬性名寫錯了。

---

## 附錄：本文件怎麼驗證的

本文所有 JPQL 都寫進一個暫時的 Repository 介面，
用 `@SpringBootTest` 連上真的 SQL Server 容器全部執行過一遍（28 條全數通過），
確認語法能被 Hibernate 解析、也能被 SQL Server 執行，之後才刪掉驗證檔。

第十五章的 cascade 行為也是實測的：建一張工單 → 塞三筆留言 →
`ticketsRepository.deleteById()` → 數留言剩幾筆，
並從 Hibernate 的 SQL log 確認只發出 1 條 DELETE。
測試都掛 `@Transactional` 自動回滾，沒有弄髒開發資料庫。

要自己驗證新寫的 JPQL，最省事的方式是在 `JpaTest.java` 裡加個測試方法直接呼叫。

⚠️ **用指令列跑測試時的注意事項**：這個專案跑在 JDK 25 上，
而 JDK 23 之後 javac 預設不再從 classpath 自動執行註解處理器，
所以直接 `./mvnw test` 會因為 Lombok 沒生效而編譯失敗
（一堆 `cannot find symbol: method setXxx`）。要這樣跑：

```bash
./mvnw -Dmaven.compiler.proc=full test
```

（IDEA 裡點綠色三角形不會有這個問題，因為 IDEA 用自己的 Lombok 外掛。）
