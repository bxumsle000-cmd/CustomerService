package com.poz.CustomerService.controller;

import com.poz.CustomerService.dto.agent.AgentResponse;
import com.poz.CustomerService.dto.agent.UpdateAgentStatusRequest;
import com.poz.CustomerService.service.AgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客服相關端點：轉派時要用的客服清單，以及右上角的工作狀態切換。
 */
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    /**
     * 客服清單，GET /api/agents。供轉派時做成下拉選單或驗證代號。
     * <p>
     * 沒有參數，也沒有分頁——客服人數不多，一次全給。
     *
     * @return {@code List<AgentResponse>}——200，已依 agentId 排序；沒資料時回空 list，不會是 null
     */
    @GetMapping
    public List<AgentResponse> findAll() {
        return agentService.findAll();
    }

    /**
     * 變更自己的工作狀態（右上角頭像的下拉選單）。
     *
     * 路徑寫死 me、不吃 agentId：能指定 agentId 就等於開放「改別人的狀態」，
     * 而現階段沒有權限檢查擋得住。用 PATCH 不用 PUT，是因為只改一個欄位、其他不動。
     *
     * @param request {@link UpdateAgentStatusRequest}——request body，只有一個欄位 {@code status}，
     *                值限 ONLINE / BREAK / RESTROOM / LUNCH / MEETING。不接受 agentId
     * @return {@link AgentResponse}——200，改完之後的 agentId / name / status。
     *         狀態不合法或通話中回 400
     */
    @PatchMapping("/me/status")
    public AgentResponse updateMyStatus(@Valid @RequestBody UpdateAgentStatusRequest request) {
        return agentService.updateMyStatus(request);
    }
}
