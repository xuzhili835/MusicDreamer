package com.musicdreamer.login.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 歌手认证申请（F048）。证件照字段课设简化，可空串。 */
@Data
public class SingerApplyDTO {

    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    @NotBlank(message = "身份证号不能为空")
    private String idCard;

    private String artistStatement;
}
