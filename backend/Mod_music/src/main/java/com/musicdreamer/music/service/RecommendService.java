package com.musicdreamer.music.service;

import com.musicdreamer.common.auth.AuthContext;
import com.musicdreamer.music.entity.SongSimilarity;
import com.musicdreamer.music.mapper.PlayHistoryMapper;
import com.musicdreamer.music.mapper.SongMapper;
import com.musicdreamer.music.mapper.SongSimilarityMapper;
import com.musicdreamer.music.vo.SimItemVO;
import com.musicdreamer.music.vo.SongCardVO;
import com.musicdreamer.music.vo.UserSongCountVO;
import com.musicdreamer.music.vo.UserSongStatVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 个性化推荐（基于物品的协同过滤 ItemCF）：
 * 离线：播放历史聚合 (user,song,cnt) → 歌曲共现对 → 余弦相似度 → song_similarity Top-N
 * 在线：用户近期听过的歌 → 查相似表加权聚合 → 排除已听 → 不足热榜补齐
 * 数据规模为课设级，重算为同步实现（万级播放记录内毫秒~秒级）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendService {

    /** 每首歌保留的相似歌曲数。 */
    private static final int TOP_N = 20;
    /** 在线推荐时每首种子歌取的相似数。 */
    private static final int SEED_TOP = 5;

    private final PlayHistoryMapper playHistoryMapper;
    private final SongSimilarityMapper similarityMapper;
    private final SongMapper songMapper;
    private final SongService songService;

    /** 重算相似度矩阵（管理员触发 / 表为空时自动兜底）。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> recompute() {
        List<UserSongCountVO> agg = playHistoryMapper.selectPlayAggregates();

        // user → (song → cnt)
        Map<Long, Map<Long, Long>> userSongs = new HashMap<>();
        // song → 总播放用户数（用于归一化分母）
        Map<Long, Long> songUsers = new HashMap<>();
        for (UserSongCountVO v : agg) {
            userSongs.computeIfAbsent(v.getUserId(), k -> new HashMap<>())
                    .put(v.getSongId(), v.getCnt());
            songUsers.merge(v.getSongId(), 1L, Long::sum);
        }

        // 歌曲共现计数：同一用户听过的两两歌曲各贡献一次
        Map<String, Long> cooccurrence = new HashMap<>();
        for (Map<Long, Long> songs : userSongs.values()) {
            List<Long> ids = new ArrayList<>(songs.keySet());
            for (int i = 0; i < ids.size(); i++) {
                for (int j = i + 1; j < ids.size(); j++) {
                    Long a = Math.min(ids.get(i), ids.get(j));
                    Long b = Math.max(ids.get(i), ids.get(j));
                    cooccurrence.merge(a + ":" + b, 1L, Long::sum);
                }
            }
        }

        // 相似度 = 共现 / sqrt(用户数a × 用户数b)（余弦），每首歌取 Top-N
        Map<Long, List<SimItemVO>> topBySong = new HashMap<>();
        for (Map.Entry<String, Long> e : cooccurrence.entrySet()) {
            String[] parts = e.getKey().split(":");
            Long a = Long.parseLong(parts[0]);
            Long b = Long.parseLong(parts[1]);
            double score = e.getValue() / Math.sqrt((double) songUsers.get(a) * songUsers.get(b));
            topBySong.computeIfAbsent(a, k -> new ArrayList<>()).add(sim(b, score));
            topBySong.computeIfAbsent(b, k -> new ArrayList<>()).add(sim(a, score));
        }

        List<SongSimilarity> rows = new ArrayList<>();
        for (Map.Entry<Long, List<SimItemVO>> e : topBySong.entrySet()) {
            List<SimItemVO> list = e.getValue();
            list.sort(Comparator.comparingDouble(SimItemVO::getScore).reversed());
            for (int i = 0; i < Math.min(TOP_N, list.size()); i++) {
                SongSimilarity row = new SongSimilarity();
                row.setSongId(e.getKey());
                row.setSimSongId(list.get(i).getSimSongId());
                row.setScore(list.get(i).getScore());
                rows.add(row);
            }
        }

        similarityMapper.deleteAll();
        if (!rows.isEmpty()) {
            // 分批插入，避免单条 SQL 过大
            for (int i = 0; i < rows.size(); i += 500) {
                similarityMapper.batchInsert(rows.subList(i, Math.min(i + 500, rows.size())));
            }
        }
        log.info("similarity recomputed: users={}, songs={}, pairs={}, rows={}",
                userSongs.size(), songUsers.size(), cooccurrence.size(), rows.size());

        Map<String, Object> stat = new LinkedHashMap<>();
        stat.put("users", userSongs.size());
        stat.put("songs", songUsers.size());
        stat.put("pairs", cooccurrence.size());
        stat.put("rows", rows.size());
        return stat;
    }

    /**
     * 为当前用户推荐（匿名或无历史 → 热榜兜底，首页匿名可看）。
     */
    public List<SongCardVO> listFor(int limit) {
        if (limit <= 0) limit = 10;
        Long userId = AuthContext.getUserId();

        if (userId != null) {
            List<UserSongStatVO> seeds = playHistoryMapper.selectUserPlays90(userId);
            if (!seeds.isEmpty()) {
                Set<Long> listened = new HashSet<>();
                for (UserSongStatVO s : seeds) {
                    listened.add(s.getSongId());
                }
                // 种子歌的相似歌加权聚合：种子越新权重越高（位置衰减）
                Map<Long, Double> score = new HashMap<>();
                for (int i = 0; i < seeds.size(); i++) {
                    double weight = 1.0 / (1 + i);
                    for (SimItemVO sim : similarityMapper.selectTopBySong(seeds.get(i).getSongId(), SEED_TOP)) {
                        if (!listened.contains(sim.getSimSongId())) {
                            score.merge(sim.getSimSongId(), sim.getScore() * weight, Double::sum);
                        }
                    }
                }
                List<Long> ranked = new ArrayList<>(score.keySet());
                ranked.sort((x, y) -> Double.compare(score.get(y), score.get(x)));
                if (!ranked.isEmpty()) {
                    List<Long> ids = ranked.subList(0, Math.min(limit, ranked.size()));
                    List<SongCardVO> cards = songMapper.selectCardsByIds(ids);
                    // 保持得分排序
                    Map<Long, SongCardVO> byId = new HashMap<>();
                    for (SongCardVO c : cards) {
                        byId.put(c.getId(), c);
                    }
                    List<SongCardVO> result = new ArrayList<>();
                    for (Long id : ids) {
                        SongCardVO c = byId.get(id);
                        if (c != null) {
                            result.add(c);
                        }
                    }
                    if (!result.isEmpty()) {
                        return result;
                    }
                }
            }
        }

        // 兜底：热榜（也覆盖匿名用户）
        Object hot = songService.chart("hot", limit);
        if (hot instanceof List) {
            @SuppressWarnings("unchecked")
            List<SongCardVO> list = (List<SongCardVO>) hot;
            return list;
        }
        return new ArrayList<>();
    }

    private static SimItemVO sim(Long songId, double score) {
        SimItemVO v = new SimItemVO();
        v.setSimSongId(songId);
        v.setScore(score);
        return v;
    }
}
