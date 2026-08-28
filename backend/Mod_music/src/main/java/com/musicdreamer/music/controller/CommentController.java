package com.musicdreamer.music.controller;

import com.musicdreamer.common.api.Mess;
import com.musicdreamer.common.auth.AuthContext;
import com.musicdreamer.music.dto.CommentAddDTO;
import com.musicdreamer.music.service.CommentService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/** 评论控制器：/api/v1/comment（列表匿名可看，操作需登录）。 */
@RestController
@RequestMapping("/api/v1/comment")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /** 歌曲评论列表：sort=hot|new，分页。 */
    @GetMapping("/list/{songId}")
    public Mess list(@PathVariable("songId") Long songId,
                     @RequestParam(value = "sort", defaultValue = "new") String sort,
                     @RequestParam(value = "page", defaultValue = "1") int page,
                     @RequestParam(value = "size", defaultValue = "20") int size) {
        return Mess.ok(commentService.list(songId, sort, page, size));
    }

    /** 发表评论（登录）。 */
    @PostMapping("/add")
    public Mess add(@Valid @RequestBody CommentAddDTO dto) {
        return Mess.ok(commentService.add(dto));
    }

    /** 删除自己的评论（登录）。 */
    @DeleteMapping("/{id}")
    public Mess delete(@PathVariable("id") Long id) {
        AuthContext.requireLogin();
        commentService.delete(id);
        return Mess.ok();
    }

    /** 评论点赞（登录）。 */
    @PostMapping("/{id}/like")
    public Mess like(@PathVariable("id") Long id) {
        AuthContext.requireLogin();
        commentService.like(id);
        return Mess.ok();
    }

    /** 取消点赞（登录）。 */
    @DeleteMapping("/{id}/like")
    public Mess unlike(@PathVariable("id") Long id) {
        AuthContext.requireLogin();
        commentService.unlike(id);
        return Mess.ok();
    }
}
