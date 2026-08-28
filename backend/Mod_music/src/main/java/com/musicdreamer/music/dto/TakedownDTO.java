package com.musicdreamer.music.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 管理员下架歌曲（status=0，原因记入 reject_reason）。 */
@Data
public class TakedownDTO {

    @NotBlank(message = "下架原因不能为空")
    private String reason;
}
