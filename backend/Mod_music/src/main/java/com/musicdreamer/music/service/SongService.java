package com.musicdreamer.music.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicdreamer.common.api.ErrorCode;
import com.musicdreamer.common.auth.AuthContext;
import com.musicdreamer.common.exception.BizException;
import com.musicdreamer.music.dto.AuditDTO;
import com.musicdreamer.music.dto.InternalCreateDTO;
import com.musicdreamer.music.dto.SongEditDTO;
import com.musicdreamer.music.dto.SongSubmitDTO;
import com.musicdreamer.music.dto.VersionDTO;
import com.musicdreamer.music.entity.Comment;
import com.musicdreamer.music.entity.Song;
import com.musicdreamer.music.entity.SongVersion;
import com.musicdreamer.music.mapper.AlbumLinkMapper;
import com.musicdreamer.music.mapper.CollectMapper;
import com.musicdreamer.music.mapper.CommentMapper;
import com.musicdreamer.music.mapper.PlayHistoryMapper;
import com.musicdreamer.music.mapper.SongMapper;
import com.musicdreamer.music.mapper.SongVersionMapper;
import com.musicdreamer.music.mapper.UserMapper;
import com.musicdreamer.music.util.CacheHelper;
import com.musicdreamer.music.vo.SongCardVO;
import com.musicdreamer.music.vo.SongManageVO;
import com.musicdreamer.music.vo.SongDetailVO;
import com.musicdreamer.music.vo.SongPlayVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 歌曲管理：提交/播放/详情/榜单/编辑/重提/我的/管理列表/审核/下架/上架/版本。
 * 审核状态机（设计 6.1 节）：入库 1 → 通过 2 / 驳回保持 1 记原因 → 下架 0 → 重新上架 2。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SongService {

    // findAndRegisterModules 自动带上 JSR310（LocalDateTime）等模块，裸 ObjectMapper 转换会抛 IllegalArgumentException
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    /** 歌曲状态常量。 */
    public static final int STATUS_TAKEN_DOWN = 0;
    public static final int STATUS_UNDER_REVIEW = 1;
    public static final int STATUS_PUBLISHED = 2;

    private static final Duration CHART_TTL = Duration.ofMinutes(10);

    private final SongMapper songMapper;
    private final SongVersionMapper songVersionMapper;
    private final PlayHistoryMapper playHistoryMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final CollectMapper collectMapper;
    private final AlbumLinkMapper albumLinkMapper;
    private final CacheHelper cache;
    private final OperationLogService operationLogService;
    private final MediaFileCleanup fileCleanup;

    /** 提交歌曲（role>=1），入库即审核中。 */
    public Long submit(SongSubmitDTO dto) {
        AuthContext.requireUploader();
        Long singerId = AuthContext.requireLogin();

        if (StringUtils.hasText(dto.getSourceUrl())
                && songMapper.selectCount(new LambdaQueryWrapper<Song>().eq(Song::getSourceUrl, dto.getSourceUrl())) > 0) {
            throw new BizException(2002, "该链接已导入过");
        }

        Song song = new Song();
        song.setName(dto.getName());
        song.setSingerId(singerId);
        song.setAlbum(dto.getAlbum());
        song.setStyle(dto.getStyle());
        song.setLanguage(dto.getLanguage());
        song.setDuration(dto.getDuration() == null ? 0 : dto.getDuration());
        song.setFileUrl(dto.getFileUrl());
        song.setCoverUrl(dto.getCoverUrl());
        song.setLyricUrl(dto.getLyricUrl());
        song.setFileFormat(dto.getFileFormat());
        song.setSourceUrl(dto.getSourceUrl());
        if (StringUtils.hasText(dto.getSingerName())) {
            song.setSingerName(dto.getSingerName().trim());
        }
        song.setPlayCount(0L);
        song.setCollectCount(0);
        // 管理员提交免审直接发布（自己审自己没意义）；歌手提交仍走审核流程
        song.setStatus(AuthContext.getRole() == AuthContext.ROLE_ADMIN ? STATUS_PUBLISHED : STATUS_UNDER_REVIEW);
        songMapper.insert(song);
        // 免审发布改变可见列表，需失效热榜缓存，否则用户端最长 10 分钟看不到
        if (song.getStatus() == STATUS_PUBLISHED) {
            evictHotChart();
        }
        syncAlbumMembership(song.getId(), dto.getAlbum(), song.getStatus() == STATUS_PUBLISHED);
        log.info("song submitted: id={}, singer={}, name={}", song.getId(), singerId, song.getName());
        return song.getId();
    }

    /** 播放：按状态拦截（1→3002，0→3003，查无→3001）。 */
    public SongPlayVO play(Long id) {
        Song song = mustGet(id);
        if (song.getStatus() == STATUS_UNDER_REVIEW) {
            throw new BizException(ErrorCode.SONG_UNDER_REVIEW);
        }
        if (song.getStatus() == STATUS_TAKEN_DOWN) {
            throw new BizException(ErrorCode.SONG_TAKEN_DOWN);
        }
        SongPlayVO vo = songMapper.selectPlayById(id);
        if (vo == null) {
            throw new BizException(ErrorCode.SONG_NOT_FOUND);
        }
        return vo;
    }

    /** 详情（匿名可见）。 */
    public SongDetailVO detail(Long id) {
        SongDetailVO vo = songMapper.selectDetailById(id);
        if (vo == null) {
            throw new BizException(ErrorCode.SONG_NOT_FOUND);
        }
        return vo;
    }

    /** 榜单：hot 热门榜（Redis 缓存 10min，异常降级直查）/ rise 周榜。 */
    public Object chart(String type, int limit) {
        if (type == null || type.isBlank()) {
            type = "hot";
        }
        if ("hot".equals(type)) {
            String key = "chart:hot:" + limit;
            List<SongCardVO> cached = cache.get(key, new TypeReference<List<SongCardVO>>() {});
            if (cached != null) {
                return cached;
            }
            List<SongCardVO> list = songMapper.selectHotCards(limit);
            cache.set(key, list, CHART_TTL);
            return list;
        }
        if ("rise".equals(type)) {
            return playHistoryMapper.selectRise(limit);
        }
        throw new BizException(ErrorCode.PARAM_FORMAT_ERROR);
    }

    /** 编辑元数据：本人或管理员。 */
    public void edit(Long id, SongEditDTO dto) {
        Song song = mustGet(id);
        requireOwnerOrAdmin(song);

        LambdaUpdateWrapper<Song> uw = new LambdaUpdateWrapper<Song>().eq(Song::getId, id);
        if (StringUtils.hasText(dto.getName())) uw.set(Song::getName, dto.getName());
        if (dto.getAlbum() != null) uw.set(Song::getAlbum, dto.getAlbum());
        if (dto.getStyle() != null) uw.set(Song::getStyle, dto.getStyle());
        if (dto.getLanguage() != null) uw.set(Song::getLanguage, dto.getLanguage());
        if (dto.getCoverUrl() != null) uw.set(Song::getCoverUrl, dto.getCoverUrl());
        if (StringUtils.hasText(dto.getLyricUrl())) uw.set(Song::getLyricUrl, dto.getLyricUrl());
        if (dto.getSingerName() != null) uw.set(Song::getSingerName, dto.getSingerName().trim());
        songMapper.update(null, uw);
        // 歌名/歌手/封面都是榜单卡片展示字段，编辑后必须失效热榜缓存（否则首页最长 10 分钟仍显示旧值）
        evictHotChart();
        // 填了专辑名且存在同名专辑 → 自动挂进该专辑（用户预期：设置专辑=加入专辑）
        syncAlbumMembership(id, dto.getAlbum(), song.getStatus() == STATUS_PUBLISHED);
        operationLogService.record("EDIT", "PUT",
                Map.of("songId", id, "singerId", song.getSingerId()));
    }

    /** 驳回后重新提交：清 reject_reason，保持待审。 */
    public void resubmit(Long id) {
        Song song = mustGet(id);
        requireOwnerOrAdmin(song);
        if (song.getStatus() != STATUS_UNDER_REVIEW || !StringUtils.hasText(song.getRejectReason())) {
            throw new BizException(ErrorCode.PARAM_OUT_OF_RANGE, "仅被驳回的歌曲可重新提交");
        }
        songMapper.update(null, new LambdaUpdateWrapper<Song>()
                .eq(Song::getId, id)
                .set(Song::getRejectReason, null));
    }

    /** 我上传的歌（只看本人，管理员想看全量走 adminList；联 user 带歌手昵称）。 */
    public Map<String, Object> mine(int page, int size, Integer status) {
        AuthContext.requireLogin();
        Page<SongManageVO> pager = new Page<>(page, size);
        songMapper.selectManagePage(pager, AuthContext.getUserId(), status, null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", pager.getTotal());
        result.put("songs", pager.getRecords());
        return result;
    }

    /** 管理端全量列表（联 user 带歌手昵称；附当前过滤条件下的播放总数汇总）。 */
    public Map<String, Object> adminList(int page, int size, Integer status, String keyword) {
        AuthContext.requireAdmin();
        Page<SongManageVO> pager = new Page<>(page, size);
        songMapper.selectManagePage(pager, null, status, keyword);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", pager.getTotal());
        result.put("songs", pager.getRecords());
        result.put("totalPlays", songMapper.sumPlayCount(status, keyword));
        return result;
    }

    /** 审核：通过→2 记 auditor/audit_time；驳回→保持 1 记原因。 */
    public void audit(AuditDTO dto) {
        Long adminId = AuthContext.requireLogin();
        AuthContext.requireAdmin();
        Song song = mustGet(dto.getSongId());
        if (Boolean.TRUE.equals(dto.getPass())) {
            songMapper.update(null, new LambdaUpdateWrapper<Song>()
                    .eq(Song::getId, song.getId())
                    .set(Song::getStatus, STATUS_PUBLISHED)
                    .set(Song::getAuditorId, adminId)
                    .set(Song::getAuditTime, LocalDateTime.now())
                    .set(Song::getRejectReason, null));
            evictHotChart();
        } else {
            if (!StringUtils.hasText(dto.getRejectReason())) {
                throw new BizException(ErrorCode.PARAM_MISSING, "驳回原因不能为空");
            }
            songMapper.update(null, new LambdaUpdateWrapper<Song>()
                    .eq(Song::getId, song.getId())
                    .set(Song::getStatus, STATUS_UNDER_REVIEW)
                    .set(Song::getRejectReason, dto.getRejectReason()));
        }
        operationLogService.record("AUDIT", "POST", Map.of(
                "songId", song.getId(),
                "pass", Boolean.TRUE.equals(dto.getPass()),
                "rejectReason", dto.getRejectReason() == null ? "" : dto.getRejectReason()));
    }

    /** 下架：status=0，原因记入 reject_reason。 */
    public void takedown(Long id, String reason) {
        AuthContext.requireAdmin();
        Song song = mustGet(id);
        songMapper.update(null, new LambdaUpdateWrapper<Song>()
                .eq(Song::getId, id)
                .set(Song::getStatus, STATUS_TAKEN_DOWN)
                .set(Song::getRejectReason, reason));
        evictHotChart();
        operationLogService.record("TAKEDOWN", "POST", Map.of("songId", id, "reason", reason));
        log.info("song taken down: id={}, operator={}", id, AuthContext.getUserId());
    }

    /** 重新上架：status=2。 */
    public void relist(Long id) {
        AuthContext.requireAdmin();
        mustGet(id);
        songMapper.update(null, new LambdaUpdateWrapper<Song>()
                .eq(Song::getId, id)
                .set(Song::getStatus, STATUS_PUBLISHED)
                .set(Song::getRejectReason, null));
        evictHotChart();
        operationLogService.record("RELIST", "POST", Map.of("songId", id));
    }

    /** 删除歌曲（管理员）：连带版本/评论/收藏，播放历史保留；
     *  音频/封面/歌词文件经 Mod_media 内部接口一并删除（此前只删 DB 行，
     *  磁盘文件全部残留成孤儿，"删除实际上没删"的根源）。 */
    public void delete(Long id) {
        AuthContext.requireAdmin();
        Song song = mustGet(id);
        songMapper.deleteById(id);
        songVersionMapper.delete(new LambdaQueryWrapper<SongVersion>().eq(SongVersion::getSongId, id));
        commentMapper.delete(new LambdaQueryWrapper<Comment>().eq(Comment::getSongId, id));
        collectMapper.deleteBySongId(id);
        evictHotChart();
        operationLogService.record("DELETE_SONG", "POST", Map.of("songId", id));
        log.info("song deleted: id={}, name={}, operator={}", id, song.getName(), AuthContext.getUserId());
        // DB 已删，文件清理放最后：失败不阻断删除（残留最多是孤儿文件）
        fileCleanup.deleteFiles(song.getFileUrl(), song.getCoverUrl(), song.getLyricUrl());
    }

    /** 版本列表（本人或管理员）。 */
    public List<SongVersion> versions(Long songId) {
        Song song = mustGet(songId);
        requireOwnerOrAdmin(song);
        return songVersionMapper.selectList(new LambdaQueryWrapper<SongVersion>()
                .eq(SongVersion::getSongId, songId)
                .orderByDesc(SongVersion::getVersion));
    }

    /** 追加版本：version=max+1，进入版本审核中（song_version 语义 status=2）。 */
    public Long addVersion(Long songId, VersionDTO dto) {
        Song song = mustGet(songId);
        requireOwnerOrAdmin(song);

        SongVersion last = songVersionMapper.selectOne(new LambdaQueryWrapper<SongVersion>()
                .eq(SongVersion::getSongId, songId)
                .orderByDesc(SongVersion::getVersion)
                .last("LIMIT 1"));

        SongVersion v = new SongVersion();
        v.setSongId(songId);
        v.setVersion(last == null ? 1 : last.getVersion() + 1);
        v.setFileUrl(dto.getFileUrl());
        v.setFileFormat(dto.getFileFormat());
        v.setFileSize(dto.getFileSize() == null ? 0L : dto.getFileSize());
        v.setDuration(dto.getDuration() == null ? 0 : dto.getDuration());
        v.setStatus(2);
        songVersionMapper.insert(v);
        return v.getId();
    }

    /** ---------- 内部接口（Feign，无鉴权） ---------- */

    public Song internalGet(Long id) {
        return mustGet(id);
    }

    public List<Map<String, Object>> internalBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<Song> songs = songMapper.selectBatchIds(ids);
        if (songs.isEmpty()) {
            return new ArrayList<>();
        }
        // 联一次用户表补 singerName：歌单/专辑详情拼装歌曲行时直接可读（旧消费方按 key 取值，向后兼容）
        Set<Long> singerIds = songs.stream()
                .map(Song::getSingerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>();
        if (!singerIds.isEmpty()) {
            for (Map<String, Object> row : userMapper.selectNamesByIds(new ArrayList<>(singerIds))) {
                Object id = row.get("id");
                Object name = row.get("name");
                if (id instanceof Number && name != null) {
                    names.put(((Number) id).longValue(), String.valueOf(name));
                }
            }
        }
        List<Map<String, Object>> result = new ArrayList<>(songs.size());
        for (Song song : songs) {
            Map<String, Object> row = MAPPER.convertValue(song, new TypeReference<Map<String, Object>>() {});
            row.put("singerName", StringUtils.hasText(song.getSingerName())
                    ? song.getSingerName() : names.get(song.getSingerId()));
            result.add(row);
        }
        return result;
    }

    public Song internalBySource(String url) {
        if (!StringUtils.hasText(url)) {
            throw new BizException(ErrorCode.PARAM_MISSING, "url 不能为空");
        }
        Song song = songMapper.selectOne(new LambdaQueryWrapper<Song>()
                .eq(Song::getSourceUrl, url).last("LIMIT 1"));
        if (song == null) {
            throw new BizException(ErrorCode.SONG_NOT_FOUND);
        }
        return song;
    }

    public Long internalCreate(InternalCreateDTO dto) {
        if (StringUtils.hasText(dto.getSourceUrl())
                && songMapper.selectCount(new LambdaQueryWrapper<Song>().eq(Song::getSourceUrl, dto.getSourceUrl())) > 0) {
            throw new BizException(2002, "该链接已导入过");
        }
        Song song = new Song();
        song.setName(dto.getName());
        song.setSingerId(dto.getSingerId());
        song.setAlbum(null);
        song.setDuration(dto.getDuration() == null ? 0 : dto.getDuration());
        song.setFileUrl(dto.getFileUrl());
        song.setCoverUrl(dto.getCoverUrl());
        song.setFileFormat(dto.getFileFormat());
        song.setSourceUrl(dto.getSourceUrl());
        if (StringUtils.hasText(dto.getSingerName())) {
            song.setSingerName(dto.getSingerName().trim());
        }
        if (StringUtils.hasText(dto.getStyle())) {
            song.setStyle(dto.getStyle().trim());
        }
        song.setPlayCount(0L);
        song.setCollectCount(0);
        // 与手动上传(submit)一致：操作者是管理员时直接发布，免去人工审核
        Integer operatorRole = dto.getSingerId() == null ? null : userMapper.selectRole(dto.getSingerId());
        boolean byAdmin = operatorRole != null && operatorRole == AuthContext.ROLE_ADMIN;
        song.setStatus(byAdmin ? STATUS_PUBLISHED : STATUS_UNDER_REVIEW);
        songMapper.insert(song);
        // 管理员导入直接发布同样改变可见列表，需失效热榜缓存（与 submit 同理）
        if (byAdmin) {
            evictHotChart();
        }
        log.info("song created by media pipeline: id={}, source={}", song.getId(), dto.getSourceUrl());
        return song.getId();
    }

    public void internalCollectCount(Long songId, int delta) {
        mustGet(songId);
        songMapper.adjustCollectCount(songId, delta);
    }

    public void internalLoudness(Long songId, Double gain, Double integrated) {
        mustGet(songId);
        LambdaUpdateWrapper<Song> uw = new LambdaUpdateWrapper<Song>().eq(Song::getId, songId);
        if (gain != null) uw.set(Song::getVolumeGain, gain);
        if (integrated != null) uw.set(Song::getIntegratedLoudness, integrated);
        if (gain != null || integrated != null) {
            songMapper.update(null, uw);
        }
    }

    /** 音频/封面正式地址回写（Feign，下载落位 music/{id}.mp3 后，原地址指向 task 临时文件会被清理）。 */
    public void internalFile(Long songId, String fileUrl, String coverUrl) {
        mustGet(songId);
        LambdaUpdateWrapper<Song> uw = new LambdaUpdateWrapper<Song>().eq(Song::getId, songId);
        if (fileUrl != null) uw.set(Song::getFileUrl, fileUrl);
        if (coverUrl != null) uw.set(Song::getCoverUrl, coverUrl);
        if (fileUrl != null || coverUrl != null) {
            songMapper.update(null, uw);
        }
    }

    public void internalLyric(Long songId, String lyricUrl) {
        mustGet(songId);
        songMapper.update(null, new LambdaUpdateWrapper<Song>()
                .eq(Song::getId, songId)
                .set(Song::getLyricUrl, lyricUrl));
    }

    /** 在线词库命中信息回写（Feign，歌词任务用；复盘时间戳对照）。 */
    public void internalLyricSource(Long songId, Long sourceId, String sourceUrl) {
        mustGet(songId);
        songMapper.update(null, new LambdaUpdateWrapper<Song>()
                .eq(Song::getId, songId)
                .set(Song::getLyricSourceId, sourceId)
                .set(Song::getLyricSourceUrl, sourceUrl));
    }

    /** ---------- 私有 ---------- */

    /** 可见性变化（发布/下架/上架）后失效热榜缓存，key 形如 chart:hot:{limit}。 */
    private void evictHotChart() {
        cache.evictByPrefix("chart:hot:");
    }

    /** 专辑字段联动：填了专辑名且存在同名专辑则幂等挂入（仅已发布歌曲，与专辑页加歌的校验一致）。 */
    private void syncAlbumMembership(Long songId, String albumName, boolean published) {
        if (!published || !StringUtils.hasText(albumName)) {
            return;
        }
        for (Long albumId : albumLinkMapper.matchAlbumIds(albumName.trim())) {
            albumLinkMapper.insertIfAbsent(albumId, songId);
        }
    }

    private Song mustGet(Long id) {
        Song song = songMapper.selectById(id);
        if (song == null) {
            throw new BizException(ErrorCode.SONG_NOT_FOUND);
        }
        return song;
    }

    private void requireOwnerOrAdmin(Song song) {
        Long uid = AuthContext.requireLogin();
        if (AuthContext.getRole() != AuthContext.ROLE_ADMIN && !song.getSingerId().equals(uid)) {
            throw new BizException(ErrorCode.NO_PERMISSION);
        }
    }
}
