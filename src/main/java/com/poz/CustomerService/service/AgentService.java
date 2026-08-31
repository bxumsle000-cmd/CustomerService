package com.poz.CustomerService.service;

import com.poz.CustomerService.entity.Agents;
import com.poz.CustomerService.dto.AgentResponse;
import com.poz.CustomerService.dto.LoginRequest;
import com.poz.CustomerService.dto.LoginResponse;
import com.poz.CustomerService.dto.UpdateAgentStatusRequest;
import com.poz.CustomerService.exception.ApiException;
import com.poz.CustomerService.repository.AgentsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 客服相關的 business logic：登入、查自己、客服清單、變更工作狀態。
 *
 * <h2>目前有哪些方法可用</h2>
 * 對外開放（Controller 呼叫的就是這四支）：
 * <ul>
 *   <li>{@link #login(LoginRequest)} → {@link LoginResponse}
 *       ——POST /api/auth/login。驗帳密、狀態重設為 ONLINE，回傳 token 和客服資訊</li>
 *   <li>{@link #me()} → {@link AgentResponse}
 *       ——GET /api/auth/me。取得「目前登入的自己」</li>
 *   <li>{@link #findAll()} → {@code List<AgentResponse>}
 *       ——GET /api/agents。全部客服、依代號排序，給轉派下拉選單用</li>
 *   <li>{@link #updateMyStatus(UpdateAgentStatusRequest)} → {@link AgentResponse}
 *       ——PATCH /api/agents/me/status。變更自己的工作狀態</li>
 * </ul>
 * 內部小工具（private，Controller 叫不到）：
 * <ul>
 *   <li>{@link #currentAgentId()}——「我是誰」，目前寫死，之後接 JWT 只改這一支</li>
 *   <li>{@link #findAgentOrThrow(String)}——依代號撈客服，查不到丟 404</li>
 *   <li>{@link #invalidCredentials()}——產生 401 例外</li>
 * </ul>
 * <b>還沒有的</b>：新增客服、改密碼、停用帳號。客服資料目前由 V2__seed_agents.sql 直接塞進資料庫。
 *
 * <h2>身分怎麼來</h2>
 * 方法簽章上<b>都沒有</b> agentId 參數，「我是誰」一律由 {@link #currentAgentId()} 決定。
 * 這樣 Controller 就沒機會把身分弄錯——若改由 Controller 傳進來，
 * 只要有一支不小心從 request 參數取值而不是從 token，就變成「任何人都能查別人的資料」，
 * 而且不會噴錯、測試也會過。
 */
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentsRepository agentsRepository;
    private final PasswordEncoder passwordEncoder;

    // ------------------------------------------------------------------
    // 狀態常數
    // ------------------------------------------------------------------
    private static final String STATUS_ONLINE = "ONLINE";

    /** 通話中。由通話事件驅動，不接受客服手動設定。 */
    private static final String STATUS_ON_CALL = "ON_CALL";

    /** 允許客服手動選擇的狀態。刻意不含 ON_CALL。 */
    private static final Set<String> PICKABLE_STATUS =
            Set.of("ONLINE", "BREAK", "RESTROOM", "LUNCH", "MEETING");

    /** 還沒接 JWT，先回一個明顯是假的字串，萬一不小心上線一眼就看得出來不對勁。 */
    private static final String DEV_PLACEHOLDER_TOKEN = "DEV-TOKEN-NOT-A-REAL-JWT";

    // ------------------------------------------------------------------
    // 目前登入的客服
    // ------------------------------------------------------------------

    /** 開發階段寫死的客服代號，對應 V2__seed_agents.sql 建的「林曉明」。 */
    private static final String DEV_CURRENT_AGENT_ID = "CSC00001";

    /**
     * 目前登入的客服代號。
     * <p>
     * 接上 JWT 之後把內容換成
     * {@code SecurityContextHolder.getContext().getAuthentication().getName()} 就好，
     * 其他地方都不用動。
     *
     * @return {@code String}——客服代號，例如 CSC00001。現階段固定回傳寫死的值，不會是 null
     */
    private String currentAgentId() {
        return DEV_CURRENT_AGENT_ID;
    }

    // ------------------------------------------------------------------
    // 對外的方法
    // ------------------------------------------------------------------

    /**
     * 登入，對應 POST /api/auth/login。成功會把狀態重設為 ONLINE。
     *
     * @param request {@link LoginRequest}——客服代號與密碼明文，不可為 null
     * @return {@link LoginResponse}——token 與登入者的公開資訊，status 一定是 ONLINE
     * @throws ApiException 401 / {@code INVALID_CREDENTIALS}——代號不存在或密碼錯誤，
     *                      兩者刻意回相同訊息
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        Agents agent = agentsRepository.findById(request.agentId())
                .orElseThrow(AgentService::invalidCredentials);

        if (!passwordEncoder.matches(request.password(), agent.getPasswordHash())) {
            throw invalidCredentials();
        }

        // 規格要求：登入成功要把狀態重設為 ONLINE，
        // 否則上次下班前留下的「午休」會被帶到今天。
        //
        // 不必呼叫 save()：方法有 @Transactional、agent 是受管理的實體，
        // 交易結束時 Hibernate 會自動偵測欄位變動並發出 UPDATE。
        agent.setStatus(STATUS_ONLINE);

        return new LoginResponse(DEV_PLACEHOLDER_TOKEN, AgentResponse.from(agent));
    }

    /**
     * 取得目前登入的客服，對應 GET /api/auth/me。供側邊欄與右上角狀態選單顯示。
     * <p>
     * 沒有參數——「我是誰」由 {@link #currentAgentId()} 決定，不由呼叫端指定。
     *
     * @return {@link AgentResponse}——目前登入者的 agentId / name / status
     * @throws ApiException 404 / {@code AGENT_NOT_FOUND}——登入中的代號在資料庫查不到
     */
    @Transactional(readOnly = true)
    public AgentResponse me() {
        return AgentResponse.from(findAgentOrThrow(currentAgentId()));
    }

    /**
     * 客服清單，對應 GET /api/agents。供轉派時驗證代號或做成下拉選單。
     *
     * @return {@code List<AgentResponse>}——全部客服，已依 agentId 由小到大排序；
     *         沒資料時回空 list，不會是 null
     */
    @Transactional(readOnly = true)
    public List<AgentResponse> findAll() {
        return agentsRepository.findAll(Sort.by("agentId"))
                .stream()
                .map(AgentResponse::from)
                .toList();
    }

    /**
     * 變更自己的工作狀態，對應 PATCH /api/agents/me/status。
     *
     * @param request {@link UpdateAgentStatusRequest}——要切換到的狀態，不可為 null。
     *                只接受 ONLINE / BREAK / RESTROOM / LUNCH / MEETING。
     *                沒有 agentId，改的一定是自己
     * @return {@link AgentResponse}——改完之後的 agentId / name / status
     * @throws ApiException 400 / {@code INVALID_AGENT_STATUS}——狀態不在白名單內（含 ON_CALL）；
     *                      400 / {@code AGENT_ON_CALL}——目前通話中，不允許變更；
     *                      404 / {@code AGENT_NOT_FOUND}——登入中的代號在資料庫查不到
     */
    @Transactional
    public AgentResponse updateMyStatus(UpdateAgentStatusRequest request) {
        String newStatus = request.status();

        // DTO 的 @Pattern 也會擋這一關，但那要 Controller 加了 @Valid 才生效，
        // Service 不該假設呼叫端一定驗過。
        if (!PICKABLE_STATUS.contains(newStatus)) {
            throw ApiException.badRequest(
                    "INVALID_AGENT_STATUS",
                    STATUS_ON_CALL.equals(newStatus)
                            ? "「通話中」由系統自動設定，不可手動變更"
                            : "不支援的客服狀態：" + newStatus);
        }

        Agents agent = findAgentOrThrow(currentAgentId());

        // 這一關 DTO 擋不了——它看不到資料庫裡的現況，只有這裡知道。
        if (STATUS_ON_CALL.equals(agent.getStatus())) {
            throw ApiException.badRequest(
                    "AGENT_ON_CALL",
                    "通話中不可變更狀態，請於通話結束後再試");
        }

        agent.setStatus(newStatus);   // 同 login()，交易結束自動 UPDATE

        return AgentResponse.from(agent);
    }

    // ------------------------------------------------------------------
    // 內部小工具
    // ------------------------------------------------------------------

    /**
     * 依代號撈客服，查不到就丟 404。{@link #me()} 和 {@link #updateMyStatus} 共用，
     * 錯誤訊息才會一致。
     *
     * @param agentId {@code String}——客服代號，例如 CSC00001
     * @return {@link Agents}——<b>受 Hibernate 管理的</b>實體。在有 {@code @Transactional}
     *         的方法裡改它的欄位，交易結束會自動寫回資料庫，不必呼叫 save()
     * @throws ApiException 404 / {@code AGENT_NOT_FOUND}——代號不存在
     */
    private Agents findAgentOrThrow(String agentId) {
        return agentsRepository.findById(agentId)
                .orElseThrow(() -> ApiException.notFound(
                        "AGENT_NOT_FOUND", "找不到客服：" + agentId));
    }

    /**
     * 產生「帳號或密碼錯誤」的例外。
     * <p>
     * 抽成方法不是為了少打字，而是要<b>保證「帳號不存在」和「密碼錯誤」丟出完全相同的內容</b>，
     * 免得哪天有人改了其中一句文案，攻擊者就能拿來判斷哪些代號是有效帳號。
     *
     * @return {@link ApiException}——401 / {@code INVALID_CREDENTIALS}。
     *         <b>是「回傳」不是「丟出」</b>，所以 {@code throw invalidCredentials();} 和
     *         {@code .orElseThrow(AgentService::invalidCredentials)} 兩種用法都可以
     */
    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "客服代號或密碼錯誤");
    }


}
