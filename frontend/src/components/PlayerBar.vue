<template>
  <footer class="player-bar" ref="rootEl">
    <!-- 左：当前曲目 + 收藏（喜欢属于曲日本身，语境化放封面旁） -->
    <div class="track">
      <div class="cv">
        <img v-if="player.current && player.current.coverUrl && !coverFail"
          :src="resolveFileUrl(player.current.coverUrl)" alt="封面" @error="coverFail = true" />
        <div v-else class="ph">♪</div>
      </div>
      <div class="meta">
        <b>{{ player.current ? player.current.name : '未在播放' }}</b>
        <span>{{ player.current ? player.currentSinger : 'Music Dreamer' }}</span>
      </div>
      <button class="ctl heart" :class="{ on: currentCollected }" :disabled="!player.current"
        :title="currentCollected ? '取消收藏' : '收藏当前歌曲'" @click="collectCurrent">
        <svg width="16" height="16" viewBox="0 0 24 24" :fill="currentCollected ? 'currentColor' : 'none'"
          stroke="currentColor" stroke-width="2">
          <path d="M12 21s-8-4.5-8-10a4.5 4.5 0 0 1 8-3 4.5 4.5 0 0 1 8 3c0 5.5-8 10-8 10z" />
        </svg>
      </button>
    </div>

    <!-- 中：歌词开关 + 控制 + 进度（preview-v2：冻结的回声波形当进度条） -->
    <div class="mid">
      <div class="ctrls">
        <button class="ctl word" :class="wordState" :disabled="!player.current"
          :title="wordTitle" @click="lyrics.togglePanel()">
          <span class="word-glyph">詞</span>
        </button>
        <button class="ctl" :title="player.modeLabel" @click="player.cycleMode()">
          <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M17 2v6h-6M3 12a9 9 0 0 1 15-6.7L21 8" />
            <path d="M7 22v-6h6M21 12a9 9 0 0 1-15 6.7L3 16" />
            <text v-if="player.mode === 1" x="9.5" y="15" font-size="9" fill="currentColor" stroke="none"
              font-weight="700">1</text>
            <text v-else-if="player.mode === 3" x="8" y="15" font-size="9" fill="currentColor" stroke="none"
              font-weight="700">∞</text>
          </svg>
        </button>
        <button class="ctl" title="上一首" @click="player.prev()">
          <svg width="17" height="17" viewBox="0 0 24 24" fill="currentColor">
            <path d="M7 6h2v12H7zm3 0 9 6-9 6z" transform="scale(-1,1) translate(-24,0)" />
          </svg>
        </button>
        <button class="play" ref="playBtn" :title="player.playing ? '暂停' : '播放'" @click="player.toggle()">
          <svg v-if="player.loading" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
            stroke-width="2" class="spin">
            <path d="M21 12a9 9 0 1 1-6.2-8.56" />
          </svg>
          <svg v-else-if="player.playing" width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
            <rect x="6" y="5" width="4" height="14" rx="1" />
            <rect x="14" y="5" width="4" height="14" rx="1" />
          </svg>
          <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
            <path d="M7 5v14l12-7z" />
          </svg>
        </button>
        <button class="ctl" title="下一首" @click="player.next(false)">
          <svg width="17" height="17" viewBox="0 0 24 24" fill="currentColor">
            <path d="M7 6h2v12H7zm3 0 9 6-9 6z" />
          </svg>
        </button>
        <button class="ctl" title="播放队列" @click="queueOpen = !queueOpen">
          <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="4" y1="6" x2="20" y2="6" /><line x1="4" y1="12" x2="20" y2="12" />
            <line x1="4" y1="18" x2="12" y2="18" />
          </svg>
        </button>
      </div>

      <div class="seek">
        <time class="mono">{{ fmt(player.currentTime) }}</time>
        <div class="seek-bar" ref="barEl" role="slider" aria-label="进度" aria-valuemin="0" aria-valuemax="100"
          :aria-valuenow="Math.round(pct)" tabindex="0"
          @pointerdown="onBarDown" @pointermove="onBarMove" @pointerup="onBarUp" @pointercancel="onBarUp">
          <i class="fill" :style="{ width: pct + '%' }"></i>
        </div>
        <time class="mono">{{ fmt(player.duration) }}</time>
      </div>
    </div>

    <!-- 右：倍速 / A-B / 音量（听力工具组，收紧排列） -->
    <div class="end">
      <button class="pill" title="播放速度（点击切换）" @click="cycleRate">{{ rateLabel }}</button>
      <button class="pill"
        :class="{ 'set-a': player.abA !== null && player.abB === null, looping: player.abA !== null && player.abB !== null }"
        title="A-B 复读（第一次点设 A 点，第二次设 B 点开始循环，第三次清除）" @click="player.cycleAb()">
        {{ abLabel }}
      </button>
      <div class="vol">
        <button class="ctl" :title="muted ? '取消静音' : '静音'" @click="toggleMute">
          <svg v-if="muted || player.volume === 0" width="16" height="16" viewBox="0 0 24 24" fill="none"
            stroke="currentColor" stroke-width="2">
            <path d="M11 5 6 9H2v6h4l5 4V5z" /><line x1="23" y1="9" x2="17" y2="15" />
            <line x1="17" y1="9" x2="23" y2="15" />
          </svg>
          <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M11 5 6 9H2v6h4l5 4V5z" />
            <path d="M15.5 8.5a5 5 0 0 1 0 7" />
          </svg>
        </button>
        <input type="range" min="0" max="100" :value="volPct" aria-label="音量" @input="onVolume" />
      </div>
    </div>

    <!-- 播放队列浮层 -->
    <div v-if="queueOpen" class="queue-pop">
      <div v-if="!player.queue.length" class="muted" style="padding: 8px">队列为空，去首页挑一首歌吧</div>
      <div v-for="(s, i) in player.queue" :key="s.id + '-' + i" class="queue-item"
        :class="{ active: i === player.currentIndex }" @click="player.playAt(i)">
        <span class="qidx">{{ i + 1 }}</span>
        <span class="qname">{{ s.name }}</span>
        <span class="qsinger">{{ singerName(s) }}</span>
        <span class="qdel" title="移除" @click.stop="player.removeAt(i)">×</span>
      </div>
      <div v-if="player.queue.length" style="text-align: right; margin-top: 6px">
        <button class="btn btn-secondary" style="padding: 4px 10px; font-size: 12px" @click="player.stop(true)">
          清空队列
        </button>
      </div>
    </div>
  </footer>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { usePlayerStore, getAudioEl } from '../stores/player'
import { useUserStore } from '../stores/user'
import { useLyricsStore } from '../stores/lyrics'
import { resolveFileUrl, singerName } from '../utils'
import { gsap, prefersReduced } from '../utils/motion'
import { getAnalyser } from '../utils/audioAnalyser'

const player = usePlayerStore()
const userStore = useUserStore()
const lyrics = useLyricsStore()
const coverFail = ref(false)
const queueOpen = ref(false)
const barEl = ref(null)
const rootEl = ref(null)
const playBtn = ref(null)
const dragging = ref(false)
const muted = ref(false)
const lastVol = ref(0.8)

onMounted(() => {
  if (rootEl.value && !prefersReduced) {
    gsap.from(rootEl.value, { y: 90, duration: 0.6, ease: 'power3.out', clearProps: 'transform' })
  }
  if (!prefersReduced && !rafId) rafId = requestAnimationFrame(pulseLoop)
})

onBeforeUnmount(() => { if (rafId) cancelAnimationFrame(rafId) })

// 播放/暂停切换时给橙色圆钮一个轻微回弹
watch(() => player.playing, () => {
  if (playBtn.value && !prefersReduced) {
    gsap.fromTo(playBtn.value, { scale: 0.82 }, { scale: 1, duration: 0.4, ease: 'back.out(3)' })
  }
})

// 换曲重置封面加载失败标记：否则一首封面 404 后整个会话都不再显示任何封面
watch(() => player.current && player.current.id, () => {
  coverFail.value = false
})

/* ---------- 播放能量 → 呼吸辉光（Web Audio Analyser，真音频数据） ----------
   进度条本身用普通轨道；频谱可视化在首页 hero。这里只保留：
   全局 Analyser 单例读取整体能量，驱动底栏上缘的三色呼吸光。 */
let analyser = null
let rafId = 0
let pulseV = 0
const FREQ = new Uint8Array(128)

function pulseLoop() {
  rafId = 0
  let target = 0
  if (player.playing && analyser) {
    analyser.getByteFrequencyData(FREQ)
    let sum = 0
    for (let i = 2; i < 90; i++) sum += FREQ[i]
    target = Math.min(1, (sum / 88 / 255) * 1.6)
  }
  pulseV += (target - pulseV) * (target > pulseV ? 0.4 : 0.08) // 快起慢落
  if (rootEl.value) rootEl.value.style.setProperty('--pulse', pulseV.toFixed(3))
  if (player.playing || pulseV > 0.002) rafId = requestAnimationFrame(pulseLoop)
}

watch(() => player.playing, (on) => {
  if (on && !prefersReduced) analyser = getAnalyser(getAudioEl())
  if (!rafId && !prefersReduced) rafId = requestAnimationFrame(pulseLoop)
}, { immediate: true }) // bug13：切页回来时 playing 已是 true 不再触发 watch，immediate 补挂分析器
const pct = computed(() => {
  const d = player.duration || 0
  return d > 0 ? Math.min(100, (player.currentTime / d) * 100) : 0
})
const volPct = computed(() => Math.round((player.volume || 0) * 100))
const currentCollected = computed(() =>
  player.current ? userStore.isCollected(player.current.id) : false
)

/* 「詞」按钮三态：无歌词（暗灰）/ 有歌词未开（亮）/ 面板开启（品牌渐变） */
const wordState = computed(() => ({
  none: !lyrics.hasLyrics,
  open: lyrics.panelOpen
}))
const wordTitle = computed(() => {
  if (!lyrics.hasLyrics) return '暂无歌词，点击打开歌词面板获取'
  return lyrics.panelOpen ? '收起歌词' : '展开歌词'
})

const RATES = [0.5, 0.75, 1, 1.25, 1.5, 2]
const rateLabel = computed(() => (Number(player.rate) || 1).toFixed(2).replace(/0$/, '') + 'x')
const abLabel = computed(() => {
  if (player.abA === null) return 'A-B'
  if (player.abB === null) return 'A·'
  return 'A-B↻'
})

function cycleRate() {
  const i = RATES.indexOf(Number(player.rate) || 1)
  player.setRate(RATES[(i + 1) % RATES.length])
}

async function collectCurrent() {
  if (!player.current) return
  await userStore.toggleCollect(player.current.id)
}

function fmt(t) {
  const s = Math.max(0, Math.floor(t || 0))
  return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0')
}

function ratioFromEvent(e) {
  const el = barEl.value
  if (!el) return 0
  const rect = el.getBoundingClientRect()
  return Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
}

function onBarDown(e) {
  dragging.value = true
  player.seek(ratioFromEvent(e) * (player.duration || 0))
  if (e.currentTarget.setPointerCapture) e.currentTarget.setPointerCapture(e.pointerId)
}
function onBarMove(e) {
  if (dragging.value) player.seek(ratioFromEvent(e) * (player.duration || 0))
}
function onBarUp() {
  dragging.value = false
}

function onVolume(e) {
  const v = Number(e.target.value) / 100
  muted.value = false
  player.setVolume(v)
  if (v > 0) lastVol.value = v
}

function toggleMute() {
  if (muted.value || player.volume === 0) {
    player.setVolume(lastVol.value || 0.8)
    muted.value = false
  } else {
    lastVol.value = player.volume
    player.setVolume(0)
    muted.value = true
  }
}
</script>

<style scoped>
/* 播放条：横跨整个窗口下缘的一条唱机台面 —— 与侧栏、内容同宽，
   无圆角无悬浮，画布环境光直接透过毛玻璃 */
.player-bar {
  flex-shrink: 0;
  height: 84px;
  display: flex;
  align-items: center;
  gap: 22px;
  padding: 0 32px;
  background: rgba(255, 254, 250, 0.78);
  backdrop-filter: blur(22px) saturate(1.15);
  -webkit-backdrop-filter: blur(22px) saturate(1.15);
  border-top: 1px solid rgba(92, 72, 50, 0.1);
  position: relative;
  z-index: 20;
}

/* 呼吸辉光：播放时按整体频谱能量（--pulse，JS 每帧回写）把三色光晕投到内容区，
   暂停即熄灭 —— 音响亮着的隐喻 */
.player-bar::after {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  top: -30px;
  height: 30px;
  background: linear-gradient(90deg, #ff6b1a, #e11d92 50%, #0699b8);
  filter: blur(24px);
  opacity: calc(var(--pulse, 0) * 0.32);
  pointer-events: none;
}

/* 左：当前曲目 + 收藏 */
.track {
  display: flex;
  align-items: center;
  gap: 13px;
  min-width: 0;
}
.track .heart {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
}
.track .heart.on {
  color: var(--magenta);
  filter: drop-shadow(0 0 6px rgba(225, 29, 146, 0.35));
}
.track .heart:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.track .cv {
  width: 52px;
  height: 52px;
  border-radius: 10px;
  overflow: hidden;
  flex-shrink: 0;
  position: relative;
  background: var(--surface-2);
  border: 1px solid var(--border);
}
.track .cv img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.track .cv .ph {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: var(--accent);
}
.track .meta {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.track b {
  font-size: 13.5px;
  font-weight: 600;
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.track span {
  font-size: 12px;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 中：控制 + 波形进度 */
.mid {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 9px;
  min-width: 0;
}
.ctrls {
  display: flex;
  align-items: center;
  gap: 20px;
}
.ctl {
  width: 28px;
  height: 28px;
  color: var(--text-muted);
  transition: color 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}
.ctl:hover {
  color: var(--text);
}
.play {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: var(--accent);
  display: grid;
  place-items: center;
  color: var(--accent-text);
  transition: transform 0.15s ease, box-shadow 0.2s ease;
}
.play:hover {
  transform: scale(1.06);
  box-shadow: 0 5px 18px rgba(255, 107, 26, 0.4);
}
.spin {
  animation: pbspin 1s linear infinite;
}
@keyframes pbspin {
  to { transform: rotate(360deg); }
}

.seek {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  max-width: 540px;
}
.seek time {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
  /* 定宽 5 字符（可容 99:59）：时间文本长度变化（0:00 -> 12:34）不再挤压进度条 */
  width: 5ch;
  flex-shrink: 0;
  text-align: center;
  flex-shrink: 0;
}
/* 普通进度条：暖墨细轨道 + 品牌渐变填充，悬停加粗 */
.seek-bar {
  flex: 1;
  height: 14px;
  min-width: 0;
  display: flex;
  align-items: center;
  cursor: pointer;
  touch-action: none;
  position: relative;
}
.seek-bar::before {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  height: 4px;
  border-radius: 2px;
  background: rgba(92, 72, 50, 0.16);
  transition: height 0.15s ease;
}
.seek-bar .fill {
  position: absolute;
  left: 0;
  height: 4px;
  border-radius: 2px;
  background: linear-gradient(90deg, var(--accent), var(--magenta));
  transition: height 0.15s ease;
}
.seek-bar .fill::after {
  content: "";
  position: absolute;
  right: -5px;
  top: 50%;
  width: 10px;
  height: 10px;
  transform: translateY(-50%) scale(0);
  border-radius: 50%;
  background: var(--accent);
  box-shadow: var(--glow-accent);
  transition: transform 0.15s ease;
}
.seek-bar:hover::before,
.seek-bar:hover .fill {
  height: 7px;
}
.seek-bar:hover .fill::after {
  transform: translateY(-50%) scale(1);
}

/* 右：倍速 / A-B / 音量 —— 听力工具组：更紧凑的间距 + 音量前细分隔线，
   避免控件稀疏地散在窗口右缘 */
.end {
  display: flex;
  align-items: center;
  gap: 9px;
  justify-content: flex-end;
}
.end .vol {
  gap: 7px;
  padding-left: 13px;
  border-left: 1px solid rgba(92, 72, 50, 0.14);
}
/* 「詞」按钮：繁体言字旁，三态（无歌词暗灰 / 有歌词亮 / 开启品牌渐变） */
.word {
  font-size: 16px;
  font-weight: 600;
  color: rgba(92, 72, 50, 0.3);
  transition: color 0.2s ease, text-shadow 0.2s ease, transform 0.15s ease;
}
.word:not(.none):not(.open) {
  color: var(--text);
}
.word.open {
  background: linear-gradient(135deg, var(--accent), var(--magenta));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  text-shadow: none;
}
.word:not(:disabled):hover {
  transform: scale(1.12);
}
.word:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.pill {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--border);
  color: var(--text-muted);
  transition: all 0.15s ease;
  white-space: nowrap;
}
.pill:hover {
  color: var(--text);
  border-color: rgba(0, 0, 0, 0.25);
}
.pill.set-a {
  color: var(--warning);
  border-color: rgba(255, 194, 75, 0.5);
}
.pill.looping {
  color: var(--accent);
  border-color: rgba(255, 107, 26, 0.5);
}
.vol {
  display: flex;
  align-items: center;
  gap: 8px;
}
.vol input[type="range"] {
  width: 74px;
  accent-color: var(--accent);
  height: 3px;
  cursor: pointer;
}

@media (max-width: 980px) {
  .end .pill {
    display: none;
  }
  .vol input[type="range"] {
    display: none;
  }
  .player-bar {
    padding: 0 14px;
  }
}

/* ---------- 播放队列浮层 ---------- */
.queue-pop {
  position: absolute;
  right: 20px;
  bottom: 92px;
  width: 340px;
  max-height: 340px;
  overflow-y: auto;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  padding: 8px;
  z-index: 60;
}
.queue-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
}
.queue-item:hover {
  background: var(--glass);
}
.queue-item.active {
  background: var(--cyan-soft);
}
.queue-item.active .qname {
  color: var(--cyan);
}
.qidx {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-faint);
  width: 20px;
  flex-shrink: 0;
  text-align: right;
}
.qname {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.qsinger {
  font-size: 11.5px;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 90px;
  flex-shrink: 0;
}
.qdel {
  color: var(--text-faint);
  font-size: 15px;
  padding: 0 4px;
  flex-shrink: 0;
}
.qdel:hover {
  color: var(--error);
}
</style>
