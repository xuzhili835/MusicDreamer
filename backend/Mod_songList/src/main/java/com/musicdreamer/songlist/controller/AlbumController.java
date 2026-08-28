package com.musicdreamer.songlist.controller;

import com.musicdreamer.common.api.Mess;
import com.musicdreamer.common.auth.AuthContext;
import com.musicdreamer.songlist.dto.AlbumCreateDTO;
import com.musicdreamer.songlist.service.AlbumService;
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
 * 专辑控制器：/api/v1/album 前缀。
 * 发布专辑仅限歌手/管理员（role>=1）；公开查询匿名可看（经网关 OPTIONAL_AUTH）。
 */
@RestController
@RequestMapping("/api/v1/album")
public class AlbumController {

    private final AlbumService albumService;

    public AlbumController(AlbumService albumService) {
        this.albumService = albumService;
    }

    /** 发布专辑（歌手/管理员）。 */
    @PostMapping("/create")
    public Mess create(@Valid @RequestBody AlbumCreateDTO dto) {
        Long userId = AuthContext.requireLogin();
        if (AuthContext.getRole() < 1) {
            return Mess.fail(2007, "仅歌手可发布专辑，请先完成歌手认证");
        }
        return Mess.ok(albumService.create(dto, userId));
    }

    /** 专辑详情（公开匿名可看，未发布仅主人）。 */
    @GetMapping("/{id}")
    public Mess detail(@PathVariable("id") Long id) {
        return Mess.ok(albumService.detail(id, AuthContext.getUserId()));
    }

    /** 删除专辑（主人）。 */
    @DeleteMapping("/{id}")
    public Mess delete(@PathVariable("id") Long id) {
        Long userId = AuthContext.requireLogin();
        albumService.delete(id, userId);
        return Mess.ok();
    }

    /** 添加歌曲到专辑（主人）。 */
    @PostMapping("/{id}/song/{songId}")
    public Mess addSong(@PathVariable("id") Long id, @PathVariable("songId") Long songId) {
        Long userId = AuthContext.requireLogin();
        return Mess.ok(albumService.addSong(id, songId, userId));
    }

    /** 从专辑移除歌曲（主人）。 */
    @DeleteMapping("/{id}/song/{songId}")
    public Mess removeSong(@PathVariable("id") Long id, @PathVariable("songId") Long songId) {
        Long userId = AuthContext.requireLogin();
        albumService.removeSong(id, songId, userId);
        return Mess.ok();
    }

    /** 我发布的专辑（歌手/管理员）。 */
    @GetMapping("/my")
    public Mess my() {
        Long userId = AuthContext.requireLogin();
        return Mess.ok(albumService.my(userId));
    }

    /** 我收藏的专辑（引用语义：源取消发布即消失）。 */
    @GetMapping("/favorites")
    public Mess favorites() {
        Long userId = AuthContext.requireLogin();
        return Mess.ok(albumService.favorites(userId));
    }

    /** 公开专辑分页（广场，登录时排除本人）。 */
    @GetMapping("/public/list")
    public Mess publicList(@RequestParam(value = "page", defaultValue = "1") long page,
                           @RequestParam(value = "size", defaultValue = "10") long size,
                           @RequestParam(value = "keyword", required = false) String keyword) {
        Long userId = AuthContext.getUserId();
        return Mess.ok(albumService.publicList(page, size, keyword, userId));
    }

    /** 收藏专辑（登录，幂等，不能收自己的）。 */
    @PostMapping("/{id}/favorite")
    public Mess favorite(@PathVariable("id") Long id) {
        Long userId = AuthContext.requireLogin();
        return Mess.ok(albumService.favorite(id, userId));
    }

    /** 取消收藏（登录，幂等）。 */
    @DeleteMapping("/{id}/favorite")
    public Mess unfavorite(@PathVariable("id") Long id) {
        Long userId = AuthContext.requireLogin();
        albumService.unfavorite(id, userId);
        return Mess.ok();
    }
}
