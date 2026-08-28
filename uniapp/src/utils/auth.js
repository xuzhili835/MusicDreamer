const KEY_TOKEN = 'md_token'
const KEY_USER = 'md_user'

export function getToken() {
  return uni.getStorageSync(KEY_TOKEN) || ''
}

export function setToken(token) {
  uni.setStorageSync(KEY_TOKEN, token)
}

export function getUser() {
  return uni.getStorageSync(KEY_USER) || null
}

export function setUser(user) {
  uni.setStorageSync(KEY_USER, user)
}

export function clearAuth() {
  uni.removeStorageSync(KEY_TOKEN)
  uni.removeStorageSync(KEY_USER)
}
