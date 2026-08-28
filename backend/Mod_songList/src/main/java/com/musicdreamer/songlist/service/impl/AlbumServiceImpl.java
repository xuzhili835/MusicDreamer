package com.musicdreamer.songlist.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.musicdreamer.common.api.ErrorCode;
import com.musicdreamer.common.exception.BizException;
import com.musicdreamer.songlist.dto.AlbumCreateDTO;
import com.musicdreamer.songlist.entity.Album;
import com.musicdreamer.songlist.entity.AlbumSong;
import com.musicdreamer.songlist.entity.UserFavoriteAlbum;
import com.musicdreamer.songlist.mapper.AlbumMapper;
import com.musicdreamer.songlist.mapper.AlbumSongMapper;
import com.musicdreamer.songlist.mapper.UserFavoriteAlbumMapper;
import com.musicdreamer.songlist.mapper.UserMapper;
import com.musicdreamer.songlist.service.AlbumService;
import com.musicdreamer.songlist.service.SongRemoteService;
import com.musicdreamer.songlist.vo.AlbumDetailVO;
import com.musicdreamer.songlist.vo.AlbumVO;
import com.musicdreamer.songlist.vo.PlaylistSongVO;
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

/**
 * 专辑实现：完全镜像歌单语义（引用式收藏、公开可见性、级联清理），
 * 差异点：发布者是歌手/管理员（role>=1，控制器校验）；收藏同样禁止自己的作品。
 */
@Slf4j
@Service
public class AlbumServiceImpl implements AlbumService {

    /** 专辑域内部错误码：专辑不存在。 */
    private static final int ALBUM_NOT_FOUND = 6101;

    private final AlbumMapper albumMapper;
    private final AlbumSongMapper albumSongMapper;
    private final UserFavoriteAlbumMapper favoriteMapper;
    private final SongRemoteService songRemoteService;
    private final UserMapper userMapper;

    public AlbumServiceImpl(AlbumMapper albumMapper,
                            AlbumSongMapper albumSongMapper,
                            UserFavoriteAlbumMapper favoriteMapper,
                            SongRemoteService songRemoteService,
                            UserMapper userMapper) {
        this.albumMapper = albumMapper;
        this.albumSongMapper = albumSongMapper;
        this.favoriteMapper = favoriteMapper;
        this.songRemoteService = songRemoteService;
        this.userMapper = userMapper;
    }

    // ---------- 专辑 CRUD ----------

    @Override
    public Map<String, Object> create(AlbumCreateDTO dto, Long userId) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_MISSING, "专辑名称不能为空");
        }
        Album a = new Album();
        a.setUserId(userId);
        a.setName(dto.getName().trim());
        a.setDescription(dto.getDescription());
        a.setIsPublic(dto.getIsPublic() == null ? Boolean.TRUE : dto.getIsPublic());
        albumMapper.insert(a);
        Album saved = albumMapper.selectById(a.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("albumId", a.getId());
        result.put("createTime", saved == null ? LocalDateTime.now() : saved.getCreateTime());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        requireOwnedAlbum(id, userId);
        // 级联清理：专辑歌曲关联 + 用户收藏记录
        albumSongMapper.delete(new LambdaQueryWrapper<AlbumSong>()
                .eq(AlbumSong::getAlbumId, id));
        favoriteMapper.delete(new LambdaQueryWrapper<UserFavoriteAlbum>()
                .eq(UserFavoriteAlbum::getAlbumId, id));
        albumMapper.deleteById(id);
    }

    // ---------- 专辑歌曲管理 ----------

    @Override
    public Map<String, Object> addSong(Long id, Long songId, Long userId) {
        requireOwnedAlbum(id, userId);
        Map<String, Object> song = songRemoteService.requirePublishedSong(songId);
        // bug65：专辑只能收录自己的作品——跨歌手把别人的歌拉进自己专辑属于越权
        Object songOwner = song.get("singerId");
        if (!(songOwner instanceof Number) || userId.longValue() != ((Number) songOwner).longValue()) {
            throw new BizException(ErrorCode.NO_PERMISSION, "专辑只能添加自己上传的歌曲");
        }

        Long exists = albumSongMapper.selectCount(new LambdaQueryWrapper<AlbumSong>()
                .eq(AlbumSong::getAlbumId, id)
                .eq(AlbumSong::getSongId, songId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("songId", songId);
        if (exists != null && exists > 0) {
            result.put("duplicated", true);
            return result;
        }

        AlbumSong last = albumSongMapper.selectOne(new LambdaQueryWrapper<AlbumSong>()
                .eq(AlbumSong::getAlbumId, id)
                .orderByDesc(AlbumSong::getSortOrder)
                .last("LIMIT 1"));
        int nextOrder = last == null || last.getSortOrder() == null ? 0 : last.getSortOrder() + 1;

        AlbumSong link = new AlbumSong();
        link.setAlbumId(id);
        link.setSongId(songId);
        link.setSortOrder(nextOrder);
        try {
            albumSongMapper.insert(link);
        } catch (DuplicateKeyException e) {
            log.info("song {} duplicated in album {}, idempotent ok", songId, id);
            result.put("duplicated", true);
            result.put("sortOrder", nextOrder);
            return result;
        }
        result.put("sortOrder", nextOrder);
        // 专辑封面跟随最新加入的歌曲（bug18）
        refreshCoverFromSong(id, song);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeSong(Long id, Long songId, Long userId) {
        requireOwnedAlbum(id, userId);
        albumSongMapper.delete(new LambdaQueryWrapper<AlbumSong>()
                .eq(AlbumSong::getAlbumId, id)
                .eq(AlbumSong::getSongId, songId));
        compressSortOrder(id);
        // 封面回退：找剩余歌曲里最新一首"带封面"的；一首带封面的都没有才复位无封面（P1-4）
        List<AlbumSong> rest = albumSongMapper.selectList(new LambdaQueryWrapper<AlbumSong>()
                .eq(AlbumSong::getAlbumId, id)
                .orderByDesc(AlbumSong::getSortOrder)
                .orderByDesc(AlbumSong::getId));
        if (rest.isEmpty()) {
            refreshCoverFromSong(id, null);
            return;
        }
        try {
            Map<Long, Map<String, Object>> batch = songRemoteService.batchSongs(
                    rest.stream().map(AlbumSong::getSongId).toList());
            for (AlbumSong row : rest) {
                Map<String, Object> s = batch.get(row.getSongId());
                Object cover = s == null ? null : s.get("coverUrl");
                if (cover != null && !"null".equals(cover) && !String.valueOf(cover).isBlank()) {
                    refreshCoverFromSong(id, s);
                    return;
                }
            }
            refreshCoverFromSong(id, null); // 剩余全无封面
        } catch (Exception e) {
            log.warn("album {} cover refresh after remove failed: {}", id, e.getMessage());
        }
    }

    /** 专辑封面跟随歌曲（bug18，P1-4 收口）：song 带封面则更新；
     *  song 非空但无封面 → 保留现有封面不清空；song 为 null → 复位无封面。
     *  失败只记日志不影响主流程。 */
    private void refreshCoverFromSong(Long albumId, Map<String, Object> song) {
        try {
            String coverUrl = null;
            if (song != null) {
                Object cover = song.get("coverUrl");
                coverUrl = cover == null || "null".equals(cover) ? null : String.valueOf(cover);
                if (coverUrl == null || coverUrl.isBlank()) {
                    return; // 该歌无封面：不动专辑现有封面
                }
            }
            albumMapper.update(null, new LambdaUpdateWrapper<Album>()
                    .eq(Album::getId, albumId)
                    .set(Album::getCoverUrl, coverUrl));
        } catch (Exception e) {
            log.warn("album {} cover refresh failed: {}", albumId, e.getMessage());
        }
    }

    /** 移除歌曲后压缩序号，保证连续。 */
    private void compressSortOrder(Long albumId) {
        List<AlbumSong> rest = albumSongMapper.selectList(new LambdaQueryWrapper<AlbumSong>()
                .eq(AlbumSong::getAlbumId, albumId)
                .orderByAsc(AlbumSong::getSortOrder)
                .orderByAsc(AlbumSong::getId));
        int order = 0;
        for (AlbumSong row : rest) {
            if (row.getSortOrder() == null || row.getSortOrder() != order) {
                row.setSortOrder(order);
                albumSongMapper.updateById(row);
            }
            order++;
        }
    }

    // ---------- 查询 ----------

    @Override
    public AlbumDetailVO detail(Long id, Long userId) {
        Album a = requireAlbum(id);
        boolean owner = userId != null && userId.equals(a.getUserId());
        // 可见性：未发布专辑仅主人可见
        if (!Boolean.TRUE.equals(a.getIsPublic()) && !owner) {
            throw new BizException(ErrorCode.NO_PERMISSION);
        }

        List<AlbumSong> links = albumSongMapper.selectList(new LambdaQueryWrapper<AlbumSong>()
                .eq(AlbumSong::getAlbumId, id)
                .orderByAsc(AlbumSong::getSortOrder)
                .orderByAsc(AlbumSong::getId));

        List<Long> songIds = links.stream().map(AlbumSong::getSongId).collect(Collectors.toList());
        Map<Long, Map<String, Object>> songs = songRemoteService.batchSongs(songIds);

        List<PlaylistSongVO> songVOs = new ArrayList<>(links.size());
        for (AlbumSong link : links) {
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

        AlbumDetailVO vo = new AlbumDetailVO();
        vo.setId(a.getId());
        vo.setUserId(a.getUserId());
        vo.setName(a.getName());
        vo.setDescription(a.getDescription());
        vo.setCoverUrl(a.getCoverUrl());
        vo.setIsPublic(a.getIsPublic());
        vo.setCreateTime(a.getCreateTime());
        vo.setUpdateTime(a.getUpdateTime());
        vo.setSongCount((long) songVOs.size());
        vo.setFavored(userId != null && isFavored(userId, id));
        vo.setSongs(songVOs);
        vo.setSingerName(userNames(List.of(a.getUserId())).get(a.getUserId()));
        return vo;
    }

    @Override
    public List<AlbumVO> my(Long userId) {
        List<Album> mine = albumMapper.selectList(new LambdaQueryWrapper<Album>()
                .eq(Album::getUserId, userId)
                .orderByDesc(Album::getCreateTime)
                .orderByDesc(Album::getId));
        Map<Long, Long> counts = songCounts(mine.stream().map(Album::getId).collect(Collectors.toList()));
        return mine.stream().map(a -> toVO(a, counts.getOrDefault(a.getId(), 0L), null)).collect(Collectors.toList());
    }

    @Override
    public List<AlbumVO> favorites(Long userId) {
        List<UserFavoriteAlbum> favorites = favoriteMapper.selectList(
                new LambdaQueryWrapper<UserFavoriteAlbum>()
                        .eq(UserFavoriteAlbum::getUserId, userId)
                        .orderByDesc(UserFavoriteAlbum::getCreateTime)
                        .orderByDesc(UserFavoriteAlbum::getId));

        Map<Long, LocalDateTime> favoriteTimeByAlbum = favorites.stream()
                .collect(Collectors.toMap(UserFavoriteAlbum::getAlbumId,
                        UserFavoriteAlbum::getCreateTime, (a, b) -> a, LinkedHashMap::new));

        List<Album> favorited = favoriteTimeByAlbum.isEmpty()
                ? Collections.emptyList()
                : albumMapper.selectBatchIds(favoriteTimeByAlbum.keySet())
                .stream()
                // 收藏是引用而非快照：源专辑取消发布后从收藏区消失（删除时收藏记录已级联清理）
                .filter(a -> Boolean.TRUE.equals(a.getIsPublic()) && !userId.equals(a.getUserId()))
                .sorted((a, b) -> {
                    LocalDateTime ta = favoriteTimeByAlbum.get(a.getId());
                    LocalDateTime tb = favoriteTimeByAlbum.get(b.getId());
                    return tb.compareTo(ta);
                })
                .collect(Collectors.toList());

        Map<Long, Long> counts = songCounts(favorited.stream().map(Album::getId).collect(Collectors.toList()));
        Map<Long, String> singers = userNames(favorited.stream().map(Album::getUserId).collect(Collectors.toList()));
        return favorited.stream()
                .map(a -> {
                    AlbumVO vo = toVO(a, counts.getOrDefault(a.getId(), 0L), favoriteTimeByAlbum.get(a.getId()));
                    vo.setSingerName(singers.get(a.getUserId()));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> publicList(long page, long size, String keyword, Long userId) {
        long cur = Math.max(1, page);
        long pageSize = size <= 0 ? 10 : Math.min(100, size);

        LambdaQueryWrapper<Album> qw = new LambdaQueryWrapper<Album>()
                .eq(Album::getIsPublic, true)
                // 登录用户在广场看不到自己发布的专辑（自己的在"音乐库"里）
                .ne(userId != null, Album::getUserId, userId);
        if (keyword != null && !keyword.isBlank()) {
            qw.like(Album::getName, keyword.trim());
        }
        qw.orderByDesc(Album::getCreateTime).orderByDesc(Album::getId);

        Page<Album> pager = albumMapper.selectPage(new Page<>(cur, pageSize), qw);
        List<Long> ids = pager.getRecords().stream().map(Album::getId).collect(Collectors.toList());
        Map<Long, Long> counts = songCounts(ids);
        Map<Long, String> singers = userNames(pager.getRecords().stream()
                .map(Album::getUserId).collect(Collectors.toList()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", pager.getTotal());
        result.put("pages", pager.getPages());
        result.put("page", pager.getCurrent());
        result.put("size", pager.getSize());
        result.put("records", pager.getRecords().stream()
                .map(a -> {
                    AlbumVO vo = toVO(a, counts.getOrDefault(a.getId(), 0L), null);
                    vo.setSingerName(singers.get(a.getUserId()));
                    return vo;
                })
                .collect(Collectors.toList()));
        return result;
    }

    // ---------- 收藏 ----------

    @Override
    public Map<String, Object> favorite(Long id, Long userId) {
        Album a = requireAlbum(id);
        // 自己的专辑不用收藏，直接在音乐库里管理
        if (userId.equals(a.getUserId())) {
            throw new BizException(ErrorCode.NO_PERMISSION, "不能收藏自己的专辑");
        }
        // 仅已发布专辑可被收藏
        if (!Boolean.TRUE.equals(a.getIsPublic())) {
            throw new BizException(ErrorCode.NO_PERMISSION);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("albumId", id);
        Long exists = favoriteMapper.selectCount(new LambdaQueryWrapper<UserFavoriteAlbum>()
                .eq(UserFavoriteAlbum::getUserId, userId)
                .eq(UserFavoriteAlbum::getAlbumId, id));
        if (exists != null && exists > 0) {
            result.put("duplicated", true);
            return result;
        }
        UserFavoriteAlbum ufa = new UserFavoriteAlbum();
        ufa.setUserId(userId);
        ufa.setAlbumId(id);
        try {
            favoriteMapper.insert(ufa);
        } catch (DuplicateKeyException e) {
            log.info("album {} already favored by {}, idempotent ok", id, userId);
            result.put("duplicated", true);
        }
        return result;
    }

    @Override
    public void unfavorite(Long id, Long userId) {
        favoriteMapper.delete(new LambdaQueryWrapper<UserFavoriteAlbum>()
                .eq(UserFavoriteAlbum::getUserId, userId)
                .eq(UserFavoriteAlbum::getAlbumId, id));
    }

    // ---------- 公共工具 ----------

    private Map<Long, Long> songCounts(List<Long> albumIds) {
        if (albumIds == null || albumIds.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<AlbumSong> qw = new QueryWrapper<>();
        qw.select("album_id", "COUNT(*) AS cnt")
                .in("album_id", albumIds)
                .groupBy("album_id");
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : albumSongMapper.selectMaps(qw)) {
            Object aid = row.get("album_id");
            Object cnt = row.get("cnt");
            if (aid instanceof Number && cnt instanceof Number) {
                counts.put(((Number) aid).longValue(), ((Number) cnt).longValue());
            }
        }
        return counts;
    }

    private AlbumVO toVO(Album a, Long songCount, LocalDateTime favoriteTime) {
        AlbumVO vo = new AlbumVO();
        vo.setId(a.getId());
        vo.setUserId(a.getUserId());
        vo.setName(a.getName());
        vo.setDescription(a.getDescription());
        vo.setCoverUrl(a.getCoverUrl());
        vo.setIsPublic(a.getIsPublic());
        vo.setCreateTime(a.getCreateTime());
        vo.setSongCount(songCount == null ? 0L : songCount);
        vo.setFavoriteTime(favoriteTime);
        return vo;
    }

    /** 批量取用户昵称（专辑歌手展示）。 */
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

    private boolean isFavored(Long userId, Long albumId) {
        Long c = favoriteMapper.selectCount(new LambdaQueryWrapper<UserFavoriteAlbum>()
                .eq(UserFavoriteAlbum::getUserId, userId)
                .eq(UserFavoriteAlbum::getAlbumId, albumId));
        return c != null && c > 0;
    }

    private Album requireAlbum(Long id) {
        Album a = albumMapper.selectById(id);
        if (a == null) {
            throw new BizException(ALBUM_NOT_FOUND, "专辑不存在");
        }
        return a;
    }

    private Album requireOwnedAlbum(Long id, Long userId) {
        Album a = requireAlbum(id);
        if (!userId.equals(a.getUserId())) {
            throw new BizException(ErrorCode.NO_PERMISSION);
        }
        return a;
    }
}
