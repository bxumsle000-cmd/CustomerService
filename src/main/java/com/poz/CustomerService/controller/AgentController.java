package com.poz.CustomerService.controller;

import com.poz.CustomerService.dto.AgentResponse;
import com.poz.CustomerService.dto.UpdateAgentStatusRequest;
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
 * 客服相關端點，對應 docs/api.md「七、下拉選單 / 驗證」與「二」的狀態切換那一段。
 */
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    /**
     * 客服清單，供轉派時做成下拉選單或驗證代號。
     * 回傳已依代號排序，順序每次都一樣，選單不會跳來跳去。
     */
    @GetMapping
    public List<AgentResponse> findAll() {
        return agentService.findAll();
    }

    /**
     * 變更自己的工作狀態（右上角頭像的下拉選單）。
     *
     * <b>路徑寫死 me，不是 {agentId}。</b>
     * 有了 {agentId} 就等於開放「改別人的狀態」，而現階段沒有任何權限檢查擋得住。
     * 之後 CTI 系統要設 ON_CALL 時，會另外開一支走機器對機器認證的端點，不共用這支。
     *
     * <p>用 PATCH 不用 PUT：PUT 的語意是「整個物件換掉」，但這裡只改一個欄位，
     * 其他欄位不動，那是 PATCH 的語意。
     */
    @PatchMapping("/me/status")
    public AgentResponse updateMyStatus(@Valid @RequestBody UpdateAgentStatusRequest request) {
        return agentService.updateMyStatus(request);
    }
}
