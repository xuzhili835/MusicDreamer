package com.musicdreamer.music.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 管理列表行（我的上传 / 管理端全量）：联 user 带歌手名，避免前端"未知歌手"。 */
@Data
public class SongManageVO {

    private Long id;

    private String name;

    private String singerNickname;

    private Long singerId;

    private String album;

    private String style;

    private String language;

    private Integer duration;

    private Integer status;

    private String sourceUrl;

    private String rejectReason;

    private String coverUrl;

    private String fileUrl;

    private String lyricUrl;

    /** 在线词库记录 id（LRCLIB，复盘时间戳用；本地来源为空）。 */
    private Long lyricSourceId;

    /** 在线词库原始记录链接（https://lrclib.net/api/get/{id}）。 */
    private String lyricSourceUrl;

    private String fileFormat;

    private Long playCount;

    private Integer collectCount;

    private LocalDateTime createTime;
}
