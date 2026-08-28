package com.musicdreamer.like.client;

import com.musicdreamer.common.api.Mess;
import com.musicdreamer.like.dto.CollectCountDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 跨服务音乐客户端：调用 Mod_music（musicService8002）。
 * batch 用于收藏列表拼装歌曲详情；collect-count 维护 song.collect_count 冗余计数。
 */
@FeignClient(name = "musicService8002")
public interface SongClient {

    /** 批量查歌曲详情（含 name/singer/coverUrl/duration/style 等）。 */
    @PostMapping("/api/v1/song/internal/batch")
    Mess findByIds(@RequestBody List<Long> ids);

    /** 更新歌曲收藏计数冗余字段，body {songId, delta}。 */
    @PostMapping("/api/v1/song/internal/collect-count")
    Mess updateCollectCount(@RequestBody CollectCountDTO body);
}
