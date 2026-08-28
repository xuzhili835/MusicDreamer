package com.musicdreamer.like.controller;

import com.musicdreamer.common.api.Mess;
import com.musicdreamer.common.auth.AuthContext;
import com.musicdreamer.like.dto.CollectAddDTO;
import com.musicdreamer.like.service.CollectService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 歌曲收藏控制器：/api/v1/collect 前缀，全部需登录。
 * 鉴权依赖网关透传 X-User-Id / X-User-Role。
 */
@RestController
@RequestMapping("/api/v1/collect")
public class CollectController {

    private final CollectService collectService;

    public CollectController(CollectService collectService) {
        this.collectService = collectService;
    }

    /** 收藏歌曲（幂等）。 */
    @PostMapping("/add")
    public Mess add(@Valid @RequestBody CollectAddDTO dto) {
        Long userId = AuthContext.requireLogin();
        return Mess.ok(collectService.add(userId, dto.getSongId()));
    }

    /** 取消收藏（幂等）。 */
    @DeleteMapping("/{songId}")
    public Mess remove(@PathVariable("songId") Long songId) {
        Long userId = AuthContext.requireLogin();
        collectService.remove(userId, songId);
        return Mess.ok();
    }

    /** 收藏列表（倒序分页，singer/style 筛选）。 */
    @GetMapping("/list")
    public Mess list(@RequestParam(value = "page", defaultValue = "1") long page,
                     @RequestParam(value = "size", defaultValue = "10") long size,
                     @RequestParam(value = "singer", required = false) String singer,
                     @RequestParam(value = "style", required = false) String style) {
        Long userId = AuthContext.requireLogin();
        return Mess.ok(collectService.list(userId, page, size, singer, style));
    }

    /** 当前用户收藏的 songId 数组（前端标记收藏态用）。 */
    @GetMapping("/ids")
    public Mess ids() {
        Long userId = AuthContext.requireLogin();
        return Mess.ok(collectService.ids(userId));
    }
}
