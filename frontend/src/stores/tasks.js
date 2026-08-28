// 媒体任务全局 store：后台下载的跨页轮询 + 完成通知。
// 下载本身在服务端异步执行（media_task 表持久化，服务重启由 SelfHeal 接续），
// 这里只负责：页面切走后继续跟踪进度、刷新页面后从 /media/tasks/active 恢复。
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import api from '../api'
import { isTaskTerminal } from '../utils'

const POLL_MS = 1500
const KEEP_DONE_MS = 5000 // 终态任务短暂保留供 UI 展示，之后移除

// 任务类型 → 展示名（与后端 TaskManager 常量对应）
const TYPE_LABEL = {
  DOWNLOAD: '导入',
  LOUDNESS: '响度分析',
  SUBTITLE: '字幕',
  TRANSCRIBE: 'AI 转写',
  LYRICS_FETCH: '获取歌词',
  MODEL_DOWNLOAD: '模型下载',
  TOOL_INSTALL: '安装工具'
}

export const useTaskStore = defineStore('mediaTasks', {
  state: () => ({
    tasks: [], // [{ taskId, taskType, status, progress, stage, musicId, error }]
    timer: null
  }),
  getters: {
    active: (s) => s.tasks.filter((t) => !isTaskTerminal(t.status)),
    hasActive() {
      return this.active.length > 0
    }
  },
  actions: {
    typeLabel(t) {
      return TYPE_LABEL[(t && t.taskType) || 'DOWNLOAD'] || '任务'
    },
    byId(id) {
      return this.tasks.find((t) => t.taskId === id) || null
    },
    /** 提交下载后登记任务并开始轮询（UploadView / 其他入口共用）。 */
    track(taskId, taskType = 'DOWNLOAD') {
      if (this.byId(taskId)) return this.byId(taskId)
      const t = { taskId, taskType, status: 'PENDING', progress: 0, stage: '排队中…', musicId: null, error: null }
      this.tasks.unshift(t)
      this.ensurePolling()
      return t
    },
    /** 登录后 / 应用挂载时恢复进行中任务（刷新页面不丢后台下载）。 */
    async restore() {
      try {
        const list = await api.mediaActiveTasks()
        if (Array.isArray(list) && list.length) {
          for (const t of list) {
            this.track(t.id, t.taskType)
          }
        }
      } catch (e) {
        /* 未登录/接口不可用：静默 */
      }
    },
    ensurePolling() {
      if (this.timer || !this.hasActive) return
      this.timer = setInterval(() => this.poll(), POLL_MS)
    },
    stopPolling() {
      if (this.timer) {
        clearInterval(this.timer)
        this.timer = null
      }
    },
    async poll() {
      const pendings = this.active
      if (!pendings.length) {
        this.stopPolling()
        return
      }
      const results = await Promise.allSettled(
        pendings.map((t) => api.mediaTask(t.taskId))
      )
      results.forEach((r, i) => {
        if (r.status !== 'fulfilled' || !r.value) return
        const old = pendings[i]
        const fresh = r.value
        old.status = fresh.status
        old.progress = Number(fresh.progress) || 0
        old.stage = fresh.stage || old.stage
        old.musicId = fresh.musicId ?? old.musicId
        old.error = fresh.error ?? old.error
        if (isTaskTerminal(old.status)) {
          this.notify(old)
          setTimeout(() => {
            this.tasks = this.tasks.filter((t) => t.taskId !== old.taskId)
          }, KEEP_DONE_MS)
        }
      })
      if (!this.hasActive) this.stopPolling()
    },
    notify(t) {
      const label = this.typeLabel(t)
      if (t.status === 'SUCCESS') {
        ElMessage.success({
          message: `${label}完成${t.musicId ? '，已入库' : ''}`,
          duration: 3000  // 3秒后自动消失
        })
      } else if (t.status === 'FAILED') {
        ElMessage.error({
          message: `${label}失败：${t.error || '未知原因'}`,
          duration: 3000  // 3秒后自动消失
        })
      }
    },
    async cancel(taskId) {
      await api.mediaCancel(taskId)
      const t = this.byId(taskId)
      if (t) t.status = 'CANCELLED'
    }
  }
})
