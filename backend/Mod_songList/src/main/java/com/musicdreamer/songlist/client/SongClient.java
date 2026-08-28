package com.musicdreamer.songlist.client;

import com.musicdreamer.common.api.Mess;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 跨服务歌曲校验客户端：调用 Mod_music（musicService8002）内部接口。
 * data 为歌曲对象（LinkedHashMap，含 id/name/singerId/status/coverUrl/duration/style）。
 */
@FeignClient(name = "musicService8002", path = "/api/v1/song/internal")
public interface SongClient {

    /** 按 id 查歌曲（含未发布，用于状态校验）。 */
    @GetMapping("/{id}")
    Mess findById(@PathVariable("id") Long id);

    /** 批量查歌曲详情，用于歌单详情/列表拼装。 */
    @PostMapping("/batch")
    Mess findByIds(@RequestBody List<Long> ids);
}
