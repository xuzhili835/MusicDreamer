package com.musicdreamer.login.service;

import com.musicdreamer.login.vo.LoginVO;

import javax.servlet.http.HttpServletRequest;

/**
 * 微信小程序登录（uniapp 端）。
 * 链路：前端 wx.login 取 code → 后端 jscode2session 换 openid →
 * 已绑定直接登录；未绑定自动建号（占位邮箱 wx_{openid}@wx.placeholder，随机密码不可口令登录）。
 */
public interface WxAuthService {

    /** 微信一键登录（匿名，网关白名单）：返回与账号密码登录同构的 LoginVO。 */
    LoginVO wxLogin(String code, HttpServletRequest request);

    /** 当前登录用户绑定微信（登录态）：openid 已被他人绑定则报 2009。 */
    void bindWx(Long userId, String code);

    /** 当前登录用户解绑微信（登录态）。 */
    void unbindWx(Long userId);
}
