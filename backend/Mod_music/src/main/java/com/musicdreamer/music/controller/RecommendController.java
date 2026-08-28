package com.musicdreamer.music.controller;

import com.musicdreamer.common.api.Mess;
import com.musicdreamer.common.auth.AuthContext;
import com.musicdreamer.music.service.RecommendService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 推荐控制器：/api/v1/recommend（匿名可看：无历史/未登录返回热榜兜底）。 */
@RestController
@RequestMapping("/api/v1/recommend")
public class RecommendController {

    private final RecommendService recommendService;

    public RecommendController(RecommendService recommendService) {
        this.recommendService = recommendService;
    }

    /** 个性化推荐（ItemCF），登录且有播放历史时生效，否则热榜。 */
    @GetMapping("/list")
    public Mess list(@RequestParam(value = "limit", defaultValue = "10") int limit) {
        return Mess.ok(recommendService.listFor(limit));
    }

    /** 重算相似度矩阵（管理员）。 */
    @PostMapping("/recompute")
    public Mess recompute() {
        AuthContext.requireAdmin();
        return Mess.ok(recommendService.recompute());
    }
}
