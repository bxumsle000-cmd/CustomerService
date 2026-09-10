> 📌 本文件僅供個人參考閱讀，AI 寫 Code 時請勿參考此檔案內容。

# Docker 環境啟動流程與原理

> 本文對照的是專案根目錄的 `docker-compose.yml`、`Dockerfile`
> 與 `src/main/resources/application.properties`。
> 內容重點在「為什麼要這樣寫」，指令怎麼下請看那兩個檔案開頭的註解。

---

## 一、這套環境由哪些東西組成

`docker-compose.yml` 定義了三個服務，但**平常只會跑到前兩個**：

- **`db`** — SQL Server 2022 容器，長期存活的資料庫本體。
- **`db-init`** — 一次性容器，只負責「建立 `customer_service` 這個資料庫」，
  做完就結束（exit 0），不會一直留著。
- **`app`** — Spring Boot 應用程式容器，掛了 `profiles: ["full"]`，
  預設不啟動；平常開發是在 IntelliJ 按 Run。

另外還有兩樣不是「服務」但很關鍵的東西：

- **具名 volume `mssql-data`** — 資料庫檔案真正存放的地方。
- **預設網路 `customerservice_default`** — compose 自動建立，
  三個容器都在裡面，彼此可以用「服務名稱」當主機名稱互相連線。

> **給 Python 背景的類比**：
> compose 之於容器，有點像 `docker run` 指令的 `requirements.txt`——
> 把一堆本來要手打的參數（埠號、環境變數、掛載、依賴順序）寫成宣告式設定檔，
> 之後一句 `docker compose up -d` 就照著這份檔案把整組環境重建出來。

驗證方式（不用開 Docker Desktop 也能跑）：

```bash
docker compose config --services   # 印出 db、db-init（app 不在，因為掛了 profile）
docker compose config --profiles   # 印出 full
```

---

## 二、`docker compose up -d` 到底發生了什麼

以「全新的電腦，第一次啟動」為例，依序是這幾步：

**1. 建立網路與 volume**
compose 先建 `customerservice_default` 網路，以及 `customerservice_mssql-data` volume。
volume 名稱前面那段專案名前綴是 compose 自動加的，所以就算別的專案也叫 `mssql-data`，
兩邊也不會互相污染。

**2. 拉 image、建立容器**
本機沒有 `mcr.microsoft.com/mssql/server:2022-latest` 就會去下載（約 1.5GB，
第一次會等比較久）。`db` 跟 `db-init` 用的是**同一個 image**——
因為 `db-init` 需要的只是那顆 image 裡附的 `sqlcmd` 工具，不需要另外找一個 client image。

**3. 啟動 `db`，並開始輪詢 healthcheck**

```yaml
healthcheck:
  test: ["CMD-SHELL", "/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P \"$${MSSQL_SA_PASSWORD}\" -C -Q 'SELECT 1' || exit 1"]
  interval: 5s
  timeout: 5s
  retries: 30
  start_period: 20s
```

意思是：容器起來 20 秒後（`start_period`）開始，每 5 秒進容器內部下一次
`SELECT 1`，成功就把狀態標成 `healthy`，連續失敗 30 次才判定 `unhealthy`。

這裡有兩個容易看不懂的小地方：

- **`$$` 是跳脫**。compose 自己會做變數展開，寫 `$${...}` 是告訴 compose
  「這個錢字號不要動，原封不動交給容器裡的 shell」，
  所以真正拿到 `$MSSQL_SA_PASSWORD` 的是容器內的 bash。
- **`-C`** 是 sqlcmd 18 的「信任伺服器憑證」。容器用的是自簽憑證，
  沒有這個參數會直接被 TLS 擋下來。

**4. `db-init` 等到 healthy 才動作**

```yaml
depends_on:
  db:
    condition: service_healthy
```

條件成立後執行：

```sql
IF DB_ID('customer_service') IS NULL CREATE DATABASE customer_service;
```

注意 `-S db`：這裡連的是**服務名稱** `db`，由 compose 網路的內建 DNS 解析成該容器的 IP。
跑完 sqlcmd 就結束，容器狀態變成 `Exited (0)`，這是正常的，不是壞掉。

**5.（只有 `--profile full` 才有這步）`app` 啟動**

```yaml
depends_on:
  db-init:
    condition: service_completed_successfully
```

等 `db-init` 正常結束才啟動 app，再由 Flyway 建資料表（見第五節）。

---

## 三、為什麼非要多一個 `db-init` 不可

這是整份設定裡最需要理解的一段，關鍵在兩件事的分工：

- **Flyway 建得了「資料表」，建不了「資料庫」本身。**
  Flyway 是拿 `spring.datasource.url` 去連線的，而那串 URL 裡已經寫死
  `databaseName=customer_service`。資料庫不存在的話，Flyway 連線就失敗了，
  根本輪不到它執行 migration。這是雞生蛋的問題。
- **SQL Server 官方 image 沒有「自動建庫」的環境變數。**
  MySQL 有 `MYSQL_DATABASE`、Postgres 有 `POSTGRES_DB`，設了就會在初始化時幫你建好；
  SQL Server 的官方 image 沒有對應的東西，所以這一步得自己補。

`db-init` 補的就是這個洞。它寫成 `IF DB_ID(...) IS NULL` 是刻意的——
每次 `docker compose up` 都會再跑一次，寫成冪等（idempotent）的話重跑無害，
不會因為資料庫已存在就報錯中斷。

---

## 四、`depends_on` 的三種條件差在哪

新手最常踩的雷是：**`depends_on` 只寫服務名稱時，只保證「容器被啟動」，
不保證「裡面的服務可以用了」**。SQL Server 容器起來到真的能接受連線大概要十幾秒，
這段空窗期 app 若直接連就會噴 connection refused。

這份設定用到的三種條件：

- `service_started`（預設）— 容器起來就算數，最不可靠。
- `service_healthy` — 等 healthcheck 通過。`db-init` 等 `db` 用這個。
- `service_completed_successfully` — 等對方跑完且 exit code 為 0。
  `app` 等 `db-init` 用這個，因為 `db-init` 本來就是跑完就結束的一次性容器。

所以 healthcheck 不是裝飾用的，它是 `service_healthy` 這個條件的判斷依據；
沒有 healthcheck，`service_healthy` 根本無從成立。

---

## 五、資料表是誰建的：Flyway 接手的部分

資料庫（database）由 `db-init` 建立，**資料表（table）則完全由 Flyway 負責**。
app 一啟動，Flyway 會：

1. 連上 `customer_service`，檢查有沒有 `flyway_schema_history` 這張紀錄表，沒有就建。
2. 掃 `classpath:db/migration` 底下的檔案，依版本號排序。
3. 比對紀錄表，只執行「還沒跑過」的版本。

目前有 V1～V5，建出 `agents`、`tickets`、`ticket_comments`、`follow_ups` 四張表。

配套的設定在 `application.properties`：

- `spring.jpa.hibernate.ddl-auto=none` — 結構一律由 Flyway 管，
  不讓 Hibernate 自作主張改表。
- `spring.flyway.baseline-on-migrate=false` — 這個專案從第一天就用 Flyway，
  資料庫是全新的空庫，不需要 baseline。

⚠️ **已經跑過的 migration 檔案不可再修改**。Flyway 會對每支檔案存 checksum，
改一個空白都會讓下次啟動噴 `Migration checksum mismatch`。要改結構請新增 `V6__xxx.sql`。

這也是為什麼「砍掉重來」可以拿來驗收：

```bash
docker compose down -v   # -v 會連 volume 一起刪，資料全沒
docker compose up -d     # 資料庫重新建立（空的）
# 然後在 IDEA 按 Run → Flyway 把四張表全部長回來
```

---

## 六、資料為什麼不會隨容器消失

```yaml
volumes:
  - mssql-data:/var/opt/mssql
```

容器本身是「用完可丟」的，刪掉容器等於刪掉它的檔案系統。
把 SQL Server 存資料的目錄 `/var/opt/mssql` 掛到具名 volume 之後，
資料實際上住在 volume 裡，容器只是掛上去用。所以：

- `docker compose stop` — 容器停住，資料在。
- `docker compose down` — 容器刪掉，**volume 還在**，`up` 回來資料原封不動。
- `docker compose down -v` — 連 volume 一起刪，**資料真的沒了**。

---

## 七、主機名稱與埠號：`db:1433` 還是 `localhost:14334`

這兩串連線字串連到的是**同一個資料庫**，差別只在「從哪裡連」：

- **從 IntelliJ（跑在你的 Windows 上）連** → `localhost:14334`
  走的是 compose 的埠映射 `"14334:1433"`：左邊是你電腦的埠，右邊是容器內的埠。
  用 14334 是為了避開本機安裝的 SQL Server（1433）和 SlotMachineGame 的容器（14333）。
- **從 `app` 容器連** → `db:1433`
  容器之間走 compose 內部網路，服務名稱就是主機名稱，用的是容器內的原始埠 1433，
  跟埠映射無關。

⚠️ 在容器裡寫 `localhost` 指的是**容器自己**，不是你的電腦，這樣一定連不到資料庫。
（真的需要從容器連回主機時，用 `host.docker.internal`。）

---

## 八、兩種執行模式與設定的覆蓋順序

- **平常開發**：`docker compose up -d` 只起 `db` + `db-init`，
  app 在 IntelliJ 跑，開 http://localhost:8080 。
- **完整驗收**：`docker compose --profile full up -d --build`，
  app 也跑在容器裡，開 http://localhost:8081 。

`profiles: ["full"]` 的效果是「有掛 profile 的服務，預設不會被 `up` 啟動，
必須明確指定才會跑」。埠映射寫 `"8081:8080"`，容器內還是 8080，
只是對外換成 8081，這樣兩種模式可以同時開著不打架。

app 跑在容器裡時，compose 餵了三個環境變數：

```yaml
SPRING_DATASOURCE_URL: "jdbc:sqlserver://db:1433;databaseName=customer_service;..."
SPRING_DATASOURCE_USERNAME: "sa"
SPRING_DATASOURCE_PASSWORD: "Cs_Ticket_2648"
```

Spring Boot 的外部化設定有優先序：**環境變數優先於 `application.properties`**。
而 `SPRING_DATASOURCE_URL` 這種全大寫底線的寫法，會被對應回
`spring.datasource.url`（Spring 的 relaxed binding）。
所以同一份程式碼、同一個 jar，在 IDEA 跑會連 `localhost:14334`，
在容器裡跑會自動換成 `db:1433`，不用改任何檔案。

---

## 九、Dockerfile：多階段建置在做什麼

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build   # 第一階段：有 Maven + JDK，負責編譯
...
FROM eclipse-temurin:21-jre-alpine           # 第二階段：只有 JRE，負責執行
COPY --from=build /app/target/*.jar app.jar
```

兩個重點：

- **編譯環境不必進到最終 image**。Maven 跟完整 JDK 只有 build 階段用得到，
  最後那顆 image 只留一個 jar 加輕量 JRE，體積小很多。
- **`COPY pom.xml` 跟 `COPY src` 要分兩次**，中間夾一個 `dependency:go-offline`。
  Docker 是一行指令一層 cache，只要 `pom.xml` 沒變，下載依賴那層就直接吃 cache；
  改 Java 程式碼只會讓 `COPY src` 之後的層失效，重建快非常多。
  順序寫反（先 COPY src）的話，改一個字就要重載全部依賴。

---

## 十、卡住時先看哪裡

- **app 連不到 DB** → `docker compose logs db`。
  最常見是 SA 密碼不符合複雜度要求（大小寫 + 數字/符號，至少 8 碼），
  這種情況 db 容器會反覆重啟。
- **`db-init` 顯示 Exited (0)** → 這是正常的，它本來就跑完就結束。
  要確認有沒有做事，看 `docker compose logs db-init`。
- **埠號被占用** → 改 `ports` 左邊那個數字即可，右邊不要動。
- **Flyway 噴 checksum mismatch** → 有人改了已經跑過的 migration 檔案，
  把它改回去，或整組 `down -v` 重來。
