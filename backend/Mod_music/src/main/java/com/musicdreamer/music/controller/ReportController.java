package com.musicdreamer.music.controller;

import com.musicdreamer.common.api.Mess;
import com.musicdreamer.common.auth.AuthContext;
import com.musicdreamer.music.dto.ReportHandleDTO;
import com.musicdreamer.music.dto.ReportSubmitDTO;
import com.musicdreamer.music.service.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/** 举报控制器：/api/v1/report（提交需登录；列表/处置管理员，网关已配 ADMIN_PATHS）。 */
@RestController
@RequestMapping("/api/v1/report")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** 提交举报（登录）：歌曲/评论/歌单/动态。 */
    @PostMapping("/submit")
    public Mess submit(@Valid @RequestBody ReportSubmitDTO dto) {
        return Mess.ok(reportService.submit(dto));
    }

    /** 举报列表（管理员，分页，可按状态筛选）。 */
    @GetMapping("/list")
    public Mess list(@RequestParam(value = "page", defaultValue = "1") int page,
                     @RequestParam(value = "size", defaultValue = "20") int size,
                     @RequestParam(value = "status", required = false) Integer status) {
        return Mess.ok(reportService.list(page, size, status));
    }

    /** 处置举报（管理员）：action=confirm 确认违规（歌曲联动下架）/ dismiss 驳回。 */
    @PostMapping("/handle")
    public Mess handle(@Valid @RequestBody ReportHandleDTO dto) {
        reportService.handle(dto);
        return Mess.ok();
    }
}
