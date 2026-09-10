> 📌 本文件僅供個人參考閱讀，AI 寫 Code 時請勿參考此檔案內容。

# JPA 分層整理：Entity → Repository → DTO → Service → Controller

> 本文所有說法都拿這個專案的 `Tickets` 這條線當例子，
> 「原理」的部分（`@PrePersist` 補預設值、`ticketNo` 讀回、一級快取、dirty checking）
> 都寫成測試在 SQL Server（`docker compose up -d` 那台）實際跑過，不是憑印象寫的。
> 驗證方式與實測輸出見文末「附錄」。

---

## 一、先看全貌

**一句話：每一層都在做「換一種資料型態」，而且方向是固定的。**

去程：

```
HTTP JSON 字串
  → Controller  → Request DTO（CreateTicketRequest）
  → Service     → Entity（Tickets）
  → Repository  → JPQL
  → Hibernate   → SQL
  → 資料庫
```

回程剛好倒過來：

```
ResultSet → Entity → Response DTO（TicketListItemResponse） → JSON
```

### 呼叫方向 vs 依賴方向

- **呼叫方向**：Controller → Service → Repository → Entity
- **依賴方向**：外層 import 內層，內層**永遠不 import 外層**

證據就在自己的程式碼裡：`TicketListItemResponse` 有 `import ...entity.Tickets`（DTO 認識 entity），
但 `Tickets.java` 從頭到尾沒有 import 任何 DTO、Service、Controller。

這個單向性就是分層的核心規則。一旦破壞（例如 entity 去 import service），
整包東西就會互相牽扯，改一個地方全部要跟著改。

> **給 Python 背景的類比**：
> 這跟 FastAPI 專案裡 `models.py`（SQLAlchemy）／`schemas.py`（pydantic）／
> `crud.py`／`routers.py` 的分法是同一件事，只是 Java 把它切得更明顯。

---

## 二、Entity —— 一列資料的 Java 樣子

檔案：`entity/Tickets.java`

`@Entity` + `@Table(name = "tickets")` 是告訴 Hibernate：**這個 class 對應那張表**，
每個 `@Column` 對應一個欄位。Entity 本身沒有任何業務行為，就是一個帶標註的資料容器。

```java
@Entity
@Table(name = "tickets")
public class Tickets {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_id")
    private Integer ticketId;

    @Column(name = "customer_name")
    private String customerName;
    // ...
}
```

### 幾個一定要記的標註

- `@Id` —— 主鍵。每個 entity 一定要有，Hibernate 靠它辨認「這是哪一列」。
- `@GeneratedValue(strategy = IDENTITY)` —— 主鍵由資料庫發，**新增時不要自己填**。
- `@Column(name = "...")` —— Java 是 camelCase、資料庫是 snake_case，靠這個對起來。
- `@Generated(event = INSERT)` + `insertable = false, updatable = false` ——
  `ticketNo` 是資料庫的計算欄位，Hibernate INSERT 完會**再 SELECT 一次讀回來**塞進物件。

### 生命週期 callback

```java
@PrePersist
void applyDefaults() { ... }   // INSERT 前，補 createdAt / updatedAt / status

@PreUpdate
void touchUpdatedAt() { ... }  // UPDATE 前，把 updatedAt 換成現在時間
```

重點是**不是你去呼叫它，是 Hibernate 來呼叫你**。
只要那個物件是被 Hibernate 管理的，發 SQL 前就一定會經過這兩個方法。

實測（見附錄）：`Tickets.builder()` 時 `status` 是 `null`，
`save()` 之後變成 `IN_PROGRESS`、`createdAt` 也自動有值、`ticketNo` 拿到 `TK-004003`。

> **給 Python 背景的類比**：
> entity ≈ SQLAlchemy 的 `class Tickets(Base)`，
> `@PrePersist` ≈ SQLAlchemy 的 `before_insert` event listener。

---

## 三、Repository —— 只負責「進出資料庫」

檔案：`repository/TicketsRepository.java`

```java
@Repository
public interface TicketsRepository extends JpaRepository<Tickets, Integer> {
    Optional<Tickets> findByTicketNo(String ticketNo);
}
```

### 新手最大的疑問：它是 interface，我沒寫實作，為什麼能用？

因為**你不必寫，Spring 在啟動時幫你生**。

流程是：Spring Boot 掃到這個 interface `extends JpaRepository`
→ 用**動態代理**在記憶體裡產生一個實作類別
→ 註冊成 bean
→ 你 `@Autowired` 拿到的其實是那個代理物件。

所以「沒有實作卻能跑」不是魔法，是**執行期生成的類別**。

### 方法有三種來源

1. **繼承來的**：`save`、`findById`、`findAll`、`existsById`、`deleteById`、分頁版 `findAll(Pageable)`。
2. **方法名衍生查詢**：`findByTicketNo` —— Spring Data 去**解析方法名字**
   （`find` + `By` + 屬性名），自動生出 `where ticket_no = ?`。
   屬性名打錯，**啟動時就會炸**，不會拖到執行期才發現。
3. **自己寫 `@Query`**：名字表達不出來的複雜條件。

### `@Query` 裡寫的是 JPQL，不是 SQL

```java
@Query("""
        select t
        from Tickets t
        where (:ticketNo is null or t.ticketNo = :ticketNo)
          and (:status is null or t.status = :status)
        """)
Page<Tickets> search(@Param("ticketNo") String ticketNo, ...);
```

- `from Tickets t` —— `Tickets` 是 **class 名**，不是資料表 `tickets`
- `t.ticketNo` —— 是 **Java 屬性名**，不是欄位 `ticket_no`（寫成 `t.ticket_no` 會直接報錯）
- Hibernate 再把 JPQL 翻成 SQL Server 認得的 SQL

（JPQL 語法細節另見 `jpql.md`。）

### 這一層的紀律

Repository **只做資料進出，不做判斷**。
「page 不能小於 1」「status 只能是那三個」這種事一律不寫在這裡，全部往 Service 放。

---

## 四、DTO —— 對外的形狀

目錄：`dto/ticket/`

分成兩種，本專案分得很清楚：

- **Request DTO**：`CreateTicketRequest`、`ChangeStatusRequest`、`AddCommentRequest`
  —— 前端**送進來**的形狀，帶驗證標註
- **Response DTO**：`TicketListItemResponse`、`TicketDetailResponse`、`TicketPageResponse`
  —— **回出去**的形狀，配一個 `static from(entity)` 做轉換

### 為什麼不直接把 Entity 丟給前端？

四個實際理由：

1. **會漏內部欄位** —— `ticketId` 是內部流水號，不該給前端看到。
2. **改欄位就等於改 API** —— entity 跟資料表綁死，改一個欄位名，前端跟著壞。
3. **序列化會出事** —— 有關聯（`@OneToMany` 之類）時，Jackson 轉 JSON 會踩到
   lazy loading 例外，或兩個 entity 互相引用變成無限循環。
4. **進去跟出來的形狀本來就不同** —— `CreateTicketRequest` 沒有 `ticketNo`
   （後端才知道），`TicketListItemResponse` 有。這正是它們該分開的證據。

### 為什麼用 `record`？

```java
public record TicketListItemResponse(
        String ticketNo,
        String title,
        // ...
) {
    public static TicketListItemResponse from(Tickets ticket) {
        return new TicketListItemResponse(
                ticket.getTicketNo(),
                ticket.getTitle(),
                // ...
        );
    }
}
```

`record` 天生 immutable、自動有全參數建構子、自動有存取方法
（注意是 `title()` 不是 `getTitle()`）、自動有 `equals` / `hashCode` / `toString`。
DTO 就只是「一包不會再變的資料」，正好合用。

轉換寫成 `static from(...)` 放在 DTO 自己身上，是為了讓 Service 只寫一行
`TicketPageResponse.from(result)`，不必在每個地方重複搬欄位。

> **給 Python 背景的類比**：
> Request/Response DTO ≈ pydantic 的 model，`record` ≈ `@dataclass(frozen=True)`。

---

## 五、Service —— 業務規則與交易邊界

檔案：`service/TicketService.java`、`service/TicketDetailService.java`

Controller 不做判斷、Repository 不做判斷，**所有「業務上該不該」都在這層**。

看 `search()` 做了哪些事：

- page / size 範圍檢查，超出就丟 `ApiException.badRequest`
- status 白名單檢查（不擋的話，打錯的狀態會安靜地回 0 筆，看不出是拼錯還是真的沒資料）
- 把 1-based 頁碼換成 `PageRequest` 的 0-based
- 決定預設排序 `DEFAULT_SORT`
- 最後 `TicketPageResponse.from(result)` 轉成 DTO

看 `create()` 做了哪些事：

- `resolveAssignee` 決定負責人（沒指定就是自己，指定了就檢查客服存不存在）
- 存工單
- 順手寫 2～4 筆處理記錄

### `@Transactional` 的原理

跟 Repository 一樣是 **proxy**：Spring 包一層代理物件，
進方法前開交易 → 正常回傳就 commit → 丟 RuntimeException 就 rollback。

所以 `create()` 裡「存工單」跟「寫記錄」要嘛全部成功、要嘛全部不留，
不會出現「工單存好了但記錄沒寫進去」。

`@Transactional(readOnly = true)` 用在純查詢，Hibernate 會跳過 dirty checking，比較省。

### proxy 的副作用（新手常中招）

同一個 class 內部用 `this.otherMethod()` 呼叫自己的方法，**不會經過代理**，
`@Transactional` 就失效了。

本專案目前是**跨 Service 呼叫**（`ticketDetailService.writeComment(...)`），
走的是注入進來的 bean，代理有生效；`@Transactional` 預設傳播行為是 **REQUIRED**，
外層已經有交易就直接沿用，不會另開一個。

---

## 六、Controller —— 只翻譯 HTTP

檔案：`controller/TicketController.java`

它只做三件事：**接參數 → 呼叫 Service → 回傳物件**。

```java
@GetMapping
public TicketPageResponse search(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "1") int page, ...) {
    return ticketService.search(..., page, size);
}

@PostMapping
public TicketListItemResponse create(@Valid @RequestBody CreateTicketRequest request) {
    return ticketService.create(request);
}
```

- `@RequestParam` 從 query string 拿值，`@PathVariable` 從路徑拿，
  `@RequestBody` 讓 **Jackson** 把 JSON 反序列化成 DTO
- `@Valid` 觸發 DTO 上那些 `@NotBlank` / `@Size` / `@Pattern`，
  不過就丟例外，由 `GlobalExceptionHandler` 統一轉成 400（見 `error-handling.md`）
- 回傳值不必自己轉 JSON —— `@RestController` 會讓 Jackson 自動序列化

Controller 裡**不該出現** if 判斷業務規則、不該出現 repository、不該出現 entity。

---

## 七、拿 `GET /api/tickets?status=PENDING&page=2` 走一次

1. Spring 依路徑找到 `TicketController.search`，
   把 `status` 轉成 String、`page` 轉成 int，沒帶的參數是 `null`。
2. 呼叫 `ticketService.search(...)`，`@Transactional(readOnly = true)` 開一個唯讀交易。
3. Service 檢查 page / size / status，組出 `PageRequest.of(1, 10, DEFAULT_SORT)`
   （注意：傳進來的 2 減 1 變成 1）。
4. `ticketsRepository.search(...)` → 代理實作 → Hibernate 把 JPQL 加上
   `order by` 與分頁語法翻成 SQL Server 的 SQL；另外**再發一次 count 查詢**算總筆數。
5. 每一列 ResultSet 被組成一個 `Tickets` 物件，包成 `Page<Tickets>`。
6. `TicketPageResponse.from(result)` 把每個 entity 轉成 `TicketListItemResponse`，
   順便把頁碼從 0-based 換回 1-based。
7. 交易結束，Jackson 把 record 轉成 JSON 回給前端。

建立工單（`POST /api/tickets`）差別在於：多了 `@Valid` 驗證，
而且交易是可寫的，`create()` 裡的所有寫入屬於同一個交易。

---

## 八、Persistence Context 與 dirty checking（最容易困惑的地方）

看 `TicketDetailService.changeStatus()`：

```java
Tickets ticket = findTicket(ticketNo);   // 從 DB 撈出來
ticket.setStatus(status);                // 只改記憶體
// 沒有呼叫 save()，但資料庫真的會更新
```

**這不是漏寫 `save()`。**

交易期間有一個 **Persistence Context**（可以想成「這次交易的暫存區」，
也常被叫做一級快取）。從 repository 撈出來的 entity 是 **managed 狀態**，
Hibernate 記得它剛撈出來時長什麼樣（快照）。

交易 commit 前會做 **dirty checking**：比對現在的欄位跟原本的快照，
有差就自動發 UPDATE（也就是這時才觸發 `@PreUpdate`）。

實測（見附錄）：撈出來只呼叫 `setStatus("PENDING")`、完全不碰 `save()`，
`flush()` 之後用原生 SQL 直接查資料庫，`status` 已經是 `PENDING`。

### 那 `create()` 裡的 `save()` 為什麼要寫？

因為 entity 有三種狀態：

- **transient** —— 剛 `new` 或 `builder()` 出來，Hibernate 從沒見過它
- **managed** —— 在 Persistence Context 裡，被追蹤中
- **detached** —— 交易結束後，物件還在但沒人追蹤了

`Tickets.builder()...build()` 出來的是 transient，
要靠 `save()` 把它**納入管理**並發出 INSERT。
而 `findByTicketNo` 撈出來的本來就是 managed，改了就自動更新，不必再 `save()`。

一句話記法：**「新的要 save，撈出來的不用」**。

### 順帶一個好處

同一個交易裡對同一個主鍵 `findById` 兩次，第二次直接從 Persistence Context 拿，
**不會再打資料庫**，而且拿到的是**同一個物件**（`==` 成立）。實測確認為 `true`。

---

## 九、常見疑問速查

**Q：一個新功能要從哪一層開始寫？**
由內往外：先確定資料表與 entity → repository 加查詢 → Service 寫規則 →
定義 DTO 形狀 → Controller 開端點。本專案的 commit 順序（前置 → API）就是這樣走的。

**Q：DTO 轉換要寫在哪？**
放在 Response DTO 自己身上的 `static from(entity)`。Service 只呼叫一行，不重複搬欄位。

**Q：驗證要寫在 DTO 還是 Service？**
「格式」寫 DTO（`@NotBlank`、`@Size`、`@Pattern`，由 `@Valid` 觸發）；
「業務規則」寫 Service（例如 status 轉換合不合法、客服代號存不存在）。

**Q：Service 可以互相呼叫嗎？**
可以，本專案 `TicketService.create()` 就呼叫 `TicketDetailService.writeComment()`。
注意是透過注入的 bean 呼叫，交易才會正確傳遞。

**Q：Controller 可以直接用 Repository 嗎？**
技術上可以，但不要。交易邊界會消失，業務規則也會散到 Controller 裡。

---

## 附錄：本文的驗證方式

上面「原理」的部分，是寫成一支暫時的 `@SpringBootTest` 測試、
連 `docker compose up -d` 那台 SQL Server 實際跑過的（`@Transactional` 跑完自動回滾，
不會留髒資料）。三個測試全數通過，實測輸出：

```
[DOCS] status=IN_PROGRESS createdAt=2026-09-10T11:09:53 ticketNo=TK-004003 ticketId=4003
[DOCS] sameInstance=true
[DOCS] dbStatus=PENDING
```

分別驗證了：

1. **`@PrePersist` 與計算欄位** —— `builder()` 出來時 `status` / `createdAt` / `ticketNo`
   全是 null，`save()` + `flush()` 之後三個都有值，`ticketNo` 是 `TK-` 加六位數字。
2. **一級快取** —— 同交易內 `findById` 兩次，`a == b` 為 `true`（同一個物件實例）。
3. **dirty checking** —— 只呼叫 setter、不呼叫 `save()`，
   `flush()` 後用原生 SQL 查資料庫，`status` 確實已變成 `PENDING`。

想自己再跑一次，就在 `src/test/java/com/poz/CustomerService/` 下開一支測試，
掛上 `@SpringBootTest` 與 `@Transactional`，注入 `TicketsRepository` 與
`@PersistenceContext EntityManager`，用 `em.flush()` / `em.clear()` 控制時機即可。
