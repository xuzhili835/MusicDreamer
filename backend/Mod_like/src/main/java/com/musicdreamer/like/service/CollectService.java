package com.musicdreamer.like.service;

import com.musicdreamer.like.vo.CollectItemVO;

import java.util.List;
import java.util.Map;

/** 歌曲收藏服务。 */
public interface CollectService {

    /** 添加收藏（唯一键防重，幂等）；冗余计数 +1（Feign 失败仅日志）。 */
    Map<String, Object> add(Long userId, Long songId);

    /** 取消收藏（幂等）；实际删除时冗余计数 -1。 */
    void remove(Long userId, Long songId);

    /**
     * 当前用户收藏列表（收藏时间倒序分页），Feign batch 拼装歌曲详情；
     * singer/style 筛选在拼装后过滤（课设数据量小，可接受）。
     */
    Map<String, Object> list(Long userId, long page, long size, String singer, String style);

    /** 当前用户收藏的 songId 数组（倒序，前端标记收藏态用）。 */
    List<Long> ids(Long userId);
}
