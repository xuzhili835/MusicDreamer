import { request } from '../utils/request'

/** 账号密码登录(LoginDTO:username/password/remember)。 */
export function loginByUsername(username, password, remember = false) {
  return request({ url: '/api/v1/user/login', method: 'POST', data: { username, password, remember } })
}

/** 微信一键登录:wx.login 的 code 换 JWT(后端自动建号/绑定识别)。 */
export function loginByWxCode(code) {
  return request({ url: '/api/v1/user/wx/login', method: 'POST', data: { code } })
}

/** 当前用户信息(登录态探活)。 */
export function getUserInfo() {
  return request({ url: '/api/v1/user/info' })
}
