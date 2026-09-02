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
     * 客服清單，GET /api/agents。沒有參數也沒有分頁，一次全給。
     *
     * @return 200，已依 agentId 排序；沒資料時回空 list，不會是 null
     */
    @GetMapping
    public List<AgentResponse> findAll() {
        return agentService.findAll();
    }

    /**
     * 變更自己的工作狀態，PATCH /api/agents/me/status。路徑寫死 me，不吃 agentId。
     *
     * @param request request body，只有一個欄位 {@code status}，
     *                值限 ONLINE / BREAK / RESTROOM / LUNCH / MEETING
     * @return 200，改完之後的 agentId / name / status；狀態不合法或通話中回 400
     */
    @PatchMapping("/me/status")
    public AgentResponse updateMyStatus(@Valid @RequestBody UpdateAgentStatusRequest request) {
        return agentService.updateMyStatus(request);
    }
}
