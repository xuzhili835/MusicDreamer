package com.musicdreamer.music.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 最近播放（按歌曲去重取最新一次播放时间）。 */
@Data
public class RecentVO {

    private Long songId;

    private String name;

    private String singer;

    private String album;

    private String coverUrl;

    private Integer duration;

    private LocalDateTime lastPlayedAt;
}
