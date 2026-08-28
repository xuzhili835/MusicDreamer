package com.musicdreamer.music.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/** 管理员处理举报：pass=true 核实（status=2），false 驳回举报（status=3）。 */
@Data
public class ReportHandleDTO {

    @NotNull(message = "id 不能为空")
    private Long id;

    @NotNull(message = "pass 不能为空")
    private Boolean pass;

    /** 处置动作，如 takedown（核实下架歌曲）。 */
    private String action;

    private String handleResult;
}
