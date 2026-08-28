package com.musicdreamer.setting.dto;

import lombok.Data;

/** 更新配置请求体：{ "value": "xxx" }。 */
@Data
public class SetSettingDTO {

    private String value;
}
