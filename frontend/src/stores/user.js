// 用户 store：token + 基础信息（localStorage 持久化）+ 歌曲收藏 id 集合
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import api, { TOKEN_KEY, USER_KEY } from '../api'
import { listOf } from '../utils'

function readStoredInfo() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || '{}') || {}
  } catch (e) {
    return {}
  }
}

export const useUserStore = defineStore('user', {
  state: () => {
    const info = readStoredInfo()
    return {
      token: localStorage.getItem(TOKEN_KEY) || '',
      userId: info.userId != null ? info.userId : null,
      nickname: info.nickname || '',
      avatar: info.avatar || '',
      role: Number(info.role || 0),
      singerStatus: Number(info.singerStatus || 0), // 0未申请 1审核中 2通过 3驳回
      collectedIds: [],
      collectLoaded: false
    }
  },
  getters: {
    isLogin: (s) => !!s.token,
    isSinger: (s) => s.role >= 1,
    isAdmin: (s) => s.role === 2,
    displayName: (s) => s.nickname || (s.userId != null ? `用户${s.userId}` : '未登录')
  },
  actions: {
    persist() {
      localStorage.setItem(USER_KEY, JSON.stringify({
        userId: this.userId,
        nickname: this.nickname,
        avatar: this.avatar,
        role: this.role,
        singerStatus: this.singerStatus
      }))
    },
    setLogin(data) {
      this.token = data.token
      this.userId = data.userId
      this.nickname = data.nickname || data.username || ''
      this.avatar = data.avatar || ''
      this.role = Number(data.role || 0)
      this.persist()
      localStorage.setItem(TOKEN_KEY, this.token)
      this.loadCollectIds()
    },
    async refreshInfo() {
      if (!this.isLogin) return
      try {
        const info = await api.getUserInfo()
        if (info) {
          if (info.nickname !== undefined && info.nickname !== null) this.nickname = info.nickname
          if (info.avatar !== undefined && info.avatar !== null) this.avatar = info.avatar
          if (info.role !== undefined && info.role !== null) this.role = Number(info.role)
          if (info.singerStatus !== undefined && info.singerStatus !== null) {
            this.singerStatus = Number(info.singerStatus)
          }
          this.persist()
        }
      } catch (e) {
        /* 静默失败，拦截器已提示 */
      }
    },
    async loadCollectIds() {
      if (!this.isLogin) {
        this.collectedIds = []
        this.collectLoaded = false
        return
      }
      try {
        const ids = await api.collectIds()
        this.collectedIds = listOf(ids).map((x) => Number(typeof x === 'object' ? x.songId : x))
        this.collectLoaded = true
      } catch (e) {
        this.collectedIds = []
      }
    },
    isCollected(songId) {
      return this.collectedIds.includes(Number(songId))
    },
    // 返回操作后的收藏状态；未登录返回 null
    async toggleCollect(songId) {
      if (!this.isLogin) {
        ElMessage.warning('请先登录后再收藏')
        return null
      }
      const id = Number(songId)
      if (this.isCollected(id)) {
        await api.collectRemove(id)
        this.collectedIds = this.collectedIds.filter((x) => x !== id)
        ElMessage.success('已取消收藏')
        return false
      }
      await api.collectAdd(id)
      if (!this.collectedIds.includes(id)) this.collectedIds.push(id)
      ElMessage.success('已收藏')
      return true
    },
    clear() {
      this.token = ''
      this.userId = null
      this.nickname = ''
      this.avatar = ''
      this.role = 0
      this.singerStatus = 0
      this.collectedIds = []
      this.collectLoaded = false
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})
