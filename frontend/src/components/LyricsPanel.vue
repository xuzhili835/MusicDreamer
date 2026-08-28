<template>
  <transition name="pane">
    <aside v-if="lyrics.panelOpen" class="lyrics-pane">
      <div class="pane-inner">

        <!-- 头部：封面 + 曲目 + 歌词来源 + 重新获取 -->
        <header class="pane-head">
          <div class="cv">
            <img v-if="player.current && player.current.coverUrl && !coverFail"
              :src="resolveFileUrl(player.current.coverUrl)" alt="封面" @error="coverFail = true" />
            <div v-else class="ph">♪</div>
          </div>
          <div class="head-meta">
            <b class="song-name">{{ player.current ? player.current.name : '未在播放' }}</b>
            <span class="song-singer">{{ player.current ? player.currentSinger : '' }}</span>
            <div class="badges">
              <span v-if="lyrics.sourceLabel" class="badge">{{ lyrics.sourceLabel }}</span>
              <span v-if="lyrics.isPlain" class="badge plain">纯文本 · 无时间轴</span>
            </div>
          </div>
          <button class="refresh" :class="{ busy: !!lyrics.fetchTask }"
            :title="lyrics.fetchTask ? '正在获取…' : '重新获取（在线优先，覆盖当前）'"
            :disabled="!player.current || !!lyrics.fetchTask" @click="lyrics.startFetch()">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 12a9 9 0 1 1-2.64-6.36" />
              <path d="M21 3v6h-6" />
            </svg>
          </button>
          <button class="close" title="关闭歌词面板（也可再点播放条的「詞」）" @click="lyrics.togglePanel()">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </header>

        <!-- 一键获取进度 -->
        <div v-if="lyrics.fetchTask" class="task">
          <div class="task-row">
            <span class="task-stage">{{ lyrics.fetchTask.stage || '排队中…' }}</span>
            <span class="task-pct">{{ lyrics.fetchTask.progress || 0 }}%</span>
          </div>
          <div class="task-bar"><i :style="{ width: (lyrics.fetchTask.progress || 0) + '%' }"></i></div>
          <div v-if="lyrics.fetchTask.status === 'FAILED'" class="task-fail">
            获取失败：{{ lyrics.fetchTask.stage }}，可点击右上角重试
          </div>
        </div>

        <!-- 歌词主体 -->
        <div class="pane-body" ref="bodyEl" @wheel.passive="onUserScroll" @touchmove.passive="onUserScroll">
          <div v-if="lyrics.loading" class="empty">加载歌词中…</div>

          <div v-else-if="!lyrics.hasLyrics" class="empty">
            <div class="empty-icon">
              <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                <circle cx="6.5" cy="18" r="3" /><circle cx="17.5" cy="16" r="3" />
                <path d="M9.5 18V5l11-2v13" />
              </svg>
            </div>
            <p class="empty-title">这首歌还没有歌词</p>
            <p class="empty-sub">一键获取先查在线歌词库（LRCLIB），<br />查不到时自动用本地 AI 听写并生成时间轴</p>
            <button class="btn btn-primary empty-fetch" :disabled="!player.current" @click="lyrics.startFetch()">
              一键获取歌词
            </button>
          </div>

          <div v-else-if="lyrics.isPlain" class="plain">
            <p v-for="(l, i) in lyrics.lines" :key="i">{{ l.text }}</p>
          </div>

          <template v-else>
            <div v-for="(l, i) in displayLines" :key="l.key" class="line"
              :class="{ active: i === activeIdx, past: i < activeIdx, inter: l.interlude }"
              :ref="el => lineEls[i] = el" :title="l.interlude ? null : '点击跳到这句'"
              @click="seekTo(l)">
              <span class="txt">{{ l.text }}</span>
              <span v-if="!l.interlude" class="ts">{{ fmtTs(l.time) }}</span>
            </div>
          </template>
        </div>
      </div>
    </aside>
  </transition>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { usePlayerStore } from '../stores/player'
import { useLyricsStore } from '../stores/lyrics'
import { resolveFileUrl } from '../utils'

const player = usePlayerStore()
const lyrics = useLyricsStore()
const bodyEl = ref(null)
const lineEls = []
const coverFail = ref(false)

/* ---------- 展示行：歌词行 + 按需插入间奏虚行（前奏/长间奏 >= 12s） ----------
   interlude 行带 start/end 窗口：进入窗口后成为当前行（♪ 浮动动效），
   下一句歌词开始后自然让位 —— 与 Sakura Echo 的前奏/间奏提示同一口径 */
const INTER_GAP = 12
const displayLines = computed(() => {
  const L = lyrics.lines
  if (!L.length || lyrics.isPlain) return []
  const out = []
  if (L[0].time > 8) {
    out.push({ key: 'i-pre', interlude: true, text: '前奏', hit: 0 })
  }
  for (let i = 0; i < L.length; i++) {
    out.push({ key: 'l-' + i, text: L[i].text, time: L[i].time, hit: L[i].time })
    const next = L[i + 1]
    if (next && next.time - L[i].time > INTER_GAP) {
      out.push({ key: 'i-' + i, interlude: true, text: '间奏', hit: L[i].time + 8 })
    }
  }
  return out
})

const activeIdx = computed(() => {
  const t = player.currentTime
  let idx = -1
  for (let i = 0; i < displayLines.value.length; i++) {
    if (displayLines.value[i].hit <= t) idx = i
    else break
  }
  return idx
})

/* ---------- 自动滚动居中：用户手动滚动后暂停 2.8s 再接管 ---------- */
let userScrollUntil = 0
function onUserScroll() { userScrollUntil = Date.now() + 2800 }

function scrollToActive(smooth) {
  if (Date.now() < userScrollUntil) return
  const el = lineEls[activeIdx.value]
  const body = bodyEl.value
  if (el && body) {
    const top = el.offsetTop - body.clientHeight / 2 + el.clientHeight / 2
    body.scrollTo({ top: Math.max(0, top), behavior: smooth ? 'smooth' : 'auto' })
  }
}

watch(activeIdx, () => scrollToActive(true))
watch(() => lyrics.panelOpen, async (open) => {
  if (!open) return
  await nextTick()
  scrollToActive(false)
  // 打开时还没有歌词则重查一次：歌词可能在面板关闭期间获取完成，
  // lyricUrl 是后来才回写的，换歌 watcher 当时已经跑过、不会再触发
  if (!lyrics.hasLyrics && !lyrics.loading && player.current) {
    lyrics.loadForSong(player.current)
  }
})

/* ---------- 点击歌词行跳转播放进度 ---------- */
function seekTo(l) {
  if (l.interlude || typeof l.time !== 'number') return
  player.seek(l.time + 0.05)
}

function fmtTs(t) {
  if (typeof t !== 'number') return ''
  const s = Math.max(0, Math.floor(t))
  return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0')
}

/* ---------- 换歌加载歌词（面板开合状态不变） ---------- */
watch(() => player.current && player.current.id, async (id, old) => {
  if (id !== old) {
    coverFail.value = false
    lineEls.length = 0
    await lyrics.loadForSong(player.current)
  }
}, { immediate: true })
watch(() => player.current && player.current.lyricUrl, (u, old) => {
  lyrics.setCurrentSong(player.current)
  // 歌词地址后到（换歌时播放接口尚未返回、稍后才回填 lyricUrl）：
  // 必须触发加载，否则面板停在"无歌词"，又要手动一键获取（bug20）
  if (u && u !== old && !lyrics.hasLyrics && !lyrics.loading) {
    lyrics.loadForSong(player.current)
  }
})
</script>

<style scoped>
/* ============================================================
   页面内嵌歌词栏：内容区右侧的常驻列（非弹窗）。
   打开时以宽度动画挤入，页面内容自然让位；
   结构 = 头部（封面/曲目/来源/重取） + 获取进度 + 歌词滚动带
   ============================================================ */
.lyrics-pane {
  width: 402px;
  flex-shrink: 0;
  overflow: hidden;
  position: relative;
}
.pane-inner {
  width: 402px;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: rgba(255, 253, 248, 0.62);
  backdrop-filter: blur(22px) saturate(1.12);
  -webkit-backdrop-filter: blur(22px) saturate(1.12);
  border-left: 1px solid rgba(92, 72, 50, 0.12);
}
/* 展开收起：宽度动画（内容定宽防挤压回流） */
.pane-enter-active { transition: width 0.42s cubic-bezier(0.25, 0.9, 0.3, 1), opacity 0.3s ease 0.08s; }
.pane-leave-active { transition: width 0.3s cubic-bezier(0.5, 0, 0.75, 0.4), opacity 0.18s ease; }
.pane-enter-from, .pane-leave-to { width: 0; opacity: 0; }

/* ---------- 头部 ---------- */
.pane-head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 18px 15px;
  border-bottom: 1px solid rgba(92, 72, 50, 0.08);
  flex-shrink: 0;
}
.pane-head .cv {
  width: 62px;
  height: 62px;
  border-radius: 14px;
  overflow: hidden;
  flex-shrink: 0;
  position: relative;
  background: var(--surface-2, #f2ede4);
  border: 1px solid var(--border, rgba(92, 72, 50, 0.12));
  box-shadow: 0 8px 20px rgba(50, 36, 20, 0.14);
}
.pane-head .cv img { width: 100%; height: 100%; object-fit: cover; display: block; }
.pane-head .cv .ph {
  width: 100%; height: 100%;
  display: grid; place-items: center;
  font-size: 22px; color: var(--accent, #ff6b1a);
}
.head-meta { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.song-name {
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.song-singer {
  font-size: 12px;
  color: var(--text-muted);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.badges { display: flex; gap: 6px; margin-top: 5px; }
.badge {
  font-size: 10.5px;
  padding: 1.5px 9px;
  border-radius: 999px;
  background: rgba(6, 153, 184, 0.1);
  color: var(--cyan, #0699b8);
  white-space: nowrap;
}
.badge.plain { background: rgba(255, 194, 75, 0.16); color: var(--warning, #e5a50a); }

.refresh {
  flex-shrink: 0;
  width: 32px; height: 32px;
  border-radius: 10px;
  color: var(--text-muted);
  display: grid; place-items: center;
  transition: all 0.2s ease;
}
.refresh:hover:not(:disabled) { background: rgba(92, 72, 50, 0.07); color: var(--text); }
.refresh:disabled { opacity: 0.4; cursor: not-allowed; }
.refresh.busy svg { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.close {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  color: var(--text-muted);
  display: grid;
  place-items: center;
  transition: all 0.2s ease;
}
.close:hover { background: rgba(92, 72, 50, 0.07); color: var(--text); }

/* ---------- 获取进度 ---------- */
.task { padding: 12px 20px 4px; flex-shrink: 0; }
.task-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  font-size: 12.5px;
  color: var(--text-muted);
}
.task-stage { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.task-pct { font-family: var(--font-mono); font-variant-numeric: tabular-nums; }
.task-bar {
  height: 4px;
  border-radius: 2px;
  background: rgba(92, 72, 50, 0.13);
  margin-top: 7px;
  overflow: hidden;
}
.task-bar i {
  display: block;
  height: 100%;
  border-radius: 2px;
  background: linear-gradient(90deg, var(--accent, #ff6b1a), var(--magenta, #e11d92));
  transition: width 0.5s ease;
}
.task-fail { font-size: 12px; color: var(--error); margin-top: 7px; }

/* ---------- 歌词滚动带 ---------- */
.pane-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  position: relative; /* offsetTop 基准 */
  padding: 20px 12px 45%;
  text-align: center;
  /* 上下渐隐遮罩：聚光当前句 */
  mask-image: linear-gradient(180deg, transparent 0, #000 6%, #000 94%, transparent 100%);
  -webkit-mask-image: linear-gradient(180deg, transparent 0, #000 6%, #000 94%, transparent 100%);
  scrollbar-width: thin;
}

/* 空状态 */
.empty {
  padding: 46px 26px;
  display: flex;
  flex-direction: column;
  align-items: center;
  color: var(--text-muted);
}
.empty-icon {
  width: 76px; height: 76px;
  border-radius: 50%;
  background: rgba(92, 72, 50, 0.06);
  border: 1px dashed rgba(92, 72, 50, 0.18);
  display: grid; place-items: center;
  color: var(--text-faint, #b0a89c);
  margin-bottom: 14px;
}
.empty-title { font-size: 14px; font-weight: 600; color: var(--text); }
.empty-sub {
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.8;
  margin: 8px 0 16px;
}
.empty-fetch { padding: 8px 20px; font-size: 13px; }

/* 纯文本歌词 */
.plain p { font-size: 13.5px; line-height: 2.1; color: var(--text-muted); }

/* ---------- 歌词行三态：已播（退场）/ 当前（聚光）/ 未播（待唱） ---------- */
.line {
  position: relative;
  padding: 6px 16px;
  margin: 3px 0;
  border-radius: 10px;
  font-size: 15.5px;
  line-height: 1.85;
  color: var(--text-muted);
  opacity: 0.58;                          /* 未播 */
  cursor: pointer;
  transform-origin: center;
  transition: opacity 0.35s ease, color 0.35s ease, transform 0.35s ease, background 0.2s ease;
}
.line.past { opacity: 0.3; }              /* 已播：明显退场 */

.line:not(.active):not(.inter):hover {
  background: rgba(92, 72, 50, 0.05);
  opacity: 0.85;
}
/* 悬停行首时间戳：快速定位的听觉学习入口 */
.line .ts {
  position: absolute;
  left: 2px;
  top: 50%;
  transform: translateY(-50%);
  font-family: var(--font-mono);
  font-size: 10.5px;
  color: var(--text-faint, #b0a89c);
  opacity: 0;
  transition: opacity 0.2s ease;
  pointer-events: none;
}
.line:hover .ts { opacity: 1; }

.line.active:not(.inter) {
  opacity: 1;
  font-weight: 700;
  transform: scale(1.045);
  background: linear-gradient(92deg, var(--accent, #ff6b1a), var(--magenta, #e11d92));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  filter: drop-shadow(0 2px 12px rgba(255, 107, 26, 0.25));
}

/* 间奏虚行：♪ 夹注 + 激活时音符浮动 */
.line.inter {
  font-size: 12.5px;
  letter-spacing: 6px;
  color: var(--text-faint, #b0a89c);
  opacity: 0.55;
  cursor: default;
  padding: 10px 16px;
}
.line.inter::before { content: "♪"; margin-right: 12px; font-size: 13px; }
.line.inter::after { content: "♪"; margin-left: 12px; font-size: 13px; }
.line.inter::before, .line.inter::after { display: inline-block; }
.line.inter.active {
  opacity: 0.95;
  color: var(--cyan, #0699b8);
  letter-spacing: 9px;
}
.line.inter.active::before { animation: noteFloat 1.7s ease-in-out infinite; }
.line.inter.active::after { animation: noteFloat 1.7s ease-in-out 0.85s infinite; }
@keyframes noteFloat {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-4px); }
}

/* 窄屏兜底：歌词栏改为右侧覆盖层，不再挤压内容 */
@media (max-width: 1080px) {
  .lyrics-pane {
    position: absolute;
    right: 0; top: 0; bottom: 0;
    z-index: 30;
    box-shadow: -12px 0 36px rgba(50, 36, 20, 0.16);
  }
  .pane-inner { background: rgba(255, 254, 250, 0.95); }
}
</style>
