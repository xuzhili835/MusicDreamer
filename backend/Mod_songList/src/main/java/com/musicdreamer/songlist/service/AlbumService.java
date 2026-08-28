package com.musicdreamer.songlist.service;

import com.musicdreamer.songlist.dto.AlbumCreateDTO;
import com.musicdreamer.songlist.vo.AlbumDetailVO;
import com.musicdreamer.songlist.vo.AlbumVO;

import java.util.List;
import java.util.Map;

/** 专辑服务：镜像歌单语义——公开匿名可看、收藏=引用（随源变动）、主人管理。 */
public interface AlbumService {

    Map<String, Object> create(AlbumCreateDTO dto, Long userId);

    void delete(Long id, Long userId);

    Map<String, Object> addSong(Long id, Long songId, Long userId);

    void removeSong(Long id, Long songId, Long userId);

    /** 详情：公开匿名可看，未发布仅主人；登录者附收藏状态。 */
    AlbumDetailVO detail(Long id, Long userId);

    /** 我发布的专辑（歌手/管理员）。 */
    List<AlbumVO> my(Long userId);

    /** 我收藏的专辑（仍公开且非本人发布的，引用语义）。 */
    List<AlbumVO> favorites(Long userId);

    /** 公开专辑分页（广场，登录时排除本人发布的）。 */
    Map<String, Object> publicList(long page, long size, String keyword, Long userId);

    Map<String, Object> favorite(Long id, Long userId);

    void unfavorite(Long id, Long userId);
}
