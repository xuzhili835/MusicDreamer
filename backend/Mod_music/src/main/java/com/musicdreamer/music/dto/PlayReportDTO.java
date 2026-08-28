package com.musicdreamer.music.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/** 播完上报：同一事务写 play_history 明细 + song.play_count 热计数。
 *  时长是客户端上报值，服务端做范围收口（P1-6）：负数/超大值直接 400。 */
@Data
public class PlayReportDTO {

    @NotNull(message = "songId 不能为空")
    private Long songId;

    @Min(value = 0, message = "播放时长不能为负")
    @Max(value = 7200, message = "播放时长超出合理范围")
    private Integer playDuration;
}
