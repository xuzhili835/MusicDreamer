package com.musicdreamer.like.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.musicdreamer.like.entity.Collect;
import com.musicdreamer.like.mapper.CollectMapper;
import com.musicdreamer.like.service.CollectService;
import com.musicdreamer.like.service.SongRemoteService;
import com.musicdreamer.like.vo.CollectItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CollectServiceImpl implements CollectService {

    /** 收藏 id 集缓存 key 前缀（前端收藏态标记用）。 */
    private static final String IDS_CACHE_KEY = "collect:ids:";
    private static final Duration IDS_CACHE_TTL = Duration.ofMinutes(10);

    private final CollectMapper collectMapper;
    private final SongRemoteService songRemoteService;
    private final StringRedisTemplate redisTemplate;

    public CollectServiceImpl(CollectMapper collectMapper,
                              SongRemoteService songRemoteService,
                              StringRedisTemplate redisTemplate) {
        this.collectMapper = collectMapper;
        this.songRemoteService = songRemoteService;
        this.redisTemplate = redisTemplate;
    }

    // ---------- 写操作 ----------

    @Override
    public Map<String, Object> add(Long userId, Long songId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("songId", songId);

        // 唯一键 (user_id, song_id) 防重：已收藏则幂等成功
        Long exists = collectMapper.selectCount(new LambdaQueryWrapper<Collect>()
                .eq(Collect::getUserId, userId)
                .eq(Collect::getSongId, songId));
        if (exists != null && exists > 0) {
            result.put("duplicated", true);
            return result;
        }

        Collect c = new Collect();
        c.setUserId(userId);
        c.setSongId(songId);
        try {
            collectMapper.insert(c);
        } catch (DuplicateKeyException e) {
            // 并发重复收藏：唯一键兜底，幂等成功，不重复加计数
            log.info("song {} already collected by {}, idempotent ok", songId, userId);
            result.put("duplicated", true);
            return result;
        }

        // 冗余计数 +1：Feign 失败仅日志，不阻塞收藏主流程
        songRemoteService.notifyCollectCount(songId, 1);
        evictIdsCache(userId);
        return result;
    }

    @Override
    public void remove(Long userId, Long songId) {
        int removed = collectMapper.delete(new LambdaQueryWrapper<Collect>()
                .eq(Collect::getUserId, userId)
                .eq(Collect::getSongId, songId));
        if (removed > 0) {
            // 实际删除才回减冗余计数，避免重复取消导致计数漂移
            songRemoteService.notifyCollectCount(songId, -1);
            evictIdsCache(userId);
        }
    }

    // ---------- 读操作 ----------

    @Override
    public Map<String, Object> list(Long userId, long page, long size, String singer, String style) {
        long cur = Math.max(1, page);
        long pageSize = size <= 0 ? 10 : Math.min(100, size);
        String singerCond = singer == null ? "" : singer.trim();
        String styleCond = style == null ? "" : style.trim();
        boolean hasFilter = !singerCond.isEmpty() || !styleCond.isEmpty();

        LambdaQueryWrapper<Collect> query = new LambdaQueryWrapper<Collect>()
                        .eq(Collect::getUserId, userId)
                        .orderByDesc(Collect::getCreateTime)
                        .orderByDesc(Collect::getId);
        // 筛选条件依赖音乐服务拼装字段，须在过滤后再分页，保证 total 与 records 同口径。
        List<Collect> records;
        long total;
        if (hasFilter) {
            records = collectMapper.selectList(query);
            total = records.size();
        } else {
            Page<Collect> pager = collectMapper.selectPage(new Page<>(cur, pageSize), query);
            records = pager.getRecords();
            total = pager.getTotal();
        }

        List<Long> songIds = records.stream()
                .map(Collect::getSongId).collect(Collectors.toList());
        Map<Long, Map<String, Object>> songs = songRemoteService.batchSongs(songIds);

        List<CollectItemVO> items = new ArrayList<>(records.size());
        for (Collect c : records) {
            CollectItemVO vo = new CollectItemVO();
            vo.setSongId(c.getSongId());
            vo.setCollectTime(c.getCreateTime());
            Map<String, Object> song = songs.get(c.getSongId());
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
            items.add(vo);
        }

        if (hasFilter) {
            items = items.stream()
                    .filter(vo -> matchSinger(vo, singerCond) && matchStyle(vo, styleCond))
                    .collect(Collectors.toList());
            total = items.size();
            int from = (int) Math.min((cur - 1) * pageSize, total);
            int to = (int) Math.min(from + pageSize, total);
            items = new ArrayList<>(items.subList(from, to));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", cur);
        result.put("size", pageSize);
        result.put("records", items);
        return result;
    }

    @Override
    public List<Long> ids(Long userId) {
        List<Long> cached = readIdsCache(userId);
        if (cached != null) {
            return cached;
        }
        List<Long> ids = collectMapper.selectList(new LambdaQueryWrapper<Collect>()
                        .eq(Collect::getUserId, userId)
                        .orderByDesc(Collect::getCreateTime)
                        .orderByDesc(Collect::getId))
                .stream().map(Collect::getSongId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        writeIdsCache(userId, ids);
        return ids;
    }

    // ---------- 筛选 ----------

    /** 歌手筛选：按歌手名包含匹配；入参为纯数字时兼容按 singerId 精确匹配。 */
    private boolean matchSinger(CollectItemVO vo, String cond) {
        if (cond == null || cond.isEmpty()) {
            return true;
        }
        String name = vo.getSingerName();
        if (name != null && name.contains(cond)) {
            return true;
        }
        try {
            return vo.getSingerId() != null && vo.getSingerId() == Long.parseLong(cond);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean matchStyle(CollectItemVO vo, String cond) {
        if (cond == null || cond.isEmpty()) {
            return true;
        }
        return cond.equalsIgnoreCase(vo.getStyle() == null ? "" : vo.getStyle().trim());
    }

    // ---------- Redis 缓存（全部降级：Redis 异常不影响主流程） ----------

    private String cacheKey(Long userId) {
        return IDS_CACHE_KEY + userId;
    }

    private List<Long> readIdsCache(Long userId) {
        try {
            String val = redisTemplate.opsForValue().get(cacheKey(userId));
            if (val == null || val.isEmpty()) {
                return null;
            }
            List<Long> ids = new ArrayList<>();
            for (String s : val.split(",")) {
                if (!s.isBlank()) {
                    ids.add(Long.valueOf(s.trim()));
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("读取收藏 id 缓存失败(降级查库): {}", e.getMessage());
            return null;
        }
    }

    private void writeIdsCache(Long userId, List<Long> ids) {
        try {
            String val = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
            redisTemplate.opsForValue().set(cacheKey(userId), val, IDS_CACHE_TTL);
        } catch (Exception e) {
            log.warn("写入收藏 id 缓存失败(忽略): {}", e.getMessage());
        }
    }

    private void evictIdsCache(Long userId) {
        try {
            redisTemplate.delete(cacheKey(userId));
        } catch (Exception e) {
            log.warn("清空收藏 id 缓存失败(忽略): {}", e.getMessage());
        }
    }
}
