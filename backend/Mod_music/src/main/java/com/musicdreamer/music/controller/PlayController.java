package com.musicdreamer.music.controller;

import com.musicdreamer.common.api.Mess;
import com.musicdreamer.common.auth.AuthContext;
import com.musicdreamer.music.dto.PlayReportDTO;
import com.musicdreamer.music.service.PlayService;
import com.musicdreamer.music.util.WebUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/** 播放统计控制器：/api/v1/music（上报/最近播放/历史）。 */
@RestController
@RequestMapping("/api/v1/music")
public class PlayController {

    private final PlayService playService;

    public PlayController(PlayService playService) {
        this.playService = playService;
    }

    /** 播放上报（登录）：前端 audio ended 触发，播完才计。 */
    @PostMapping("/playReport")
    public Mess playReport(@Valid @RequestBody PlayReportDTO dto) {
        playService.playReport(dto, WebUtil.clientIp());
        return Mess.ok();
    }

    /** 最近播放（去重取最新）。 */
    @GetMapping("/recent")
    public Mess recent(@RequestParam(value = "limit", defaultValue = "10") int limit) {
        return Mess.ok(playService.recent(limit));
    }

    /** 播放历史明细分页。 */
    @GetMapping("/history")
    public Mess history(@RequestParam(value = "page", defaultValue = "1") int page,
                        @RequestParam(value = "size", defaultValue = "20") int size) {
        return Mess.ok(playService.history(page, size));
    }
}
