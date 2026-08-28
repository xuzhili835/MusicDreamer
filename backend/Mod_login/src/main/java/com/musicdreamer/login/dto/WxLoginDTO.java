package com.musicdreamer.login.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 微信小程序登录/绑定入参：wx.login 临时凭证 code（一次性，5 分钟有效）。 */
@Data
public class WxLoginDTO {

    @NotBlank(message = "code不能为空")
    private String code;
}
