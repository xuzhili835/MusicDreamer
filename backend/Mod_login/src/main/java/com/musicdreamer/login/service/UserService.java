package com.musicdreamer.login.service;

import com.musicdreamer.login.dto.AdminCreateUserDTO;
import com.musicdreamer.login.dto.UpdateUserInfoDTO;
import com.musicdreamer.login.dto.UserStatusDTO;
import com.musicdreamer.login.vo.UserBriefVO;
import com.musicdreamer.login.vo.UserVO;

import java.util.Map;

/** 用户信息维护与管理（F014-F016、管理端）。 */
public interface UserService {

    /** 查询当前用户信息。 */
    UserVO info(Long userId);

    /** 修改昵称/头像等基础字段。 */
    void updateInfo(Long userId, UpdateUserInfoDTO dto);

    /** 管理端用户分页列表（模糊匹配用户名/昵称，不返回密码）。 */
    Map<String, Object> pageUsers(long page, long size, String keyword);

    /** 管理端禁用/启用用户。 */
    void updateStatus(UserStatusDTO dto);

    /** 管理端直接创建账号（跳过邮件激活；歌手角色直接认证通过并补 singer_profile）。返回新用户 ID。 */
    Long adminCreate(AdminCreateUserDTO dto);

    /** bug75：确保同名歌手账号存在（无则拼音用户名 + 默认密码 admin123 创建），幂等。 */
    Map<String, Object> ensureSinger(String nickname);

    /** bug81：删除用户（软删除 status=2，保留歌曲/评论等历史引用；吊销存量 token）。 */
    void deleteUser(Long userId, Long operatorId);

    /** 内部调用：用户简要信息。 */
    UserBriefVO brief(Long userId);
}
