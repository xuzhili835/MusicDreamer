package com.musicdreamer.music.service;

import com.musicdreamer.common.api.ErrorCode;
import com.musicdreamer.common.auth.AuthContext;
import com.musicdreamer.common.exception.BizException;
import com.musicdreamer.music.dto.PlayReportDTO;
import com.musicdreamer.music.entity.PlayHistory;
import com.musicdreamer.music.entity.Song;
import com.musicdreamer.music.mapper.PlayHistoryMapper;
import com.musicdreamer.music.mapper.SongMapper;
import com.musicdreamer.music.vo.PlayRecordVO;
import com.musicdreamer.music.vo.RecentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 播放统计（设计 6.3 节）：口径为播完才计。
 * playReport 同一事务完成明细写入 + play_count 热计数更新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayService {

    private final SongMapper songMapper;
    private final PlayHistoryMapper playHistoryMapper;

    /** 播完上报：INSERT play_history + UPDATE song.play_count（同一事务）。 */
    @Transactional(rollbackFor = Exception.class)
    public void playReport(PlayReportDTO dto, String ip) {
        Long userId = AuthContext.requireLogin();
        Song song = songMapper.selectById(dto.getSongId());
        if (song == null) {
            throw new BizException(ErrorCode.SONG_NOT_FOUND);
        }

        PlayHistory history = new PlayHistory();
        history.setUserId(userId);
        history.setSongId(dto.getSongId());
        history.setPlayDuration(dto.getPlayDuration() == null ? 0 : dto.getPlayDuration());
        history.setPlayComplete(1);
        history.setDeviceType("web");
        history.setIpAddress(ip);
        history.setPlayedAt(LocalDateTime.now());
        playHistoryMapper.insert(history);

        songMapper.incrementPlayCount(dto.getSongId());
        log.debug("play reported: user={}, song={}", userId, dto.getSongId());
    }

    /** 最近播放（按歌曲去重取最新）。 */
    public List<RecentVO> recent(int limit) {
        Long userId = AuthContext.requireLogin();
        if (limit <= 0) limit = 50;
        return playHistoryMapper.selectRecent(userId, Math.min(limit, 200));
    }

    /** 播放明细分页。 */
    public Map<String, Object> history(int page, int size) {
        Long userId = AuthContext.requireLogin();
        if (page <= 0) page = 1;
        if (size <= 0) size = 20;
        long offset = (long) (page - 1) * size;
        List<PlayRecordVO> records = playHistoryMapper.selectHistory(userId, offset, size);
        long total = playHistoryMapper.countHistory(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("records", records);
        return result;
    }
}
