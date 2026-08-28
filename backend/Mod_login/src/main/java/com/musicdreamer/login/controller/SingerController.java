package com.musicdreamer.login.controller;

import com.musicdreamer.common.api.Mess;
import com.musicdreamer.common.auth.AuthContext;
import com.musicdreamer.login.dto.SingerApplyDTO;
import com.musicdreamer.login.dto.SingerAuditDTO;
import com.musicdreamer.login.service.SingerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/** 歌手认证接口：apply 登录；audit/applications 管理员。 */
@RestController
@RequestMapping("/api/v1/user/singer")
@RequiredArgsConstructor
public class SingerController {

    private final SingerService singerService;

    /** 提交歌手认证申请（登录）。 */
    @PostMapping("/apply")
    public Mess apply(@Valid @RequestBody SingerApplyDTO dto) {
        Long applicationId = singerService.apply(AuthContext.requireLogin(), dto);
        Map<String, Object> data = new HashMap<>();
        data.put("applicationId", applicationId);
        return Mess.ok(data);
    }

    /** 审核认证：通过/驳回（管理员）。 */
    @PostMapping("/audit")
    public Mess audit(@Valid @RequestBody SingerAuditDTO dto) {
        AuthContext.requireAdmin();
        singerService.audit(dto, AuthContext.requireLogin());
        return Mess.ok();
    }

    /** 认证申请列表（管理员，可按 status 筛选）。 */
    @GetMapping("/applications")
    public Mess applications(@RequestParam(required = false) Integer status) {
        AuthContext.requireAdmin();
        return Mess.ok(singerService.applications(status));
    }
}
