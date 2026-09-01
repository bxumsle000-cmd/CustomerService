package com.poz.CustomerService.service;

import com.poz.CustomerService.dto.ticket.CreateTicketRequest;
import com.poz.CustomerService.dto.ticket.TicketCommentResponse;
import com.poz.CustomerService.dto.ticket.TicketDetailResponse;
import com.poz.CustomerService.dto.ticket.TicketListItemResponse;
import com.poz.CustomerService.dto.ticket.TicketPageResponse;
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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工單相關的 business logic。
 *
 * <h2>方法一覽</h2>
 * 對外開放（Controller 呼叫的）：
 * <ul>
 *   <li>{@link #search(int, int)}——工單列表，分頁並依更新時間排序（篩選條件還沒接）</li>
 *   <li>{@link #create(CreateTicketRequest)}——建立工單，同時寫入建單當下的處理記錄</li>
 *   <li>{@link #detail(String)}——工單詳情，含處理記錄 timeline 與可轉換的狀態</li>
 *   <li>{@link #changeStatus(String, String)}——變更狀態，順便寫一筆系統記錄</li>
 * </ul>
 * 內部小工具（private，Controller 叫不到）：
 * <ul>
 *   <li>{@link #findTicket(String)}——用 ticketNo 撈工單，撈不到丟 404</li>
 *   <li>{@link #resolveAssignee(String, String)}——決定指派給誰，沒指定就是自己</li>
 *   <li>{@link #writeComment(Tickets, String, String)}——寫一筆處理記錄</li>
 * </ul>
 * <b>還沒有的</b>：轉派、新增留言。
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
     * <p>
     * 用 {@code LinkedHashMap} 而不是 {@code Map.of()}：這裡的 put 順序有意義，
     * {@code Map.of()} 建出來的 Map <b>不保證順序</b>，拿它決定畫面排列會出問題。
     */
    private static final Map<String, String> STATUS_LABEL;
    static {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("IN_PROGRESS", "處理中");
        labels.put("PENDING", "待客戶回覆");
        labels.put("RESOLVED", "已解決");
        STATUS_LABEL = Collections.unmodifiableMap(labels);
    }

    /**
     * 狀態機：目前狀態 → 允許轉換成哪些狀態。
     * <p>
     * <b>現在用的人是 {@link #changeStatus(String, String)}</b>——工單詳情本來會把它回給前端，後來拿掉了（畫面上長按鈕用的是
     * index.html 自己那份 TRANSITIONS）。留著是因為 PATCH /status 一定會用到：
     * 前端的 {@code alert('非法的狀態轉換')} 只是第一道防線，擋不住直接打 API 的人，
     * 後端非驗不可。寫那支的時候直接拿這張表來擋。
     * <p>
     * 規則：處理中可以去待客戶回覆或已解決；待客戶回覆可以回處理中或去已解決；
     * 已解決只能回處理中（重啟案件），不能直接跳去待客戶回覆。
     * <p>
     * 用 {@code LinkedHashMap} 的理由同 {@link #STATUS_LABEL}——順序有意義，
     * 前端按鈕就照 list 的順序排。
     */

    /** 客服自己從「＋ 新增派件」手動建立。首字大寫是照 {@code CK_tickets_channel} 的值。 */
    private static final String CHANNEL_AGENT = "Agent";

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
     * @return {@link TicketPageResponse}——這一頁的工單與分頁資訊。
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

        return TicketPageResponse.from(result);
    }

    /**
     * 建立工單。通話工作台的「✓ 建立工單並結束通話」與首頁的「＋ 新增派件」共用這一支，
     * 差別只在 {@code channel}。
     * <p>
     * 工單本身和底下的處理記錄都在<b>同一個交易</b>裡寫入：中途任何一步失敗，
     * 整批一起回滾，不會留下一張沒有任何記錄的孤兒工單。
     *
     * @param request {@link CreateTicketRequest}——表單內容，不可為 null。
     *                {@code ticketNo} 不在裡面，由資料庫算（見 {@link Tickets#getTicketNo()}）；
     *                「誰建立的」也不由前端指定
     * @return {@link TicketListItemResponse}——建立好的工單，含資料庫算出來的 {@code ticketNo}。
     *         前端拿到之後就能導到工單詳情頁
     * @throws ApiException 404 / {@code AGENT_NOT_FOUND}——指定的轉派對象不存在。
     *                      必填欄位空白、主旨超長、status 不在白名單內都不在這裡擋，
     *                      由 DTO 的 {@code @NotBlank} / {@code @Size} / {@code @Pattern} 負責
     */
    @Transactional
    public TicketListItemResponse create(CreateTicketRequest request) {
        String status = request.status();
        String channel = request.channel();

        String title = request.title().trim();
        String category = request.category().trim();

        String me = currentAgentProvider.currentAgentId();
        String assigneeId = resolveAssignee(request.assigneeId(), me);
        String description = request.description();

        // ticketNo 沒有填：它是資料庫的計算欄位，由 ticket_id 推導，
        // INSERT 完 Hibernate 會自己 SELECT 回來，所以下面用得到 ticket.getTicketNo()。
        Tickets ticket = ticketsRepository.save(Tickets.builder()
                .title(title)
                .customerName(request.customerName())
                .contactPhone(request.contactPhone().trim())
                .category(category)
                .description(description)
                .status(status)
                .channel(channel)
                .assigneeId(assigneeId)
                .build());

        // 處理記錄，順序照 index.html 的 createFromCall()
        writeComment(ticket, null, channel.equals("PHONE")
                ? "工單經電話進線建立"
                : "工單以新增派件建立");
        writeComment(ticket, me, description);

        writeComment(ticket, null, "狀態設定為「" + STATUS_LABEL.get(status) + "」");
        if (!assigneeId.equals(me)) {
            writeAssignComment(ticket, me, assigneeId);
        }

        return TicketListItemResponse.from(ticket);
    }

    /**
     * 工單詳情，對應 GET /api/tickets/{ticketNo}。點開列表某一列之後看到的那一頁。
     * <p>
     * 做兩件事：撈工單、撈底下的處理記錄。
     * <p>
     * 留言只帶客服代號、不查姓名——畫面上顯示的就是代號，見
     * {@link TicketCommentResponse}。
     * <p>
     * 掛 {@code readOnly = true}：整支方法只讀不寫，這樣標之後 Hibernate 不會為了偵測變更
     * 而保留每個 entity 的快照，資料庫那邊也能走唯讀交易。順手防呆——底下不小心改到 entity
     * 欄位時不會真的被寫回資料庫。
     *
     * @param ticketNo {@code String}——網址上的工單編號，格式 TK-XXXXXX
     * @return {@link TicketDetailResponse}——工單全欄位 + timeline
     * @throws ApiException 404 / {@code TICKET_NOT_FOUND}——查無此單號
     */
    @Transactional(readOnly = true)
    public TicketDetailResponse detail(String ticketNo) {
        Tickets ticket = findTicket(ticketNo);

        // 留言是用 ticketId 接的（外鍵指向 tickets.ticket_id），不是 ticketNo。
        List<TicketComments> comments = ticketCommentsRepository
                .findByTicketIdOrderByCreatedAtAscCommentIdAsc(ticket.getTicketId());

        List<TicketCommentResponse> timeline = comments.stream()
                .map(TicketCommentResponse::from)
                .toList();

        return TicketDetailResponse.from(ticket, timeline);
    }

    /**
     * 變更工單狀態，對應 PATCH /api/tickets/{ticketNo}/status。工單詳情頁上那幾顆狀態按鈕。
     * <p>
     * {@code alert('非法的狀態轉換')} 只是第一道防線，擋不住直接打 API 的人，所以後端非驗不可。
     * 「改成跟現在一樣的狀態」也算非法——表裡每個 key 的 list 都沒放自己。
     * <p>
     * 不必呼叫 {@code save()}：{@link #findTicket(String)} 撈回來的是受管理的 entity，
     * 在這個有 {@code @Transactional} 的方法裡改欄位，交易結束時 Hibernate 會自己送出 UPDATE。
     *
     * @param ticketNo {@code String}——網址上的工單編號，格式 TK-XXXXXX
     * @param status   {@code String}——要改成的新狀態，IN_PROGRESS / PENDING / RESOLVED
     * @return {@link TicketDetailResponse}——改完的工單全欄位 + timeline
     *         （含剛寫入的那筆狀態變更記錄），前端拿到就能直接重畫整頁
     * @throws ApiException 404 / {@code TICKET_NOT_FOUND}——查無此單號；
     *                      400 / {@code INVALID_STATUS_TRANSITION}——不允許的狀態轉換，
     *                      包含 status 根本不在白名單內的情況
     */
    @Transactional
    public TicketDetailResponse changeStatus(String ticketNo, String status) {
        Tickets ticket = findTicket(ticketNo);
        String oldStatus = ticket.getStatus();
        ticket.setStatus(status);
        writeComment(ticket, null, "狀態由「" + STATUS_LABEL.get(oldStatus)
                + "」變更為「" + STATUS_LABEL.get(status) + "」");
        return detail(ticketNo);
    }

    @Transactional
    public  TicketDetailResponse assign(String ticketNo, String assignID){
        Tickets ticket = findTicket(ticketNo);
        if (!agentsRepository.existsById(assignID)) {
            throw ApiException.notFound("AGENT_NOT_FOUND", "找不到客服：" + assignID);
        }
        String me = currentAgentProvider.currentAgentId();
        ticket.setAssigneeId(assignID);
        writeAssignComment(ticket, me, assignID);
        return detail(ticketNo);
    }

    @Transactional
    public  TicketDetailResponse submitContent(String ticketNo, String content){
        Tickets ticket = findTicket(ticketNo);
        String me = currentAgentProvider.currentAgentId();
        writeComment(ticket, me, content);
        return detail(ticketNo);
    }

    // ------------------------------------------------------------------
    // 內部小工具
    // ------------------------------------------------------------------

    /**
     *
     * @param ticketNo {@code String}——對外的工單編號，格式 TK-XXXXXX
     * @return {@link Tickets}——查到的工單，不會是 null
     * @throws ApiException 404 / {@code TICKET_NOT_FOUND}——查無此單號。
     *                      查不到不是程式壞掉，是使用者把單號打錯了，
     *                      所以回 404 而不是讓 NPE 冒出去變成 500
     */
    private Tickets findTicket(String ticketNo) {
        return ticketsRepository.findByTicketNo(ticketNo)
                .orElseThrow(() -> ApiException.notFound(
                        "TICKET_NOT_FOUND", "找不到工單：" + ticketNo));
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
        // 空字串也算「沒送」：前端那個輸入框就算沒勾轉派也可能帶著空值上來
        // （index.html:450 是無條件讀欄位值），當成漏填擋掉會很莫名其妙。
        if (requested == null || requested.isBlank()) {
            return me;
        }
        String assigneeId = requested.trim();
        if (!agentsRepository.existsById(assigneeId)) {
            throw ApiException.notFound("AGENT_NOT_FOUND", "找不到客服：" + assigneeId);
        }
        return assigneeId;
    }

    /**
     * 寫一筆轉派記錄。{@link #create(CreateTicketRequest)} 建單時就指定別人、
     * 以及 {@link #assign(String, String)} 事後轉派，兩邊共用這一支。
     * <p>
     * 抽出來是為了讓「同一種事件」在 timeline 上長得一模一樣——文案一致，
     * 留言者也一致掛在<b>執行轉派的人</b>名下（不是系統事件），
     * 否則同樣一句「由 A 轉派給 B」會因為進入點不同而顯示成兩種樣子。
     *
     * @param ticket        {@link Tickets}——已經 save 過的工單
     * @param me            {@code String}——執行轉派的客服代號，會成為這筆記錄的留言者
     * @param targetAgentId {@code String}——被轉派到的客服代號
     */
    private void writeAssignComment(Tickets ticket, String me, String targetAgentId) {
        writeComment(ticket, me, "由 " + me + " 轉派給 " + targetAgentId);
    }

    /**
     * 寫一筆處理記錄。
     *
     * @param ticket  {@link Tickets}——已經 save 過的工單，這裡要拿它被資料庫發號的 ticketId
     * @param agentId {@code String}——留言者的客服代號；<b>傳 null 代表系統事件</b>
     *                （建單、狀態設定），前端會顯示成「系統」。
     *                轉派不算系統事件，走 {@link #writeAssignComment}
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

}
