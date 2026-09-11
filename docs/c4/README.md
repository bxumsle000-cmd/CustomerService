# C4 Model

依 C4 model 由外而內三層描述本專案，`.png` 放在這一層，`.puml` 原始檔放在 `src/`。

| 層級 | 圖檔 | 內容 |
|---|---|---|
| Level 1 System Context | `c4-level1-context.png` | 誰在用系統：客戶、客服人員、開發者 |
| Level 2 Container | `c4-level2-container.png` | 系統由哪些容器組成：SPA、Spring Boot REST API、SQL Server |
| Level 3 Component | `c4-level3-component.png` | 後端 API 內部：Controller / Service / Repository / 共用元件 |

Level 3 全圖線太多，另外每個 Service 各拆一張，只畫「這支 Service 的功能、進出的 DTO、用到的東西、會丟的錯」：

| Service | 圖檔 | 負責的功能 |
|---|---|---|
| AgentService | `c4-service-agent.png` | 登入、查自己、客服清單、工作狀態、通話中切換 |
| TicketService | `c4-service-ticket.png` | 工單列表（篩選＋分頁）、建立工單 |
| TicketDetailService | `c4-service-ticket-detail.png` | 工單詳情、改狀態（狀態機）、轉派、處理記錄 |
| CalendarService | `c4-service-calendar.png` | 回電行事曆：月檢視、新增／改期／取消 |

## 目前狀態（畫圖時的真實情況）

- 前端 `index.html` 仍使用瀏覽器內的模擬資料，**尚未串接後端 API**，圖上以「規劃中」標示。
- 後端 API 目前由開發者透過 Swagger UI 呼叫測試。
- `CurrentAgentProvider` 寫死 `CSC00001`，之後接 JWT 只需改這一支。

## 重新產圖

原始檔用 [C4-PlantUML](https://github.com/plantuml-stdlib/C4-PlantUML)（PlantUML 內建的 `<C4/...>` stdlib），
沒裝 Graphviz 也能跑（`!pragma layout smetana`）。

```powershell
# 下載 plantuml.jar：https://github.com/plantuml/plantuml/releases
java -Dfile.encoding=UTF-8 -jar plantuml.jar -charset UTF-8 -tpng -o .. docs/c4/src/*.puml
```

改完程式碼結構（新增 Controller / Service / Repository）記得同步更新 `src/*.puml` 再重跑。
