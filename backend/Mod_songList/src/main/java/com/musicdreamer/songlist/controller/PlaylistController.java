package com.musicdreamer.songlist.controller;

import com.musicdreamer.common.api.Mess;
import com.musicdreamer.common.auth.AuthContext;
import com.musicdreamer.songlist.dto.PlaylistCreateDTO;
import com.musicdreamer.songlist.dto.PlaylistUpdateDTO;
import com.musicdreamer.songlist.dto.SongAddDTO;
import com.musicdreamer.songlist.service.PlaylistService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 歌单控制器：/api/v1/playlist 前缀。
 * 鉴权依赖网关透传 X-User-Id / X-User-Role，需登录接口用 AuthContext.requireLogin()。
 */
@RestController
@RequestMapping("/api/v1/playlist")
public class PlaylistController {

    private final PlaylistService playlistService;

    public PlaylistController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    /** 创建歌单（登录）。 */
    @PostMapping("/create")
    public Mess create(@Valid @RequestBody PlaylistCreateDTO dto) {
        Long userId = AuthContext.requireLogin();
        return Mess.ok(playlistService.create(dto, userId));
    }

    /** 更新歌单（登录，仅创建者）。 */
    @PutMapping("/update/{id}")
    public Mess update(@PathVariable("id") Long id, @Valid @RequestBody PlaylistUpdateDTO dto) {
        Long userId = AuthContext.requireLogin();
        playlistService.update(id, dto, userId);
        return Mess.ok();
    }

    /** 删除歌单（登录，仅创建者），级联清理关联数据。 */
    @DeleteMapping("/{id}")
    public Mess delete(@PathVariable("id") Long id) {
        Long userId = AuthContext.requireLogin();
        playlistService.delete(id, userId);
        return Mess.ok();
    }

    /** 歌单添加歌曲（登录，仅创建者）。 */
    @PostMapping("/{id}/songs")
    public Mess addSong(@PathVariable("id") Long id, @Valid @RequestBody SongAddDTO dto) {
        Long userId = AuthContext.requireLogin();
        return Mess.ok(playlistService.addSong(id, dto.getSongId(), userId));
    }

    /** 移除歌单歌曲（登录，仅创建者）。 */
    @DeleteMapping("/{id}/songs/{songId}")
    public Mess removeSong(@PathVariable("id") Long id, @PathVariable("songId") Long songId) {
        Long userId = AuthContext.requireLogin();
        playlistService.removeSong(id, songId, userId);
        return Mess.ok();
    }

    /** 歌单详情+歌曲列表（公开歌单匿名可看，私有仅创建者）。 */
    @GetMapping("/{id}")
    public Mess detail(@PathVariable("id") Long id) {
        Long userId = AuthContext.getUserId();
        return Mess.ok(playlistService.detail(id, userId));
    }

    /** 我创建的 + 我收藏的歌单（登录）。 */
    @GetMapping("/my")
    public Mess my() {
        Long userId = AuthContext.requireLogin();
        return Mess.ok(playlistService.my(userId));
    }

    /** 收藏公开歌单（登录，幂等）。 */
    @PostMapping("/{id}/favorite")
    public Mess favorite(@PathVariable("id") Long id) {
        Long userId = AuthContext.requireLogin();
        return Mess.ok(playlistService.favorite(id, userId));
    }

    /** 取消收藏（登录，幂等）。 */
    @DeleteMapping("/{id}/favorite")
    public Mess unfavorite(@PathVariable("id") Long id) {
        Long userId = AuthContext.requireLogin();
        playlistService.unfavorite(id, userId);
        return Mess.ok();
    }

    /** 公开歌单分页（广场/搜索，keyword 匹配名称，匿名可看）。 */
    @GetMapping("/public/list")
    public Mess publicList(@RequestParam(value = "page", defaultValue = "1") long page,
                           @RequestParam(value = "size", defaultValue = "10") long size,
                           @RequestParam(value = "keyword", required = false) String keyword) {
        // 广场看别人的歌单：登录时排除自己创建的（匿名时无身份，全量公开歌单）
        Long userId = AuthContext.getUserId();
        return Mess.ok(playlistService.publicList(page, size, keyword, userId));
    }
}
