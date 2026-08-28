package com.musicdreamer.music.controller;

import com.musicdreamer.common.api.Mess;
import com.musicdreamer.common.auth.AuthContext;
import com.musicdreamer.music.dto.AuditDTO;
import com.musicdreamer.music.dto.SongEditDTO;
import com.musicdreamer.music.dto.SongSubmitDTO;
import com.musicdreamer.music.dto.TakedownDTO;
import com.musicdreamer.music.dto.VersionDTO;
import com.musicdreamer.music.service.SongService;
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
 * 歌曲控制器：/api/v1/song 前缀（网关已配置白名单/管理员/上传者鉴权）。
 * 对应前端契约：submit/play/detail/chart/edit/resubmit/mine/admin/audit/takedown/relist/versions。
 */
@RestController
@RequestMapping("/api/v1/song")
public class SongController {

    private final SongService songService;

    public SongController(SongService songService) {
        this.songService = songService;
    }

    /** 上传者提交歌曲（本地文件上传后或链接导入入库，status=1 审核中）。 */
    @PostMapping("/submit")
    public Mess submit(@Valid @RequestBody SongSubmitDTO dto) {
        return Mess.ok(songService.submit(dto));
    }

    /** 播放页数据（匿名可看：含 fileUrl/lyricUrl/响度增益）。 */
    @GetMapping("/play/{id}")
    public Mess play(@PathVariable("id") Long id) {
        return Mess.ok(songService.play(id));
    }

    /** 歌曲详情（匿名可看）。 */
    @GetMapping("/detail/{id}")
    public Mess detail(@PathVariable("id") Long id) {
        return Mess.ok(songService.detail(id));
    }

    /** 榜单：type=hot 热榜 / rise 周上升榜（匿名可看）。 */
    @GetMapping("/chart")
    public Mess chart(@RequestParam(value = "type", defaultValue = "hot") String type,
                      @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return Mess.ok(songService.chart(type, limit));
    }

    /** 编辑歌曲元数据（本人或管理员）。 */
    @PutMapping("/edit/{id}")
    public Mess edit(@PathVariable("id") Long id, @Valid @RequestBody SongEditDTO dto) {
        AuthContext.requireLogin();
        songService.edit(id, dto);
        return Mess.ok();
    }

    /** 驳回后重新提交审核（本人）。 */
    @PostMapping("/resubmit/{id}")
    public Mess resubmit(@PathVariable("id") Long id) {
        AuthContext.requireLogin();
        songService.resubmit(id);
        return Mess.ok();
    }

    /** 我上传的歌曲（分页，可按状态筛选）。 */
    @GetMapping("/mine")
    public Mess mine(@RequestParam(value = "page", defaultValue = "1") int page,
                     @RequestParam(value = "size", defaultValue = "10") int size,
                     @RequestParam(value = "status", required = false) Integer status) {
        AuthContext.requireLogin();
        return Mess.ok(songService.mine(page, size, status));
    }

    /** 管理端歌曲列表（管理员）。 */
    @GetMapping("/admin/list")
    public Mess adminList(@RequestParam(value = "page", defaultValue = "1") int page,
                          @RequestParam(value = "size", defaultValue = "10") int size,
                          @RequestParam(value = "status", required = false) Integer status,
                          @RequestParam(value = "keyword", required = false) String keyword) {
        AuthContext.requireAdmin();
        return Mess.ok(songService.adminList(page, size, status, keyword));
    }

    /** 审核（管理员）：pass=true 发布 / false 驳回记原因。 */
    @PostMapping("/audit")
    public Mess audit(@Valid @RequestBody AuditDTO dto) {
        AuthContext.requireAdmin();
        songService.audit(dto);
        return Mess.ok();
    }

    /** 下架（管理员），body: {reason}。 */
    @PostMapping("/takedown/{id}")
    public Mess takedown(@PathVariable("id") Long id, @RequestBody(required = false) TakedownDTO dto) {
        AuthContext.requireAdmin();
        songService.takedown(id, dto == null ? "违规下架" : dto.getReason());
        return Mess.ok();
    }

    /** 删除歌曲（管理员）：连带版本/评论/收藏。 */
    @PostMapping("/delete/{id}")
    public Mess delete(@PathVariable("id") Long id) {
        AuthContext.requireAdmin();
        songService.delete(id);
        return Mess.ok();
    }

    /** 重新上架（管理员）。 */
    @PostMapping("/relist/{id}")
    public Mess relist(@PathVariable("id") Long id) {
        AuthContext.requireAdmin();
        songService.relist(id);
        return Mess.ok();
    }

    /** 歌曲版本历史。 */
    @GetMapping("/versions/{songId}")
    public Mess versions(@PathVariable("songId") Long songId) {
        AuthContext.requireLogin();
        return Mess.ok(songService.versions(songId));
    }

    /** 新增版本（替换音频文件，重新进入审核）。 */
    @PostMapping("/version")
    public Mess addVersion(@Valid @RequestBody VersionDTO dto) {
        AuthContext.requireLogin();
        return Mess.ok(songService.addVersion(dto.getSongId(), dto));
    }
}
