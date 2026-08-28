package com.musicdreamer.songlist.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.musicdreamer.common.api.ErrorCode;
import com.musicdreamer.common.exception.BizException;
import com.musicdreamer.songlist.dto.PlaylistCreateDTO;
import com.musicdreamer.songlist.dto.PlaylistUpdateDTO;
import com.musicdreamer.songlist.entity.Playlist;
import com.musicdreamer.songlist.entity.PlaylistSong;
import com.musicdreamer.songlist.entity.UserFavoritePlaylist;
import com.musicdreamer.songlist.mapper.PlaylistMapper;
import com.musicdreamer.songlist.mapper.PlaylistSongMapper;
import com.musicdreamer.songlist.mapper.UserFavoritePlaylistMapper;
import com.musicdreamer.songlist.mapper.UserMapper;
import com.musicdreamer.songlist.service.PlaylistService;
import com.musicdreamer.songlist.service.SongRemoteService;
import com.musicdreamer.songlist.vo.PlaylistDetailVO;
import com.musicdreamer.songlist.vo.PlaylistSongVO;
import com.musicdreamer.songlist.vo.PlaylistVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PlaylistServiceImpl implements PlaylistService {

    /** 歌单域内部错误码：歌单不存在。 */
    private static final int PLAYLIST_NOT_FOUND = 6001;

    private final PlaylistMapper playlistMapper;
    private final PlaylistSongMapper playlistSongMapper;
    private final UserFavoritePlaylistMapper favoriteMapper;
    private final SongRemoteService songRemoteService;
    private final UserMapper userMapper;

    public PlaylistServiceImpl(PlaylistMapper playlistMapper,
                               PlaylistSongMapper playlistSongMapper,
                               UserFavoritePlaylistMapper favoriteMapper,
                               SongRemoteService songRemoteService,
                               UserMapper userMapper) {
        this.playlistMapper = playlistMapper;
        this.playlistSongMapper = playlistSongMapper;
        this.favoriteMapper = favoriteMapper;
        this.songRemoteService = songRemoteService;
        this.userMapper = userMapper;
    }

    // ---------- 歌单 CRUD ----------

    @Override
    public Map<String, Object> create(PlaylistCreateDTO dto, Long userId) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_MISSING, "歌单名称不能为空");
        }
        Playlist p = new Playlist();
        p.setUserId(userId);
        p.setName(dto.getName().trim());
        p.setDescription(dto.getDescription());
        p.setIsPublic(dto.getIsPublic() == null ? Boolean.TRUE : dto.getIsPublic());
        playlistMapper.insert(p);
        // 回查一次拿 DB 生成的 create_time
        Playlist saved = playlistMapper.selectById(p.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("playlistId", p.getId());
        result.put("createTime", saved == null ? LocalDateTime.now() : saved.getCreateTime());
        return result;
    }

    @Override
    public void update(Long id, PlaylistUpdateDTO dto, Long userId) {
        Playlist p = requireOwnedPlaylist(id, userId);
        if (dto.getName() != null) {
            if (dto.getName().isBlank()) {
                throw new BizException(ErrorCode.PARAM_MISSING, "歌单名称不能为空");
            }
            p.setName(dto.getName().trim());
        }
        if (dto.getDescription() != null) {
            p.setDescription(dto.getDescription());
        }
        if (dto.getIsPublic() != null) {
            p.setIsPublic(dto.getIsPublic());
        }
        if (dto.getCoverUrl() != null) {
            p.setCoverUrl(dto.getCoverUrl());
        }
        playlistMapper.updateById(p);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        requireOwnedPlaylist(id, userId);
        // 级联清理：歌单歌曲关联 + 用户收藏记录
        playlistSongMapper.delete(new LambdaQueryWrapper<PlaylistSong>()
                .eq(PlaylistSong::getPlaylistId, id));
        favoriteMapper.delete(new LambdaQueryWrapper<UserFavoritePlaylist>()
                .eq(UserFavoritePlaylist::getPlaylistId, id));
        playlistMapper.deleteById(id);
    }

    // ---------- 歌单歌曲管理 ----------

    @Override
    public Map<String, Object> addSong(Long id, Long songId, Long userId) {
        requireOwnedPlaylist(id, userId);
        // Feign 校验歌曲存在且已发布（status=2）
        songRemoteService.requirePublishedSong(songId);

        // 唯一键 (playlist_id, song_id) 幂等：已存在直接成功
        Long exists = playlistSongMapper.selectCount(new LambdaQueryWrapper<PlaylistSong>()
                .eq(PlaylistSong::getPlaylistId, id)
                .eq(PlaylistSong::getSongId, songId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("songId", songId);
        if (exists != null && exists > 0) {
            result.put("duplicated", true);
            return result;
        }

        // sort_order = 当前最大 + 1（空歌单从 0 开始）
        PlaylistSong last = playlistSongMapper.selectOne(new LambdaQueryWrapper<PlaylistSong>()
                .eq(PlaylistSong::getPlaylistId, id)
                .orderByDesc(PlaylistSong::getSortOrder)
                .last("LIMIT 1"));
        int nextOrder = last == null || last.getSortOrder() == null ? 0 : last.getSortOrder() + 1;

        PlaylistSong link = new PlaylistSong();
        link.setPlaylistId(id);
        link.setSongId(songId);
        link.setSortOrder(nextOrder);
        try {
            playlistSongMapper.insert(link);
        } catch (DuplicateKeyException e) {
            // 并发重复添加：唯一键兜底，幂等成功
            log.info("song {} duplicated in playlist {}, idempotent ok", songId, id);
            result.put("duplicated", true);
            result.put("sortOrder", nextOrder);
            return result;
        }
        result.put("sortOrder", nextOrder);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeSong(Long id, Long songId, Long userId) {
        requireOwnedPlaylist(id, userId);
        playlistSongMapper.delete(new LambdaQueryWrapper<PlaylistSong>()
                .eq(PlaylistSong::getPlaylistId, id)
                .eq(PlaylistSong::getSongId, songId));
        compressSortOrder(id);
    }

    /** 移除歌曲后按 (playlist_id, sort_order) 压缩序号，保证连续。 */
    private void compressSortOrder(Long playlistId) {
        List<PlaylistSong> rest = playlistSongMapper.selectList(new LambdaQueryWrapper<PlaylistSong>()
                .eq(PlaylistSong::getPlaylistId, playlistId)
                .orderByAsc(PlaylistSong::getSortOrder)
                .orderByAsc(PlaylistSong::getId));
        int order = 0;
        for (PlaylistSong row : rest) {
            if (row.getSortOrder() == null || row.getSortOrder() != order) {
                row.setSortOrder(order);
                playlistSongMapper.updateById(row);
            }
            order++;
        }
    }

    // ---------- 查询 ----------

    @Override
    public PlaylistDetailVO detail(Long id, Long userId) {
        Playlist p = requirePlaylist(id);
        boolean owner = userId != null && userId.equals(p.getUserId());
        // 可见性：私有歌单仅创建者可见
        if (!Boolean.TRUE.equals(p.getIsPublic()) && !owner) {
            throw new BizException(ErrorCode.NO_PERMISSION);
        }

        List<PlaylistSong> links = playlistSongMapper.selectList(new LambdaQueryWrapper<PlaylistSong>()
                .eq(PlaylistSong::getPlaylistId, id)
                .orderByAsc(PlaylistSong::getSortOrder)
                .orderByAsc(PlaylistSong::getId));

        List<Long> songIds = links.stream().map(PlaylistSong::getSongId).collect(Collectors.toList());
        Map<Long, Map<String, Object>> songs = songRemoteService.batchSongs(songIds);

        List<PlaylistSongVO> songVOs = new ArrayList<>(links.size());
        for (PlaylistSong link : links) {
            PlaylistSongVO vo = new PlaylistSongVO();
            vo.setSongId(link.getSongId());
            vo.setSortOrder(link.getSortOrder());
            vo.setAddTime(link.getCreateTime());
            Map<String, Object> song = songs.get(link.getSongId());
            if (song != null) {
                vo.setName(songRemoteService.str(song, "name"));
                vo.setSingerId(songRemoteService.lng(song, "singerId"));
                vo.setSingerName(songRemoteService.singerName(song));
                vo.setCoverUrl(songRemoteService.str(song, "coverUrl"));
                vo.setDuration(songRemoteService.integer(song, "duration"));
                vo.setStyle(songRemoteService.str(song, "style"));
                vo.setAlbum(songRemoteService.str(song, "album"));
                vo.setStatus(songRemoteService.integer(song, "status"));
            }
            songVOs.add(vo);
        }

        PlaylistDetailVO vo = new PlaylistDetailVO();
        vo.setId(p.getId());
        vo.setUserId(p.getUserId());
        vo.setName(p.getName());
        vo.setDescription(p.getDescription());
        vo.setCoverUrl(p.getCoverUrl());
        vo.setIsPublic(p.getIsPublic());
        vo.setCreateTime(p.getCreateTime());
        vo.setUpdateTime(p.getUpdateTime());
        vo.setSongCount(songVOs.size());
        vo.setFavored(userId != null && isFavored(userId, id));
        vo.setSongs(songVOs);
        vo.setCreatorName(userNames(List.of(p.getUserId())).get(p.getUserId()));
        return vo;
    }

    @Override
    public Map<String, Object> my(Long userId) {
        List<Playlist> created = playlistMapper.selectList(new LambdaQueryWrapper<Playlist>()
                .eq(Playlist::getUserId, userId)
                .orderByDesc(Playlist::getCreateTime)
                .orderByDesc(Playlist::getId));

        List<UserFavoritePlaylist> favorites = favoriteMapper.selectList(
                new LambdaQueryWrapper<UserFavoritePlaylist>()
                        .eq(UserFavoritePlaylist::getUserId, userId)
                        .orderByDesc(UserFavoritePlaylist::getCreateTime)
                        .orderByDesc(UserFavoritePlaylist::getId));

        Map<Long, LocalDateTime> favoriteTimeByPlaylist = favorites.stream()
                .collect(Collectors.toMap(UserFavoritePlaylist::getPlaylistId,
                        UserFavoritePlaylist::getCreateTime, (a, b) -> a, LinkedHashMap::new));

        List<Playlist> favorited = favoriteTimeByPlaylist.isEmpty()
                ? Collections.emptyList()
                : playlistMapper.selectBatchIds(favoriteTimeByPlaylist.keySet())
                .stream()
                // 收藏是引用而非快照：源歌单取消公开后从收藏区消失（删除时收藏记录已级联清理）
                .filter(p -> Boolean.TRUE.equals(p.getIsPublic()) && !userId.equals(p.getUserId()))
                .sorted((a, b) -> {
                    LocalDateTime ta = favoriteTimeByPlaylist.get(a.getId());
                    LocalDateTime tb = favoriteTimeByPlaylist.get(b.getId());
                    return tb.compareTo(ta);
                })
                .collect(Collectors.toList());

        List<Long> allIds = new ArrayList<>();
        created.forEach(p -> allIds.add(p.getId()));
        favorited.forEach(p -> allIds.add(p.getId()));
        Map<Long, Long> counts = songCounts(allIds);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", created.stream()
                .map(p -> toVO(p, counts.getOrDefault(p.getId(), 0L), null))
                .collect(Collectors.toList()));
        Map<Long, String> creators = userNames(favorited.stream()
                .map(Playlist::getUserId).collect(Collectors.toList()));
        result.put("favorited", favorited.stream()
                .map(p -> {
                    PlaylistVO vo = toVO(p, counts.getOrDefault(p.getId(), 0L), favoriteTimeByPlaylist.get(p.getId()));
                    vo.setCreatorName(creators.get(p.getUserId()));
                    return vo;
                })
                .collect(Collectors.toList()));
        return result;
    }

    @Override
    public Map<String, Object> publicList(long page, long size, String keyword, Long userId) {
        long cur = Math.max(1, page);
        long pageSize = size <= 0 ? 10 : Math.min(100, size);

        LambdaQueryWrapper<Playlist> qw = new LambdaQueryWrapper<Playlist>()
                .eq(Playlist::getIsPublic, true)
                // 登录用户在广场看不到自己的歌单（自己的在"我的歌单"里）
                .ne(userId != null, Playlist::getUserId, userId);
        if (keyword != null && !keyword.isBlank()) {
            qw.like(Playlist::getName, keyword.trim());
        }
        qw.orderByDesc(Playlist::getCreateTime).orderByDesc(Playlist::getId);

        Page<Playlist> pager = playlistMapper.selectPage(new Page<>(cur, pageSize), qw);
        List<Long> ids = pager.getRecords().stream().map(Playlist::getId).collect(Collectors.toList());
        Map<Long, Long> counts = songCounts(ids);
        Map<Long, String> creators = userNames(pager.getRecords().stream()
                .map(Playlist::getUserId).collect(Collectors.toList()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", pager.getTotal());
        result.put("pages", pager.getPages());
        result.put("page", pager.getCurrent());
        result.put("size", pager.getSize());
        result.put("records", pager.getRecords().stream()
                .map(p -> {
                    PlaylistVO vo = toVO(p, counts.getOrDefault(p.getId(), 0L), null);
                    vo.setCreatorName(creators.get(p.getUserId()));
                    return vo;
                })
                .collect(Collectors.toList()));
        return result;
    }

    // ---------- 收藏 ----------

    @Override
    public Map<String, Object> favorite(Long id, Long userId) {
        Playlist p = requirePlaylist(id);
        // 自己的歌单不用收藏，直接在我的歌单里管理
        if (userId.equals(p.getUserId())) {
            throw new BizException(ErrorCode.NO_PERMISSION, "不能收藏自己的歌单");
        }
        // 仅公开歌单可被收藏
        if (!Boolean.TRUE.equals(p.getIsPublic())) {
            throw new BizException(ErrorCode.NO_PERMISSION);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("playlistId", id);
        // 唯一键 (user_id, playlist_id) 幂等
        Long exists = favoriteMapper.selectCount(new LambdaQueryWrapper<UserFavoritePlaylist>()
                .eq(UserFavoritePlaylist::getUserId, userId)
                .eq(UserFavoritePlaylist::getPlaylistId, id));
        if (exists != null && exists > 0) {
            result.put("duplicated", true);
            return result;
        }
        UserFavoritePlaylist ufp = new UserFavoritePlaylist();
        ufp.setUserId(userId);
        ufp.setPlaylistId(id);
        try {
            favoriteMapper.insert(ufp);
        } catch (DuplicateKeyException e) {
            log.info("playlist {} already favored by {}, idempotent ok", id, userId);
            result.put("duplicated", true);
        }
        return result;
    }

    @Override
    public void unfavorite(Long id, Long userId) {
        favoriteMapper.delete(new LambdaQueryWrapper<UserFavoritePlaylist>()
                .eq(UserFavoritePlaylist::getUserId, userId)
                .eq(UserFavoritePlaylist::getPlaylistId, id));
    }

    // ---------- 公共工具 ----------

    @Override
    public Map<Long, Long> songCounts(List<Long> playlistIds) {
        if (playlistIds == null || playlistIds.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<PlaylistSong> qw = new QueryWrapper<>();
        qw.select("playlist_id", "COUNT(*) AS cnt")
                .in("playlist_id", playlistIds)
                .groupBy("playlist_id");
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : playlistSongMapper.selectMaps(qw)) {
            Object pid = row.get("playlist_id");
            Object cnt = row.get("cnt");
            if (pid instanceof Number && cnt instanceof Number) {
                counts.put(((Number) pid).longValue(), ((Number) cnt).longValue());
            }
        }
        return counts;
    }

    private PlaylistVO toVO(Playlist p, Long songCount, LocalDateTime favoriteTime) {
        PlaylistVO vo = new PlaylistVO();
        vo.setId(p.getId());
        vo.setUserId(p.getUserId());
        vo.setName(p.getName());
        vo.setDescription(p.getDescription());
        vo.setCoverUrl(p.getCoverUrl());
        vo.setIsPublic(p.getIsPublic());
        vo.setCreateTime(p.getCreateTime());
        vo.setSongCount(songCount == null ? 0L : songCount);
        vo.setFavoriteTime(favoriteTime);
        return vo;
    }

    /** 批量取用户昵称（歌单作者展示）。 */
    private Map<Long, String> userNames(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> names = new HashMap<>();
        for (Map<String, Object> row : userMapper.selectNamesByIds(ids)) {
            if (row.get("id") instanceof Number && row.get("name") != null) {
                names.put(((Number) row.get("id")).longValue(), String.valueOf(row.get("name")));
            }
        }
        return names;
    }

    private boolean isFavored(Long userId, Long playlistId) {
        Long c = favoriteMapper.selectCount(new LambdaQueryWrapper<UserFavoritePlaylist>()
                .eq(UserFavoritePlaylist::getUserId, userId)
                .eq(UserFavoritePlaylist::getPlaylistId, playlistId));
        return c != null && c > 0;
    }

    private Playlist requirePlaylist(Long id) {
        Playlist p = playlistMapper.selectById(id);
        if (p == null) {
            throw new BizException(PLAYLIST_NOT_FOUND, "歌单不存在");
        }
        return p;
    }

    private Playlist requireOwnedPlaylist(Long id, Long userId) {
        Playlist p = requirePlaylist(id);
        if (!userId.equals(p.getUserId())) {
            throw new BizException(ErrorCode.NO_PERMISSION);
        }
        return p;
    }
}
