package com.musicdreamer.music.vo;

import lombok.Data;

/** 单条相似歌曲记录。 */
@Data
public class SimItemVO {

    private Long simSongId;

    private Double score;
}
