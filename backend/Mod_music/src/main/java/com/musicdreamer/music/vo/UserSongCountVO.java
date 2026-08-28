package com.musicdreamer.music.vo;

import lombok.Data;

/** （用户,歌曲）播放次数聚合（离线评分矩阵构建用）。 */
@Data
public class UserSongCountVO {

    private Long userId;

    private Long songId;

    private Long cnt;
}
