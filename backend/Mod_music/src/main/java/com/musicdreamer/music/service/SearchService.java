package com.musicdreamer.music.service;

import com.musicdreamer.music.mapper.SongMapper;
import com.musicdreamer.music.mapper.UserMapper;
import com.musicdreamer.music.vo.SingerVO;
import com.musicdreamer.music.vo.SongCardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 搜索（匿名）：歌曲/歌手/风格分类（设计 6.2 节，MySQL 索引查询）。 */
@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int MAX_SIZE = 50;

    private final SongMapper songMapper;
    private final UserMapper userMapper;

    /** 搜歌：歌名或歌手名匹配，仅已发布。 */
    public Map<String, Object> searchSongs(String keyword, int page, int size) {
        String kw = normalize(keyword);
        size = clampSize(size);
        long offset = (long) (page - 1) * size;
        List<SongCardVO> songs = songMapper.searchCards(kw, offset, size);
        long total = songMapper.countSearch(kw);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("songs", songs);
        return result;
    }

    /** 搜歌手：role>=1 且用户名/昵称匹配。 */
    public Map<String, Object> searchSingers(String keyword, int page, int size) {
        String kw = normalize(keyword);
        size = clampSize(size);
        long offset = (long) (page - 1) * size;
        List<SingerVO> singers = userMapper.searchSingers(kw, offset, size);
        long total = userMapper.countSingers(kw);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("singers", singers);
        return result;
    }

    /** 去重风格列表（分类浏览入口）。 */
    public List<String> styles() {
        return songMapper.selectDistinctStyles();
    }

    /** 风格/语言组合过滤（仅已发布）。 */
    public Map<String, Object> byStyle(String style, String language, int page, int size) {
        size = clampSize(size);
        long offset = (long) (page - 1) * size;
        List<SongCardVO> songs = songMapper.selectByStyle(style, language, offset, size);
        long total = songMapper.countByStyle(style, language);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("songs", songs);
        return result;
    }

    private String normalize(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : "";
    }

    private int clampSize(int size) {
        if (size <= 0) return 10;
        return Math.min(size, MAX_SIZE);
    }
}
