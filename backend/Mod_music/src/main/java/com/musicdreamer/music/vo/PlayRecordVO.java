package com.musicdreamer.music.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 播放明细记录（history 分页用）。 */
@Data
public class PlayRecordVO {

    private Long id;

    private Long songId;

    private String songName;

    private String coverUrl;

    private String singer;

    private Integer playDuration;

    private Integer playComplete;

    private String deviceType;

    private LocalDateTime playedAt;
}
