package com.musicdreamer.music.vo;

import lombok.Data;

/** 歌曲卡片：搜索/榜单/推荐统一返回。 */
@Data
public class SongCardVO {

    private Long id;

    private String name;

    private String singer;

    private Long singerId;

    private String album;

    private String style;

    private Integer duration;

    private String coverUrl;

    private Long playCount;

    /** 周榜专用：近 7 天播放次数。 */
    private Long weekPlayCount;

    /** 推荐专用：候选得分（相似度加权和）。 */
    private Double score;
}
