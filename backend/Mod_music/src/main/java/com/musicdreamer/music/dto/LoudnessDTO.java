package com.musicdreamer.music.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/** Mod_media 响度分析结果回写。 */
@Data
public class LoudnessDTO {

    @NotNull(message = "songId 不能为空")
    private Long songId;

    /** 补偿增益 dB。 */
    private Double gain;

    /** 原始综合响度 LUFS。 */
    private Double integrated;
}
