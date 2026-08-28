import { defineStore } from 'pinia'
import api from '../api'
import { resolveFileUrl } from '../utils'

/**
 * 歌词状态：LRC 解析（offset/♪ 剥离/元数据过滤/排序，与后端口径一致）、
 * 当前句计算（前奏/间奏提示）、一键获取任务轮询。
 * 「詞」按钮三态由 hasLyrics / panelOpen 驱动。
 */
export const useLyricsStore = defineStore('lyrics', {
  state: () => ({
    panelOpen: false,
    lines: [],          // [{time, text}]；time === null 表示纯文本歌词
    source: '',         // lrclib / whisper / subtitle / manual
    songId: null,
    loading: false,
    fetchTask: null,    // { id, status, progress, stage }
    _pollTimer: null
  }),

  getters: {
    hasLyrics: (s) => s.lines.length > 0,
    isPlain: (s) => s.lines.length > 0 && s.lines[0].time === null,
    sourceLabel: (s) => ({
      lrclib: '在线歌词库', whisper: '本地AI识别', subtitle: '平台字幕', manual: '手动录入'
    }[s.source] || '')
  },

  actions: {
    togglePanel() { this.panelOpen = !this.panelOpen },

    /** 换歌时加载：拉 LRC 文本 → 健壮解析。无歌词静默置空。
     *  列表卡片查询普遍不带 lyric_url 列（播放接口才有），行内缺字段时
     *  先查一次详情自愈——否则面板永远停在"没有歌词"。 */
    async loadForSong(song) {
      this.stopPoll()
      this.fetchTask = null
      this.songId = song ? song.id : null
      this.lines = []
      this.source = ''
      if (!song) return
      if (!song.lyricUrl) {
        try {
          const d = await api.songDetail(song.id)
          if (d && d.lyricUrl) song.lyricUrl = d.lyricUrl
        } catch (e) { /* 详情失败按无歌词处理 */ }
      }
      if (!song.lyricUrl) return
      this.loading = true
      try {
        const res = await fetch(resolveFileUrl(song.lyricUrl))
        if (res.ok) {
          const text = await res.text()
          const parsed = parseLrc(text)
          this.lines = parsed.lines
          this.source = parsed.source
        }
      } catch (e) { /* 静默：无歌词只是冷门曲 */ }
      this.loading = false
    },

    /** 一键获取：提交任务 + 轮询进度，完成后重载歌词。 */
    async startFetch() {
      if (!this.songId || this.fetchTask) return
      try {
        // 拦截器直接返回 data（{taskId, deduped?}），不再包一层 r.data
        const r = await api.lyricsFetch(this.songId)
        if (r && r.taskId) {
          this.fetchTask = { id: r.taskId, status: 'RUNNING', progress: 0, stage: r.deduped ? '已有任务进行中，复用…' : '' }
          this.poll()
        }
      } catch (e) {
        this.fetchTask = null
      }
    },

    poll() {
      this.stopPoll()
      this._pollTimer = setInterval(async () => {
        if (!this.fetchTask) return this.stopPoll()
        try {
          const t = (await api.mediaTask(this.fetchTask.id)) || {}
          this.fetchTask.status = t.status
          this.fetchTask.progress = t.progress || 0
          this.fetchTask.stage = t.stage || ''
          if (t.status === 'SUCCESS' || t.status === 'FAILED' || t.status === 'CANCELLED') {
            this.stopPoll()
            // 成功后重载当前曲歌词
            if (t.status === 'SUCCESS' && this._currentSong) {
              // lyric_url 可能刚被回写，重新取详情
              try {
                const d = await api.songDetail(this.songId)
                if (d) this._currentSong.lyricUrl = d.lyricUrl || this._currentSong.lyricUrl
              } catch (e) { /* 忽略 */ }
              await this.loadForSong(this._currentSong)
            }
            setTimeout(() => { this.fetchTask = null }, 6000)
          }
        } catch (e) { /* 轮询失败静默重试 */ }
      }, 1200)
    },

    stopPoll() {
      if (this._pollTimer) clearInterval(this._pollTimer)
      this._pollTimer = null
    },

    /** 供播放侧喂当前歌曲对象（用于获取完成后刷新 lyricUrl） */
    setCurrentSong(song) { this._currentSong = song }
  }
})

/** LRC 解析（前端口径，与后端 LrcSupport 一致）。 */
export function parseLrc(content) {
  const lines = String(content || '').split('\n')
  let source = ''
  const srcM = lines.map(l => l.trim().match(/^\[source:([^\]]+)\]/)).find(Boolean)
  if (srcM) source = srcM[1]
  if (source === 'whisper' || /whisper\.cpp/.test(content)) source = source || 'whisper'

  let offsetSec = 0
  for (const line of lines) {
    const om = line.trim().match(/^\[offset:\s*([+-]?\d+)\s*\]/i)
    if (om) offsetSec = parseInt(om[1], 10) / 1000
  }

  const isMeta = t => /^(?:作词|作曲|编曲|演唱|歌手|原唱|制作|制作人|词|曲)\s*[:：]/.test(t)
        || /^(?:作词|作曲|编曲|演唱|歌手|原唱|制作人?|作者|作家)$/.test(t)
  const isPureTag = t => /^\[[^\]]*\]$/.test(t)

  const out = []
  let hasTs = false
  for (const raw of lines) {
    const line = raw.trim()
    if (!line) continue
    const m = line.match(/\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\](.*)/)
    if (!m) continue
    hasTs = true
    let text = (m[4] || '').trim().replace(/^[♪♫♩♬]+/u, '').trim()
    if (/^[~～ー\-—\s]*$/.test(text)) text = ''
    if (!text || isMeta(text) || isPureTag(text)) continue
    const frac = m[3] ? parseInt(m[3], 10) / Math.pow(10, m[3].length) : 0
    out.push({ time: Math.max(0, +m[1] * 60 + +m[2] + frac - offsetSec), text })
  }
  if (!hasTs) {
    // 纯文本歌词：按行保留
    const plain = lines.map(l => l.trim()).filter(l => l && !isPureTag(l) && !/^\[source:/.test(l))
    return { lines: plain.map(t => ({ time: null, text: t })), source }
  }
  out.sort((a, b) => a.time - b.time)
  return { lines: out, source }
}

/** 当前句计算：句首时刻一到即切换；前奏/长间奏返回提示文本。 */
export function currentLineIndex(lines, t) {
  if (!lines.length || lines[0].time === null) return -1
  let idx = -1
  for (let i = 0; i < lines.length; i++) {
    if (lines[i].time <= t) idx = i
    else break
  }
  return idx
}

export function interludeText(lines, idx, t) {
  if (idx < 0) {
    const first = lines[0]
    return first && first.time - t > 3 ? '♪ 前奏 ♪' : null
  }
  const cur = lines[idx], next = lines[idx + 1]
  if (cur && next && next.time - t > 12 && t - cur.time > 8) return '♪ 间奏 ♪'
  return null
}
