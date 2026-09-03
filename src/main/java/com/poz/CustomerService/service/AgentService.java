package com.poz.CustomerService.service;

import com.poz.CustomerService.entity.Agents;
import com.poz.CustomerService.dto.agent.AgentResponse;
import com.poz.CustomerService.dto.auth.LoginRequest;
import com.poz.CustomerService.dto.auth.LoginResponse;
import com.poz.CustomerService.dto.agent.UpdateAgentStatusRequest;
import com.poz.CustomerService.dto.agent.UpdateCallStateRequest;
import com.poz.CustomerService.exception.ApiException;
import com.poz.CustomerService.repository.AgentsRepository;
import com.poz.CustomerService.security.CurrentAgentProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 客服相關的 business logic：登入、查自己、客服清單、變更工作狀態、通話中狀態切換。
 * <p>
 * 方法簽章上都沒有 agentId，「我是誰」一律由
 * {@link CurrentAgentProvider#currentAgentId()} 決定。
 */
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentsRepository agentsRepository;
    private final PasswordEncoder passwordEncoder;

    /** 「目前登入的是誰」只有它知道。 */
    private final CurrentAgentProvider currentAgentProvider;

    // ------------------------------------------------------------------
    // 狀態常數
    // ------------------------------------------------------------------
    private static final String STATUS_ONLINE = "ONLINE";

    /** 通話中。由通話事件驅動，不接受客服手動設定。 */
    private static final String STATUS_ON_CALL = "ON_CALL";

    /** 還沒接 JWT，先回一個明顯是假的字串。 */
    private static final String DEV_PLACEHOLDER_TOKEN = "DEV-TOKEN-NOT-A-REAL-JWT";

    // ------------------------------------------------------------------
    // 對外的方法
    // ------------------------------------------------------------------

    /**
     * 登入，成功會把狀態重設為 ONLINE。
     *
     * @param request 客服代號與密碼明文，不可為 null
     * @return token 與登入者的公開資訊，status 一定是 ONLINE
     * @throws ApiException 401 / {@code INVALID_CREDENTIALS}——代號不存在或密碼錯誤
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        Agents agent = agentsRepository.findById(request.agentId())
                .orElseThrow(AgentService::invalidCredentials);

        if (!passwordEncoder.matches(request.password(), agent.getPasswordHash())) {
            throw invalidCredentials();
        }

        // 登入成功要把狀態重設為 ONLINE，否則上次留下的「午休」會被帶到今天
        agent.setStatus(STATUS_ONLINE);

        return new LoginResponse(DEV_PLACEHOLDER_TOKEN, AgentResponse.from(agent));
    }

    /**
     * 取得目前登入的客服。
     *
     * @return 目前登入者的 agentId / name / status
     * @throws ApiException 404 / {@code AGENT_NOT_FOUND}——登入中的代號在資料庫查不到
     */
    @Transactional(readOnly = true)
    public AgentResponse me() {
        return AgentResponse.from(findAgentOrThrow(currentAgentProvider.currentAgentId()));
    }

    /**
     * 客服清單，供轉派時驗證代號或做成下拉選單。
     *
     * @return 全部客服，已依 agentId 排序；沒資料時回空 list，不會是 null
     */
    @Transactional(readOnly = true)
    public List<AgentResponse> findAll() {
        return agentsRepository.findAll(Sort.by("agentId"))
                .stream()
                .map(AgentResponse::from)
                .toList();
    }

    /**
     * 變更自己的工作狀態。
     *
     * @param request 要切換到的狀態，不可為 null，只接受
     *                ONLINE / BREAK / RESTROOM / LUNCH / MEETING
     * @return 改完之後的 agentId / name / status
     * @throws ApiException 400 / {@code AGENT_ON_CALL}——目前通話中，不允許變更；
     *                      404 / {@code AGENT_NOT_FOUND}——登入中的代號在資料庫查不到
     */
    @Transactional
    public AgentResponse updateMyStatus(UpdateAgentStatusRequest request) {
        String newStatus = request.status();

        Agents agent = findAgentOrThrow(currentAgentProvider.currentAgentId());

        // 通話中與否要看資料庫現況，DTO 的 @Pattern 擋不了
        if (STATUS_ON_CALL.equals(agent.getStatus())) {
            throw ApiException.badRequest(
                    "AGENT_ON_CALL",
                    "通話中不可變更狀態，請於通話結束後再試");
        }

        agent.setStatus(newStatus);

        return AgentResponse.from(agent);
    }

    /**
     * 由通話事件切換「通話中」：接聽時設成 {@code ON_CALL}，掛斷時回到 {@code ONLINE}。
     * <p>
     * 這是<b>唯一</b>能把狀態寫成 {@code ON_CALL} 的入口，{@link #updateMyStatus} 的白名單裡沒有它。
     * 掛斷時只有「目前真的是通話中」才會改回 {@code ONLINE}，避免重複掛斷
     * 把客服自己設好的休息／午休洗掉。
     *
     * @param request 通話狀態，不可為 null；{@code true} 接聽、{@code false} 掛斷
     * @return 改完之後的 agentId / name / status
     * @throws ApiException 404 / {@code AGENT_NOT_FOUND}——登入中的代號在資料庫查不到
     */
    @Transactional
    public AgentResponse updateMyCallState(UpdateCallStateRequest request) {
        Agents agent = findAgentOrThrow(currentAgentProvider.currentAgentId());

        if (request.inCall()) {
            agent.setStatus(STATUS_ON_CALL);
        } else if (STATUS_ON_CALL.equals(agent.getStatus())) {
            agent.setStatus(STATUS_ONLINE);
        }

        return AgentResponse.from(agent);
    }

    // ------------------------------------------------------------------
    // 內部小工具
    // ------------------------------------------------------------------

    /**
     * 依代號撈客服。
     *
     * @param agentId 客服代號，例如 CSC00001
     * @return 查到的客服，不會是 null
     * @throws ApiException 404 / {@code AGENT_NOT_FOUND}——代號不存在
     */
    private Agents findAgentOrThrow(String agentId) {
        return agentsRepository.findById(agentId)
                .orElseThrow(() -> ApiException.notFound(
                        "AGENT_NOT_FOUND", "找不到客服：" + agentId));
    }

    /**
     * 產生「帳號或密碼錯誤」的例外，確保帳號不存在與密碼錯誤回相同內容。
     *
     * @return 401 / {@code INVALID_CREDENTIALS} 的例外物件，還沒被丟出
     */
    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "客服代號或密碼錯誤");
    }


}
