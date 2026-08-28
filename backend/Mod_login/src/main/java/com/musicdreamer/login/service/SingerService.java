package com.musicdreamer.login.service;

import com.musicdreamer.login.dto.SingerApplyDTO;
import com.musicdreamer.login.dto.SingerAuditDTO;
import com.musicdreamer.login.vo.SingerApplicationVO;

import java.util.List;

/** 歌手认证：申请、审核、申请列表（设计文档 5.3 状态流转）。 */
public interface SingerService {

    /** 提交认证申请，返回申请 ID。存在审核中申请则拒绝（2002）。 */
    Long apply(Long userId, SingerApplyDTO dto);

    /** 管理员审核：通过则升级角色并落 singer_profile；驳回则记录原因。 */
    void audit(SingerAuditDTO dto, Long auditorId);

    /** 管理端申请列表（可按状态筛选）。 */
    List<SingerApplicationVO> applications(Integer status);
}
