import { BASE_URL } from '../config'
import { getToken, clearAuth } from './auth'

/**
 * 统一请求封装(后端 Mess 包装:code=0 成功)。
 * - 自动携带 Bearer token;
 * - 2006 登录态失效(过期/改密吊销/禁用)→ 清本地登录态并回登录页;
 * - 其余业务错误 toast message;网络层失败给排查提示(后端没起/不在同一 WiFi)。
 */
export function request({ url, method = 'GET', data = {}, silent = false }) {
  return new Promise((resolve, reject) => {
    const token = getToken()
    uni.request({
      url: BASE_URL + url,
      method,
      data,
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: 'Bearer ' + token } : {}),
      },
      success: (res) => {
        const body = res.data || {}
        if (body.code === 0) {
          return resolve(body.data)
        }
        if (body.code === 2006) {
          clearAuth()
          if (!silent) {
            uni.showToast({ title: body.message || '登录已失效', icon: 'none' })
          }
          setTimeout(() => uni.reLaunch({ url: '/pages/login/login' }), 600)
          return reject(body)
        }
        if (!silent) {
          uni.showToast({ title: body.message || '请求失败', icon: 'none' })
        }
        reject(body)
      },
      fail: (err) => {
        if (!silent) {
          uni.showToast({ title: '网络异常:确认后端已启动且手机与电脑同一WiFi', icon: 'none' })
        }
        reject(err)
      },
    })
  })
}
