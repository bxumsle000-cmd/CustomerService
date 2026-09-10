> 📌 本文件僅供個人參考閱讀，AI 寫 Code 時請勿參考此檔案內容。

# pom.xml 相依套件整理

> 每個套件後面標的版本，是實際跑 `./mvnw -o dependency:list` 解析出來的結果，
> 不是憑印象寫的。驗證方式見文末「附錄」。

---

## 一、專案座標

- `groupId`：`com.poz`
- `artifactId`：`CustomerService`
- `version`：`1`

三個合起來就是這個專案在 Maven 世界裡的唯一名字（`com.poz:CustomerService:1`）。
每一個相依套件也都是用同樣的三段式在指名。

用 Python 對比：`groupId:artifactId` 大概等於 PyPI 上的套件名，`version` 等於 `==1.2.3` 的那個版本。

---

## 二、繼承 spring-boot-starter-parent

- `org.springframework.boot:spring-boot-starter-parent:4.1.0`
- `<relativePath/>`（留空）→ 叫 Maven 別去上層資料夾找 parent，直接去遠端倉庫抓。

parent 的作用是幫你**管好版本**：它內含一份 `dependencyManagement` 清單，
把 Spring 生態圈上百個套件的相容版本都先訂好。
所以下面大部分的 `<dependency>` 才可以不寫 `<version>`。

---

## 三、properties（編譯設定）

- `project.build.sourceEncoding` = `UTF-8`：原始碼檔案用 UTF-8 讀。中文註解不會變亂碼。
- `java.version` = `21`
- `maven.compiler.source` = `21`：語法可以用到 Java 21 的功能。
- `maven.compiler.target` = `21`：產出的 class 檔要能在 Java 21 上跑。

---

## 四、dependencies（相依套件）

### Web 層

- **`spring-boot-starter-web`**（4.1.0）
  Spring MVC 起手包。`@RestController`、`@GetMapping`、JSON 轉換都靠它。
  順便把內嵌 Tomcat（`tomcat-embed-core` 11.0.22）拉進來，所以這個專案不用另外裝 Tomcat，
  跑 `main()` 就自己起一台 web server。

### 資料庫連線

- **`com.microsoft.sqlserver:mssql-jdbc`**（13.4.0.jre11）
  SQL Server 的 JDBC 驅動。`application.properties` 裡那個
  `spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver`
  就是這包提供的類別。

- **`spring-boot-starter-jdbc`**（4.1.0）
  JDBC 基礎建設：`DataSource`、`JdbcTemplate`、交易管理。
  連線池 HikariCP（7.0.2）是從這裡被拉進來的，
  對應 `application.properties` 裡那組 `spring.datasource.hikari.*` 設定。

- **`spring-boot-starter-data-jpa`**（4.1.0）
  JPA 起手包，一次把三樣東西拉齊：
  - `jakarta.persistence-api`（3.2.0）→ 規格（`@Entity`、`@Id` 這些註解的定義）
  - `hibernate-core`（7.4.1.Final）→ 實作（真正把 Entity 翻成 SQL 的人）
  - `spring-data-jpa`（4.1.0）→ `JpaRepository` 那套自動生成查詢的機制

  注意它本身就已經含 `spring-boot-starter-jdbc` 了，
  上面那條是另外明寫出來的，屬於重複但無害（Maven 會自己去重）。

### Flyway 資料庫版本控管

- **`spring-boot-flyway`**（4.1.0）
- **`flyway-core`**（12.4.0）
- **`flyway-sqlserver`**（12.4.0）

三個要一起加。原因寫在 pom 的註解裡：
Spring Boot 4.x 把自動組態拆成獨立模組，**只加 `flyway-core` 不會自動啟用**，
必須一併加 `spring-boot-flyway`，啟動時才會自己跑 `db/migration` 底下的 migration。
`flyway-sqlserver` 則是 SQL Server 專用的方言支援。

### API 文件

- **`org.springdoc:springdoc-openapi-starter-webmvc-ui`**（3.0.3，**有明寫版本**）
  掃描 Controller 自動產生 OpenAPI 規格，並提供 Swagger UI 網頁。
  順帶拉進 `swagger-ui`（5.32.2）這個 webjar，也就是那個網頁介面本身。

  這包要自己寫版本，因為它不是 Spring 官方出品，parent 的清單裡沒有它。

### 參數驗證

- **`spring-boot-starter-validation`**（4.1.0）
  DTO 上寫的 `@NotBlank` / `@Size` / `@Pattern` 靠它生效。
  裡面同樣是「規格 + 實作」兩件事：
  - `jakarta.validation-api`（3.1.1）→ 註解的定義
  - `hibernate-validator`（9.1.0.Final）→ 真正做檢查的實作

  重點（pom 註解也有寫）：**光有註解不會生效**，
  Controller 的參數還要加 `@Valid` 才會真的觸發檢查。

### 密碼雜湊

- **`org.springframework.security:spring-security-crypto`**（7.1.0）
  只拿 BCrypt 來雜湊密碼用。
  這是 Spring Security 拆出來的小包，**不含**登入流程、過濾器鏈那一整套，
  所以加了它不會突然被要求登入。

### 測試

- **`spring-boot-starter-test`**（4.1.0，`scope=test`）
  測試工具大禮包，主要有：
  - JUnit Jupiter（6.0.3）→ 測試框架本體
  - Mockito（5.23.0）→ 假物件
  - AssertJ（3.27.7）→ `assertThat(...)` 這種流暢斷言
  - `spring-test`（7.0.8）→ 起 Spring 容器來跑整合測試

  `scope=test` 表示只有測試時看得到，打包成 jar 時不會被塞進去。

### 開發輔助

- **`org.projectlombok:lombok`**（1.18.46，`scope=provided`）
  用 `@Getter` / `@Builder` 這些註解，在**編譯期**自動生成 getter、建構子等樣板程式碼。
  `scope=provided` 表示編譯時要，執行時不需要（因為程式碼已經生成進 class 檔了）。

---

## 五、build / plugins

- **`spring-boot-maven-plugin`**
  把專案打包成「可執行 jar」（fat jar，所有相依套件都包進去，`java -jar` 直接能跑），
  也是 `./mvnw spring-boot:run` 的來源。

- **`maven-compiler-plugin`**
  額外設了 `annotationProcessorPaths` 指向 Lombok。
  意思是明確告訴編譯器「請跑 Lombok 的註解處理器」。
  不寫的話，某些環境下 Lombok 會沒作用，然後編譯時報一堆「找不到 getXxx() 方法」。

---

## 六、為什麼大部分套件不用寫 version？

這是初學最容易卡住的點，整理成一句話：

**版本號寫在 parent 的 `dependencyManagement` 裡，子專案只要「點名要哪個套件」就好。**

好處是整組 Spring 相關套件的版本互相相容，不會你抓 Hibernate 7、我抓 Spring 6 打架。
例外只有 `springdoc-openapi-starter-webmvc-ui`，因為它是第三方套件、parent 管不到，
所以必須自己寫 `<version>3.0.3</version>`。

判斷方法：**寫了 `<version>` 的，就是 parent 沒管的套件。**

---

## 七、幾個由 starter 間接帶進來、但很常遇到的套件

這些沒有出現在 pom.xml 裡，但實際都在 classpath 上，看到它們的名字不用慌：

- `jackson-databind`（2.21.4）+ `tools.jackson`（3.1.4）→ JSON 與物件互轉
- `HikariCP`（7.0.2）→ 連線池
- `logback-classic`（1.5.34）+ `slf4j-api`（2.0.18）→ 記錄 log
- `snakeyaml`（2.6）→ 讀 `.yml` 設定檔（所以要換成 yml 不用另外加套件）
- `spring-core` / `spring-beans` / `spring-context`（7.0.8）→ Spring 框架本體

---

## 附錄：驗證方式

版本號是實際跑出來的，不是查文件抄的：

```bash
./mvnw -o dependency:list
```

`-o` 是 offline 模式，用本機 `~/.m2` 已下載的內容解析，不連網路。
輸出會列出所有直接與間接相依，格式是 `groupId:artifactId:jar:版本:scope`。

想看「誰把誰拉進來的」樹狀關係，用這個：

```bash
./mvnw -o dependency:tree
```
