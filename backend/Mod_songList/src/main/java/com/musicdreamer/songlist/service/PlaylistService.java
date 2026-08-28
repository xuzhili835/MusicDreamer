package com.musicdreamer.songlist.service;

import com.musicdreamer.songlist.dto.PlaylistCreateDTO;
import com.musicdreamer.songlist.dto.PlaylistUpdateDTO;
import com.musicdreamer.songlist.vo.PlaylistDetailVO;
import com.musicdreamer.songlist.vo.PlaylistVO;

import java.util.List;
import java.util.Map;

/** 歌单服务：歌单 CRUD、歌曲管理、收藏、公开广场。 */
public interface PlaylistService {

    /** 创建歌单，返回 {playlistId, createTime}。 */
    Map<String, Object> create(PlaylistCreateDTO dto, Long userId);

    /** 更新歌单（仅创建者）。 */
    void update(Long id, PlaylistUpdateDTO dto, Long userId);

    /** 删除歌单（仅创建者），级联清理 playlist_song 与 user_favorite_playlist。 */
    void delete(Long id, Long userId);

    /** 添加歌曲（仅创建者）：Feign 校验存在且已发布，sort_order=当前最大+1，重复添加幂等。 */
    Map<String, Object> addSong(Long id, Long songId, Long userId);

    /** 移除歌曲（仅创建者）并压缩序号，幂等。 */
    void removeSong(Long id, Long songId, Long userId);

    /** 歌单详情 + 歌曲列表（sort_order 升序，Feign 拼装歌曲信息）；私有歌单仅创建者可见。 */
    PlaylistDetailVO detail(Long id, Long userId);

    /** 我创建的 + 我收藏的两组歌单（带歌曲数）。 */
    Map<String, Object> my(Long userId);

    /** 收藏公开歌单（唯一键防重，幂等）。 */
    Map<String, Object> favorite(Long id, Long userId);

    /** 取消收藏（幂等）。 */
    void unfavorite(Long id, Long userId);

    /** 公开歌单分页（keyword 匹配名称）。 */
    Map<String, Object> publicList(long page, long size, String keyword, Long userId);

    /** 批量统计歌单歌曲数。 */
    Map<Long, Long> songCounts(List<Long> playlistIds);
}
