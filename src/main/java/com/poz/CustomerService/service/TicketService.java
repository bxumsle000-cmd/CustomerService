package com.poz.CustomerService.service;

import com.poz.CustomerService.dto.CreateTicketRequest;
import com.poz.CustomerService.dto.TicketListItemResponse;
import com.poz.CustomerService.dto.TicketPageResponse;
import com.poz.CustomerService.entity.TicketComments;
import com.poz.CustomerService.entity.Tickets;
import com.poz.CustomerService.exception.ApiException;
import com.poz.CustomerService.repository.AgentsRepository;
import com.poz.CustomerService.repository.TicketCommentsRepository;
import com.poz.CustomerService.repository.TicketsRepository;
import com.poz.CustomerService.security.CurrentAgentProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 工單相關的 business logic。
 *
 * <h2>目前有哪些方法可用</h2>
 * 對外開放（Controller 呼叫的）：
 * <ul>
 *   <li>{@link #create(CreateTicketRequest)} → {@link TicketListItemResponse}
 *       ——建立工單，並寫入建單當下的處理記錄</li>
 * </ul>
 * 內部小工具（private，Controller 叫不到）：
 * <ul>
 *   <li>{@link #resolveAssignee(String, String)}——決定指派給誰，沒指定就是自己</li>
 *   <li>{@link #generateTicketNo()}——產生對外的工單編號 TK-XXXXXX</li>
 *   <li>{@link #writeComment(Tickets, String, String)}——寫一筆處理記錄</li>
 * </ul>
 * <b>還沒有的</b>：列表查詢、工單詳情、狀態變更、轉派、新增留言。
 */
@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketsRepository ticketsRepository;
    private final TicketCommentsRepository ticketCommentsRepository;
    private final AgentsRepository agentsRepository;
    private final CurrentAgentProvider currentAgentProvider;

    // ------------------------------------------------------------------
    // 常數
    // ------------------------------------------------------------------

    /**
     * 工單狀態白名單，順便存中文標籤——系統留言「狀態設定為「處理中」」要用。
     * 三個值必須跟 {@code CK_tickets_status} 一致，否則存進去會被資料庫擋成 500。
     */
    private static final Map<String, String> STATUS_LABEL = Map.of(
            "IN_PROGRESS", "處理中",
            "PENDING", "待客戶回覆",
            "RESOLVED", "已解決");

    /** 通話工作台在通話中建立。 */
    private static final String CHANNEL_PHONE = "PHONE";

    /** 客服自己從「＋ 新增派件」手動建立。首字大寫是照 {@code CK_tickets_channel} 的值。 */
    private static final String CHANNEL_AGENT = "Agent";

    /** {@code tickets.title} 是 NVARCHAR(50)（V5 縮下來的），超過就存不進去。 */
    private static final int TITLE_MAX_LENGTH = 50;

    private static final String TICKET_NO_PREFIX = "TK-";

    /**
     * 三種狀態的顯示順序，tabCounts 就照這個順序排。
     * <p>
     * 不直接用 {@code STATUS_LABEL.keySet()} 的原因：{@code Map.of()} 建出來的 Map
     * <b>不保證順序</b>（而且每次跑順序還可能不一樣），拿它來決定畫面上的排列會很怪。
     */
    private static final List<String> STATUS_ORDER =
            List.of("IN_PROGRESS", "PENDING", "RESOLVED");

    /** tabCounts 裡「全部」那一格的 key，前端第一個 tab 用的。 */
    private static final String TAB_ALL = "ALL";

    /** 每頁筆數上限。不擋的話，有人送 size=999999 就是一次把整張表撈進記憶體。 */
    private static final int MAX_PAGE_SIZE = 50;

    /** 列表預設排序：最近有動靜的排前面，同 index.html:637。 */
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "updatedAt");

    // ------------------------------------------------------------------
    // 對外的方法
    // ------------------------------------------------------------------

    /**
     * 工單列表，對應 GET /api/tickets。首頁那張表格。
     * <p>
     * <b>目前是最簡版：不吃任何篩選條件，一律回全部工單。</b>
     * 單號 / 姓名 / 電話 / 客服 / 狀態 tab / 時間範圍這六個篩選還沒接，
     * 作法見 {@code TicketsRepository} 的 TODO(2) 與 {@code TicketSearchRequest}。
     * 分頁與排序已經是完整的，接篩選時不用重寫。
     *
     * @param page {@code int}——頁碼，<b>從 1 開始</b>（不是 0）
     * @param size {@code int}——每頁筆數，1 到 {@value #MAX_PAGE_SIZE}
     * @return {@link TicketPageResponse}——這一頁的工單、分頁資訊、四個 tab 的件數。
     *         查無資料時 content 是空 list、totalElements 是 0，<b>不是 404</b>
     *         （「符合條件的有 0 筆」是正常結果，不是錯誤）
     * @throws ApiException 400 / {@code VALIDATION_ERROR}——page 小於 1，
     *                      或 size 不在 1 到 {@value #MAX_PAGE_SIZE} 之間
     */
    @Transactional(readOnly = true)
    public TicketPageResponse search(int page, int size) {
        // 這兩個值來自網址，使用者想改就改，所以一定要擋。
        // 之後改用 TicketSearchRequest 時，這段會被 DTO 上的 @Min / @Max 取代。
        if (page < 1) {
            throw ApiException.badRequest("VALIDATION_ERROR", "頁碼不可小於 1");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw ApiException.badRequest("VALIDATION_ERROR",
                    "每頁筆數必須介於 1 到 " + MAX_PAGE_SIZE);
        }

        // PageRequest 的頁碼從 0 開始，所以這裡減 1。
        // 轉回 1-based 是 TicketPageResponse.from() 的事，兩邊各做一次、各只做一次。
        Page<Tickets> result =
                ticketsRepository.findAll(PageRequest.of(page - 1, size, DEFAULT_SORT));

        return TicketPageResponse.from(result, countByTab());
    }

    /**
     * 建立工單。通話工作台的「✓ 建立工單並結束通話」與首頁的「＋ 新增派件」共用這一支，
     * 差別只在 {@code channel}。
     * <p>
     * 工單本身和底下的處理記錄都在<b>同一個交易</b>裡寫入：中途任何一步失敗，
     * 整批一起回滾，不會留下一張沒有任何記錄的孤兒工單。
     *
     * @param request {@link CreateTicketRequest}——表單內容，不可為 null。
     *                {@code ticketNo} 不在裡面，由後端產生；「誰建立的」也不由前端指定
     * @return {@link TicketListItemResponse}——建立好的工單，含後端發的 {@code ticketNo}。
     *         前端拿到之後就能導到工單詳情頁
     * @throws ApiException 400 / {@code INVALID_TICKET_STATUS}——狀態不在白名單內；
     *                      400 / {@code VALIDATION_ERROR}——主旨超過 50 字；
     *                      404 / {@code AGENT_NOT_FOUND}——指定的轉派對象不存在。
     *                      必填欄位空白不在這裡擋，由 DTO 的 {@code @NotBlank} 負責
     */
    @Transactional
    public TicketListItemResponse create(CreateTicketRequest request) {
        // 「必填」不在這裡檢查——交給 CreateTicketRequest 的 @NotBlank 搭配 Controller 的
        // @Valid。不合格的請求會被 Spring 擋在 Controller，根本進不到這裡，
        // 錯誤文案也只有 DTO 那一份，不會兩邊各寫一句然後改到不一致。
        //
        // 代價寫清楚：這支方法假設呼叫端已經驗過。從 Controller 以外的地方呼叫
        // （測試、排程、其他 Service）而且沒驗，title / category 是 null 時這裡會 NPE。
        String status = request.status();
        if (!STATUS_LABEL.containsKey(status)) {
            throw ApiException.badRequest("INVALID_TICKET_STATUS", "不支援的工單狀態：" + status);
        }

        String channel = request.channel();

        String title = request.title().trim();
        if (title.length() > TITLE_MAX_LENGTH) {
            throw ApiException.badRequest("VALIDATION_ERROR",
                    "主旨長度不可超過 " + TITLE_MAX_LENGTH);
        }
        String category = request.category().trim();

        String me = currentAgentProvider.currentAgentId();
        String assigneeId = resolveAssignee(request.assigneeId(), me);
        String description = request.description();

        Tickets ticket = ticketsRepository.save(Tickets.builder()
                .ticketNo(generateTicketNo())
                .title(title)
                .customerName(request.customerName())
                // 只有電話去頭尾空白。使用者常從別處複製貼上，多一個空白會讓
                // 「用進線號碼查歷史工單」查不到——那是等值比對，走
                // IX_tickets_contact_phone 這條索引，而且畫面上看起來一模一樣，很難查。
                .contactPhone(request.contactPhone().trim())
                .category(category)
                .description(description)
                .status(status)
                .channel(channel)
                .assigneeId(assigneeId)
                .build());
        // createdAt / updatedAt 不用自己填，Tickets 的 @PrePersist 會補上同一個時間，
        // 剛好符合前端「剛建立的工單兩個時間對齊」的顯示規則（更新時間顯示為「—」）。

        // 處理記錄，順序照 index.html 的 createFromCall()
        writeComment(ticket, null, CHANNEL_PHONE.equals(channel)
                ? "工單經電話進線建立"
                : "工單以新增派件建立");
        writeComment(ticket, me, description);

        writeComment(ticket, null, "狀態設定為「" + STATUS_LABEL.get(status) + "」");
        if (!assigneeId.equals(me)) {
            writeComment(ticket, null, "由 " + me + " 轉派給 " + assigneeId);
        }

        return TicketListItemResponse.from(ticket);
    }

    // ------------------------------------------------------------------
    // 內部小工具
    // ------------------------------------------------------------------

    /**
     * 算出首頁四個 tab 括號裡的數字。
     * <p>
     * 這四個數字<b>不受篩選條件影響</b>，一律從全部工單算起。
     * 理由看原型第 655 行：{@code tabCount} 是從 tickets 全部算的，沒有套 listFiltered()。
     * 不這樣做的話，使用者停在「處理中」那個 tab 時，另外三個 tab 會全部變成 0，
     * 他就再也點不回去了。
     *
     * @return {@code Map<String, Long>}——四個 key 一定都在，順序是
     *         IN_PROGRESS / PENDING / RESOLVED / ALL。用 LinkedHashMap 才留得住順序
     *         （前端是照 key 取值，順序只影響 JSON 讀起來順不順眼）
     */
    private Map<String, Long> countByTab() {
        Map<String, Long> counts = new LinkedHashMap<>();
        long all = 0;

        for (String status : STATUS_ORDER) {
            long count = ticketsRepository.countByStatus(status);
            counts.put(status, count);
            all += count;
        }

        // ALL 不是查出來的，是三個加起來——少打一次資料庫。
        // 前提是 CK_tickets_status 保證 status 只會是這三種，不會有第四種漏算。
        counts.put(TAB_ALL, all);
        return counts;
    }

    /**
     * 決定這張工單要指派給誰。
     * <p>
     * 前端只有勾了「轉派給其他客服」才會送代號，沒送就是自己處理。
     * 代號是<b>使用者手打</b>的（見 index.html 的 c-assignee-code 輸入框），
     * 打錯的話直接存下去會撞上外鍵 {@code FK_tickets_agents} 變成 500，
     * 所以這裡先查一次，讓使用者看到看得懂的 404。
     *
     * @param requested {@code String}——前端送來的轉派對象，可為 null 或空白
     * @param me        {@code String}——目前登入的客服代號
     * @return {@code String}——最終的負責客服代號，不會是 null
     * @throws ApiException 404 / {@code AGENT_NOT_FOUND}——代號不存在
     */
    private String resolveAssignee(String requested, String me) {
        String assigneeId = requested;
        if (assigneeId == null) {
            return me;
        }
        if (!agentsRepository.existsById(assigneeId)) {
            throw ApiException.notFound("AGENT_NOT_FOUND", "找不到客服：" + assigneeId);
        }
        return assigneeId;
    }

    /**
     * 產生對外的工單編號，格式 {@code TK-} + 六位數字（例：TK-084215），共 9 個字，
     * 塞得進 {@code ticket_no} 的 NVARCHAR(10)。
     * <p>
     * <b>已知限制</b>：這裡<b>沒有</b>檢查編號是否已被用過，撞號時會由資料表上的
     * {@code UQ_tickets_ticket_no} 擋下來，使用者看到的是 500。
     * 九十萬個號碼隨機撞上的機率很低，但不是零。
     * 要補這一關，{@code TicketsRepository} 需要一支
     * {@code boolean existsByTicketNo(String ticketNo)}，
     * 再把下面改成「產生 → 檢查 → 重試最多 N 次」的迴圈。
     *
     * @return {@code String}——工單編號，例如 TK-084215
     */
    private String generateTicketNo() {
        return TICKET_NO_PREFIX + ThreadLocalRandom.current().nextInt(100_000, 1_000_000);
    }

    /**
     * 寫一筆處理記錄。
     *
     * @param ticket  {@link Tickets}——已經 save 過的工單，這裡要拿它被資料庫發號的 ticketId
     * @param agentId {@code String}——留言者的客服代號；<b>傳 null 代表系統事件</b>
     *                （建單、狀態設定、轉派），前端會顯示成「系統」
     * @param content {@code String}——留言內容或系統事件描述，不可為 null
     */
    private void writeComment(Tickets ticket, String agentId, String content) {
        ticketCommentsRepository.save(TicketComments.builder()
                .ticketId(ticket.getTicketId())
                .agentId(agentId)
                .content(content)
                .build());
        // createdAt 由 TicketComments 的 @PrePersist 補。
        // 注意：時間只精確到秒，同一次建單的幾筆記錄時間會一樣，
        // 之後撈 timeline 排序時要再帶上 commentId 才不會亂序。
    }

    // ==================================================================
    // TODO 還沒寫的部分。建議照這個順序做，每做完一支就 compile 一次。
    //
    // 先加一個常數：狀態機。格式參考上面的 STATUS_LABEL。
    //
    //     private static final Map<String, List<String>> ALLOWED_TRANSITIONS = Map.of(
    //             "IN_PROGRESS", List.of(...),
    //             ...);
    //
    //     規則去 index.html:261 的 TRANSITIONS 抄，或看 api.md 那張表。
    //     這份表 detail() 和 updateStatus() 都會用到，所以**只能有一份**。
    //
    // ------------------------------------------------------------------
    // 對外的方法（Controller 會叫的）
    // ------------------------------------------------------------------
    //
    // 1) search 的最簡版已經做好了（見上面），但**篩選條件還沒接**。
    //    要接的話：把參數從 (int page, int size) 換成 TicketSearchRequest，
    //    findAll(Pageable) 換成 TicketsRepository 裡 TODO(2) 那支自訂查詢。
    //    分頁、排序、tabCounts 都不用重寫。
    //
    //    順序建議：一次只加一個條件，加完就打一次 API 確認，
    //    六個一起加然後查不出東西時，你會不知道是哪一個寫壞的。
    //
    // 2) detail(String ticketNo) → TicketDetailResponse
    //    @Transactional(readOnly = true)
    //
    //    步驟：用 ticketNo 撈工單（查不到丟 404 / TICKET_NOT_FOUND）
    //    → 用它的 ticketId 撈 comments → 把 agentId 換成 agentName
    //    → 查 ALLOWED_TRANSITIONS → 組成 TicketDetailResponse。
    //
    //    agentName 不要在迴圈裡一則一則查（N+1），理由見 TicketCommentResponse 的註解。
    //
    // 3) updateStatus(String ticketNo, UpdateTicketStatusRequest request) → TicketDetailResponse
    //    @Transactional
    //
    //    步驟：撈工單 → 查 ALLOWED_TRANSITIONS 確認這個轉換合法
    //    （不合法丟 400 / INVALID_STATUS_TRANSITION，訊息用中文標籤，
    //      例：「無法從「已解決」變更為「待客戶回覆」」——STATUS_LABEL 就是為此存在的）
    //    → 改狀態 → writeComment(ticket, null, "狀態由「X」變更為「Y」")
    //    → 回 detail。
    //
    //    注意：在 @Transactional 方法裡改「從 repository 撈出來的」實體，
    //    交易結束會自動 UPDATE，**不需要呼叫 save()**。
    //    （這叫 dirty checking，AgentService.login() 那段註解有寫。）
    //
    //    再注意：新舊狀態一樣時要怎麼辦？寫一筆「由處理中變更為處理中」很怪。
    //
    // 4) updateAssignee(String ticketNo, UpdateTicketAssigneeRequest request) → TicketDetailResponse
    //    @Transactional
    //
    //    步驟：撈工單 → 確認新客服存在（現成的 resolveAssignee 能不能重用？看一下簽章）
    //    → 跟目前負責人相同就直接回、不做事也不寫留言（api.md 明寫這條）
    //    → 改 assigneeId → writeComment(ticket, null, "由 A 轉派給 B")。
    //
    // 5) addComment(String ticketNo, CreateCommentRequest request) → ？
    //    @Transactional
    //
    //    步驟：撈工單 → writeComment(ticket, currentAgentProvider.currentAgentId(), 內容)。
    //
    //    這一支有個容易漏掉的點：只寫 ticket_comments 的話，
    //    tickets.updated_at **不會變**（@PreUpdate 只在 tickets 這張表被 UPDATE 時觸發）。
    //    但列表是照 updated_at 排序的，「剛留言的工單」不會浮到最上面。
    //    原型的 addComment() 有呼叫 touch(t)（第 771 行）。你要怎麼做到一樣的效果？
    //
    // ------------------------------------------------------------------
    // 建議一併補的內部小工具（private）
    // ------------------------------------------------------------------
    //
    //   findTicketOrThrow(String ticketNo) → Tickets
    //       上面五支有四支開頭都是「用 ticketNo 撈工單，查不到丟 404」，抽出來。
    //       寫法照 AgentService.findAgentOrThrow()。
    //
    //   toDetail(Tickets ticket) → TicketDetailResponse
    //       detail / updateStatus / updateAssignee 三支的結尾都要組同一份回應，抽出來。
    //
    // ------------------------------------------------------------------
    // 別忘了
    // ------------------------------------------------------------------
    //   [ ] readOnly = true 只用在查詢，會改資料的不能加
    //   [ ] 所有錯誤都用 ApiException.notFound / badRequest 丟，不要自己 new RuntimeException
    //   [ ] 錯誤訊息不要出現資料表名稱或 SQL 片段（見 ApiException 的註解）
    // ==================================================================

}
