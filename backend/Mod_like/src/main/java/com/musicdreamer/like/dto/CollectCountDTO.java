package com.musicdreamer.like.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 音乐服务冗余计数请求体：{songId, delta}。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CollectCountDTO {

    private Long songId;

    /** 增量：收藏 +1 / 取消收藏 -1。 */
    private Integer delta;
}
