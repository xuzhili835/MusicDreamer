package com.musicdreamer.songlist.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/** 专辑详情。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AlbumDetailVO extends AlbumVO {

    private LocalDateTime updateTime;

    /** 收藏状态（登录者视角）。 */
    private Boolean favored;

    /** 歌手昵称（取专辑内第一首歌的歌手名）。 */

    private List<PlaylistSongVO> songs;
}
