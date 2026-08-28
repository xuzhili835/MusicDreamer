package com.musicdreamer.like.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 收藏列表项：collect 记录 + Feign 拼装的歌曲详情。 */
@Data
public class CollectItemVO {

    private Long songId;

    /** 收藏时间。 */
    private LocalDateTime collectTime;

    private String name;

    private Long singerId;

    /** 歌手名（音乐服务返回则带出）。 */
    private String singerName;

    private String coverUrl;

    /** 时长（秒）。 */
    private Integer duration;

    private String style;

    private String album;

    private Integer status;
}
