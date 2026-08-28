// 播放器 store：HTML5 Audio 单例 + 队列/模式/音量/进度状态
// 循环模式：0 顺序播放 1 单曲循环 2 列表循环 3 随机播放
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import api from '../api'
import { useUserStore } from './user'
import { resolveFileUrl, singerName } from '../utils'
import { setOutputVolume } from '../utils/audioAnalyser'

let audioEl = null
let listenersBound = false
let playSeq = 0 // 播放请求代际号：新的播放/停止使旧的在途请求作废，防止慢响应劫持当前曲目

function fmtShort(t) {
  const s = Math.max(0, Math.floor(t || 0))
  return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0')
}

function getAudio() {
  if (!audioEl) {
    // HMR 热替换本模块会新建元素，旧元素若还在放就成了无法控制的"幽灵声"：接管并停掉
    if (typeof window !== 'undefined' && window.__mdAudioEl instanceof HTMLMediaElement) {
      audioEl = window.__mdAudioEl
      try { audioEl.pause() } catch (e) { /* 忽略 */ }
    } else {
      audioEl = new Audio()
    }
    if (typeof window !== 'undefined') window.__mdAudioEl = audioEl
    audioEl.preload = 'auto'
  }
  return audioEl
}

/** 暴露 audio 元素给 Web Audio 频谱分析（MediaElementSource 只能建一次，必须复用同一元素） */
export function getAudioEl() {
  return getAudio()
}

export const PLAY_MODES = [
  { value: 0, label: '顺序播放' },
  { value: 1, label: '单曲循环' },
  { value: 2, label: '列表循环' },
  { value: 3, label: '随机播放' }
]

export const usePlayerStore = defineStore('player', {
  state: () => ({
    queue: [],
    currentIndex: -1,
    mode: 0,
    playing: false,
    loading: false,
    volume: Number(localStorage.getItem('md_volume') || 0.8), // 用户基准音量 0-1
    volumeGain: null, // 当前曲目响度补偿增益 dB（null 表示无）
    currentTime: 0,
    duration: 0,
    dragging: false, // 进度条拖拽中，暂停 timeupdate 回写
    rate: 1, // 播放倍速
    abA: null, // A-B 复读起点（秒）
    abB: null, // A-B 复读终点（秒）
    currentReported: false // 当前曲是否已上报播放（防 ended 与切歌补报双计）
  }),
  getters: {
    current: (s) => s.queue[s.currentIndex] || null,
    currentSinger: (s) => singerName(s.queue[s.currentIndex]),
    modeLabel: (s) => (PLAY_MODES[s.mode] || PLAY_MODES[0]).label
  },
  actions: {
    // 绑定 audio 事件（App 挂载时调用一次）
    initAudio() {
      const a = getAudio()
      this.applyVolume()
      if (listenersBound) return
      listenersBound = true
      a.addEventListener('timeupdate', () => {
        if (!this.dragging) this.currentTime = a.currentTime || 0
        // A-B 复读：到达 B 点回到 A 点循环
        if (this.abA !== null && this.abB !== null && a.currentTime >= this.abB) {
          a.currentTime = this.abA
        }
      })
      a.addEventListener('durationchange', () => {
        this.duration = isFinite(a.duration) ? a.duration : 0
      })
      a.addEventListener('loadedmetadata', () => {
        if (isFinite(a.duration) && a.duration > 0) this.duration = a.duration
        a.playbackRate = this.rate // 换曲后保持倍速
      })
      a.addEventListener('play', () => {
        this.playing = true
      })
      a.addEventListener('playing', () => {
        this.playing = true
      })
      a.addEventListener('pause', () => {
        this.playing = false
      })
      a.addEventListener('ended', () => this.handleEnded())
      a.addEventListener('error', () => {
        if (this.current) {
          this.playing = false
          ElMessage.error('音频加载失败，请稍后重试')
        }
      })
    },
    // 响度补偿：最终音量 = 基准音量 * 10^(gain/20)，clamp 0-1。
    // 音量优先落 WebAudio 增益节点（在频谱分析器之后）：应用内静音/音量0 不影响动效；
    // 图未建时退回 audio.volume，建图瞬间由 audioAnalyser 读回最近目标值补挂
    applyVolume() {
      const a = getAudio()
      let v = this.volume
      const gain = Number(this.volumeGain)
      if (this.volumeGain !== null && this.volumeGain !== undefined && !Number.isNaN(gain)) {
        v = this.volume * Math.pow(10, gain / 20)
      }
      v = Math.min(1, Math.max(0, v))
      if (setOutputVolume(v)) {
        a.volume = 1 // 增益节点已接管，元素保持满幅（否则双重衰减，实际响度变轻）
      } else {
        a.volume = v
      }
    },
    setVolume(v) {
      this.volume = Math.min(1, Math.max(0, v))
      localStorage.setItem('md_volume', String(this.volume))
      this.applyVolume()
    },
    async playAt(index) {
      if (index < 0 || index >= this.queue.length) return
      this.flushReport() // 切歌前给上一首补报（bug2：否则没播完的歌永远进不了最近播放）
      const song = this.queue[index]
      const a = getAudio()
      const seq = ++playSeq
      this.currentIndex = index
      this.currentTime = 0
      this.duration = Number(song.duration) || 0
      this.volumeGain = null
      this.currentReported = false
      this.loading = true
      try {
        const data = await api.songPlay(song.id)
        if (seq !== playSeq) return // 期间已切歌/停止，本次结果作废
        const url = data && (data.fileUrl || data.url)
        if (!url) {
          // 取不到地址：停掉旧曲并复位，避免"界面已切歌、耳朵还在放上一首"
          a.pause()
          this.playing = false
          ElMessage.error('未获取到播放地址')
          return
        }
        // 播放视图带回完整元数据（coverUrl/lyricUrl 等），合并进队列行：
        // 收藏/最近播放/歌单等列表查询不含封面列，底部栏与歌词面板也能正常显示
        if (data && typeof data === 'object') {
          if (data.coverUrl) song.coverUrl = data.coverUrl
          if (data.lyricUrl) song.lyricUrl = data.lyricUrl
          if (data.name) song.name = data.name
          if (data.singer) song.singer = data.singer
          if (data.duration) song.duration = data.duration
        }
        if (data.volumeGain !== null && data.volumeGain !== undefined && data.volumeGain !== '') {
          this.volumeGain = Number(data.volumeGain)
        }
        a.src = resolveFileUrl(url)
        this.applyVolume()
        await a.play()
      } catch (e) {
        if (seq === playSeq) {
          a.pause()
          this.playing = false
          // 歌曲已被删除（清库/管理端删除）时从队列摘除并自动播下一首，
          // 否则"幽灵曲目"会一直挂在底部栏：有歌名、无封面、放不出来
          const gone = e && /不存在|已下架|审核中/.test(e.message || '')
          if (gone && this.queue[index] && this.queue[index].id === song.id) {
            this.queue.splice(index, 1)
            if (this.queue.length) {
              this.playAt(Math.min(index, this.queue.length - 1))
            } else {
              this.stop(true)
            }
            return
          }
        }
      } finally {
        if (seq === playSeq) this.loading = false
      }
    },
    // 整队播放：点击某行时把当前列表作为队列
    playQueue(songs, index = 0) {
      const list = (songs || []).filter(Boolean)
      if (!list.length) return
      this.queue = list
      this.playAt(Math.min(Math.max(index, 0), list.length - 1))
    },
    playSong(song) {
      if (!song) return
      this.playQueue([song], 0)
    },
    toggle() {
      const a = getAudio()
      if (!this.current) {
        if (this.queue.length) this.playAt(0)
        return
      }
      if (a.paused) {
        a.play().catch(() => {})
      } else {
        a.pause()
      }
    },
    next(auto = false) {
      if (!this.queue.length) return
      if (this.mode === 3) {
        // 随机
        if (this.queue.length === 1) {
          if (!auto) this.playAt(this.currentIndex)
          return
        }
        let i = this.currentIndex
        while (i === this.currentIndex) {
          i = Math.floor(Math.random() * this.queue.length)
        }
        this.playAt(i)
        return
      }
      const nextIndex = this.currentIndex + 1
      if (nextIndex >= this.queue.length) {
        if (this.mode === 2 || !auto) {
          this.playAt(0) // 列表循环；或手动点击时回到开头
        } else {
          this.playing = false // 顺序模式播完最后一条停止
        }
        return
      }
      this.playAt(nextIndex)
    },
    prev() {
      if (!this.queue.length) return
      if (this.currentTime > 3) {
        // 播放超过 3 秒先回到开头
        this.seek(0)
        return
      }
      this.playAt((this.currentIndex - 1 + this.queue.length) % this.queue.length)
    },
    seek(t) {
      const a = getAudio()
      const time = Number(t)
      if (this.current && isFinite(time) && time >= 0) {
        a.currentTime = Math.min(time, a.duration || time)
        this.currentTime = time
      }
    },
    cycleMode() {
      this.mode = (this.mode + 1) % PLAY_MODES.length
      ElMessage({ message: this.modeLabel, grouping: true, duration: 1000 })
    },
    setRate(r) {
      this.rate = r
      getAudio().playbackRate = r
    },
    // A-B 复读：第一次点设 A，第二次点设 B 并开始循环，第三次清除
    cycleAb() {
      if (this.abA === null) {
        this.abA = this.currentTime
        ElMessage({ message: 'A 点已设置 ' + fmtShort(this.abA), grouping: true, duration: 1200 })
      } else if (this.abB === null) {
        if (this.currentTime <= this.abA) {
          ElMessage.warning('B 点需要晚于 A 点')
          return
        }
        this.abB = this.currentTime
        ElMessage({ message: 'A-B 复读开始', grouping: true, duration: 1200 })
      } else {
        this.clearAb()
        ElMessage({ message: '已清除 A-B 复读', grouping: true, duration: 1200 })
      }
    },
    clearAb() {
      this.abA = null
      this.abB = null
    },
    removeAt(index) {
      if (index < 0 || index >= this.queue.length) return
      const wasCurrent = index === this.currentIndex
      this.queue.splice(index, 1)
      if (index < this.currentIndex) {
        this.currentIndex -= 1
      } else if (wasCurrent) {
        // 移除的是当前曲：尝试播同位置下一首
        const a = getAudio()
        a.pause()
        if (index < this.queue.length) {
          this.playAt(index)
        } else if (this.queue.length) {
          this.playAt(this.queue.length - 1)
        } else {
          this.stop(true)
        }
      }
    },
    stop(keepQueue = false) {
      this.flushReport() // 停止前补报，再作废在途请求（顺序不能反：flush 要读旧曲目与进度）
      playSeq++ // 使在途的播放请求作废（否则停止后慢响应又会把歌放出来）
      const a = getAudio()
      a.pause()
      if (!a.paused) a.currentTime = 0
      a.removeAttribute('src')
      this.playing = false
      this.currentIndex = -1
      this.currentTime = 0
      this.duration = 0
      this.volumeGain = null
      this.clearAb()
      if (!keepQueue) this.queue = []
    },
    // 切歌/停止时补报上一首：听过 30 秒或过半即视为有效播放（bug2）
    flushReport() {
      const song = this.queue && this.queue[this.currentIndex]
      if (!song || this.currentReported) return
      const a = getAudio()
      const played = Math.round(a.currentTime || this.currentTime || 0)
      const dur = Number(this.duration || song.duration) || 0
      if (played <= 0 || (played < 30 && (dur <= 0 || played < dur * 0.5))) return
      this.currentReported = true
      const user = useUserStore()
      if (user.isLogin) {
        api.playReport({ songId: song.id, playDuration: played }).catch(() => {})
      }
    },
    async handleEnded() {
      const song = this.current
      const a = getAudio()
      const played = Math.round(a.currentTime || this.duration || 0)
      // 播完才上报：{ songId, playDuration }
      if (song) {
        this.currentReported = true
        const user = useUserStore()
        if (user.isLogin) {
          api.playReport({ songId: song.id, playDuration: played }).catch(() => {})
        }
      }
      if (this.mode === 1) {
        a.currentTime = 0
        a.play().catch(() => {})
        return
      }
      this.next(true)
    }
  }
})
