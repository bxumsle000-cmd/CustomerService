package com.poz.CustomerService.controller;

import com.poz.CustomerService.dto.agent.AgentResponse;
import com.poz.CustomerService.dto.agent.UpdateAgentStatusRequest;
import com.poz.CustomerService.dto.agent.UpdateCallStateRequest;
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
 * 客服相關端點：轉派時要用的客服清單、右上角的工作狀態切換，以及通話中狀態的自動切換。
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

    /**
     * 通話開始／結束時切換「通話中」，PATCH /api/agents/me/call。
     * 由前端的接聽與掛斷按鈕觸發，不是給客服手動點的。
     *
     * @param request request body，只有一個欄位 {@code inCall}；
     *                {@code true} 接聽（狀態變 ON_CALL）、
     *                {@code false} 掛斷（通話中才會回到 ONLINE）
     * @return 200，改完之後的 agentId / name / status；欄位沒帶回 400
     */
    @PatchMapping("/me/call")
    public AgentResponse updateMyCallState(@Valid @RequestBody UpdateCallStateRequest request) {
        return agentService.updateMyCallState(request);
    }
}
