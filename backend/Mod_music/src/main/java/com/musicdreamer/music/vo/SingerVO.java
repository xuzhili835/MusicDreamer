package com.musicdreamer.music.vo;

import lombok.Data;

/** 歌手搜索结果项。 */
@Data
public class SingerVO {

    private Long id;

    private String nickname;

    private String avatar;

    /** 来自 singer_profile，普通用户无则为空。 */
    private Integer fansCount;
}
