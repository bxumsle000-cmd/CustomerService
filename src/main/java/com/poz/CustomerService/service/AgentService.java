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
 * 客服相關的business logic：登入、查自己、客服清單、變更工作狀態。
 *
 * <h2>身分怎麼來（重要）</h2>
 * 採「Service 自己決定我是誰」的做法：方法簽章上<b>沒有</b> agentId 參數，
 * 由下方的 {@link #currentAgentId()} 提供。
 * <p>
 * 現階段它回傳一個寫死的代號。等之後接上 JWT，<b>只要改那一個方法的內容</b>，
 * 這裡所有方法的簽章都不用動，Controller 也不用動。
 * <p>
 * 這樣做還有一個好處：Controller 沒有機會把身分弄錯。
 * 如果改成由 Controller 傳 agentId 進來，只要有一支不小心從 request 參數取值
 * （而不是從 token），就會變成「任何人都能查別人的資料」——
 * 而且不會噴錯、測試也會過，是最難發現的那種漏洞。
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
    /**
     * 還沒接 JWT，所以先回一個明顯是假的字串。
     * 故意寫得很醒目，萬一哪天不小心上線了，一眼就看得出來不對勁。
     */
    private static final String DEV_PLACEHOLDER_TOKEN = "DEV-TOKEN-NOT-A-REAL-JWT";
    // ------------------------------------------------------------------
    // 目前登入的客服
    // ------------------------------------------------------------------

    /**
     * 開發階段暫時寫死的客服代號，對應 V2__seed_agents.sql 建的「林曉明」。
     * 這是整個專案唯一一處假裝知道「我是誰」的地方。
     */
    private static final String DEV_CURRENT_AGENT_ID = "CSC00001";

    /**
     * 目前登入的客服代號。
     *
     * <b>接上 JWT 之後，把內容換成下面這行就好，其他地方都不用動：</b>
     * <pre>
     * return SecurityContextHolder.getContext().getAuthentication().getName();
     * </pre>
     */
    private String currentAgentId() {
        return DEV_CURRENT_AGENT_ID;
    }

    // ------------------------------------------------------------------
    // 對外的方法
    // ------------------------------------------------------------------

    /**
     * 登入，對應 POST /api/auth/login。
     *
     * <h4>失敗訊息為什麼不區分「帳號不存在」和「密碼錯誤」</h4>
     * 兩者分開講，等於告訴嘗試入侵的人「這個帳號是存在的」，
     * 對方就能先枚舉出有效帳號，再集中火力猜密碼。
     * 所以下面兩種失敗都丟同一個 code、同一句訊息。
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        Agents agent = agentsRepository.findById(request.agentId())
                .orElseThrow(AgentService::invalidCredentials);

        if (!passwordEncoder.matches(request.password(), agent.getPasswordHash())) {
            throw invalidCredentials();
        }

        // docs/api.md 要求：登入成功要把狀態重設為 ONLINE。
        // 否則上次下班前留下的「午休」會被帶到今天。
        //
        // 這裡不必呼叫 save()：方法有 @Transactional，agent 是「受管理的」實體，
        // 交易結束時 Hibernate 會自動偵測到欄位變動並發出 UPDATE
        // （順便觸發 Agents 的 @PreUpdate 更新 updated_at）。
        agent.setStatus(STATUS_ONLINE);

        return new LoginResponse(DEV_PLACEHOLDER_TOKEN, AgentResponse.from(agent));
    }

    /**
     * 取得目前登入的客服，對應 GET /api/auth/me。
     * 供側邊欄與右上角的狀態選單顯示。
     */
    @Transactional(readOnly = true)
    public AgentResponse me() {
        return AgentResponse.from(findAgentOrThrow(currentAgentId()));
    }

    /**
     * 客服清單，對應 GET /api/agents。
     * 供轉派時驗證代號或做成下拉選單。
     *
     * 依代號排序，讓回傳順序穩定——不排序的話，資料庫每次回傳的順序不保證一致，
     * 畫面上的下拉選單就會跳來跳去。
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
     * <h4>要擋兩件事，都回 400</h4>
     * <ol>
     *   <li>送 ON_CALL——通話中只能由通話事件觸發，不接受手動設定</li>
     *   <li>目前已經是 ON_CALL——通話中不允許變更，必須等通話結束</li>
     * </ol>
     * 第 1 點 DTO 的 @Pattern 也會擋，但那是 Controller 加了 @Valid 才會生效；
     * 這裡再擋一次，是因為 Service 不該假設呼叫端一定做過驗證。
     * 第 2 點 DTO 擋不了——它看不到資料庫裡的現況，只有這裡知道。
     */
    @Transactional
    public AgentResponse updateMyStatus(UpdateAgentStatusRequest request) {
        String newStatus = request.status();

        if (!PICKABLE_STATUS.contains(newStatus)) {
            throw ApiException.badRequest(
                    "INVALID_AGENT_STATUS",
                    STATUS_ON_CALL.equals(newStatus)
                            ? "「通話中」由系統自動設定，不可手動變更"
                            : "不支援的客服狀態：" + newStatus);
        }

        Agents agent = findAgentOrThrow(currentAgentId());

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
     * 依代號撈客服，查不到就丟 404。
     * <p>
     * 抽出來是因為 {@link #me()} 和 {@link #updateMyStatus} 都要做同一件事，
     * 錯誤訊息也該一致。
     *
     * @param agentId 客服代號，例如 CSC00001
     * @return <b>受 Hibernate 管理的</b>實體。這點很重要：在有 {@code @Transactional}
     *         的方法裡改它的欄位，交易結束時會自動寫回資料庫，不需要呼叫 save()
     * @throws ApiException 404 / {@code AGENT_NOT_FOUND}，當代號不存在
     */
    private Agents findAgentOrThrow(String agentId) {
        return agentsRepository.findById(agentId)
                .orElseThrow(() -> ApiException.notFound(
                        "AGENT_NOT_FOUND", "找不到客服：" + agentId));
    }

    /**
     * 產生「帳號或密碼錯誤」的例外（401）。
     * <p>
     * <b>注意是「回傳」不是「丟出」</b>——所以兩種用法都可以：
     * <pre>
     * throw invalidCredentials();                      // 直接丟
     * .orElseThrow(AgentService::invalidCredentials)   // 交給 Optional 延後呼叫
     * </pre>
     * <p>
     * 抽成方法的理由不是為了少打字，而是要<b>保證「帳號不存在」和「密碼錯誤」
     * 丟出完全相同的內容</b>。兩者訊息只要有一點不同，攻擊者就能拿來判斷
     * 哪些代號是有效帳號，先枚舉出名單再集中猜密碼。
     * 寫成兩份的話，哪天有人改了其中一句文案就破功了。
     *
     * @return 401 / {@code INVALID_CREDENTIALS} 的例外物件
     */
    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "客服代號或密碼錯誤");
    }


}
