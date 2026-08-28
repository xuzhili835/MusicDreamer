<template>
  <el-dialog v-model="visible" title="听歌识曲" width="460px" :close-on-click-modal="false" append-to-body
    @closed="reset">
    <!-- 待机：选择识别方式 -->
    <template v-if="phase === 'idle'">
      <div class="rec-modes">
        <div v-for="m in MODES" :key="m.key" class="rec-mode" :class="{ on: mode === m.key }"
          @click="mode = m.key">
          <!-- bug60：模式图标统一为线性 SVG，替换 emoji（各平台渲染大小、风格不一致） -->
          <span class="rec-mode-icon" v-html="m.icon"></span>
          <span>{{ m.label }}</span>
        </div>
      </div>
      <div class="rec-desc">{{ MODE_DESC[mode] }}</div>
      <!-- bug61：勾选共享音频是最常见的失败点，系统声音模式给出分步指引。
           bug61b：两种声音来源要分开教——网页里的歌走标签页；后台软件只能走整个屏幕+系统音频 -->
      <div v-if="mode === 'tab'" class="rec-steps">
        <span>① 网页里放的歌：切到「标签页」，选中播放页并勾选「同时共享标签页音频」</span>
        <span>② 后台软件/播放器放的歌：切到「整个屏幕」，勾选「同时也共享系统音频」（需 Win10 及较新浏览器）</span>
        <span>③ 单个「窗口」模式拿不到任何声音（浏览器限制），不要选它</span>
      </div>
      <div v-if="mode !== 'file'" class="rec-act">
        <el-button type="danger" round size="large" class="rec-start" @click="start">● 开始识曲</el-button>
        <div class="rec-file-tip">持续聆听、识别到自动停止（最长约 30 秒）</div>
      </div>
      <div v-else class="rec-act">
        <input ref="fileInput" type="file" accept="audio/*,video/*" hidden @change="onFile" />
        <el-button type="primary" round size="large" @click="$refs.fileInput.click()">选择音频文件</el-button>
        <div class="rec-file-tip">支持 mp3 / m4a / webm / mp4 等，取前 15 秒参与识别</div>
      </div>
      <el-alert v-if="!canCapture && mode !== 'file'" type="warning" :closable="false" style="margin-top: 14px"
        title="当前页面不是安全上下文（需 https 或 localhost），浏览器禁止录音" />
    </template>

    <!-- 持续聆听中（系统声音/麦克风）：递增窗口多轮探测，命中即停 -->
    <div v-else-if="phase === 'listening'" class="rec-center">
      <div class="rec-pulse"></div>
      <div class="rec-sec">{{ elapsed }}<span class="rec-sec-unit">s</span></div>
      <div class="rec-hint">{{ best ? `疑似《${best.song?.name || '?'}》，继续确认中…` : '正在聆听，识别到会自动停止…' }}</div>
      <el-button round @click="manualStop">停止识别</el-button>
    </div>

    <!-- 上传比对中（本地文件） -->
    <div v-else-if="phase === 'uploading'" class="rec-center">
      <div class="rec-sec">♪</div>
      <div class="rec-hint">正在比对曲库指纹…</div>
    </div>

    <!-- 结果 -->
    <div v-else-if="phase === 'result' && result">
      <template v-if="result.matched">
        <div class="rec-song">
          <img v-if="result.song && result.song.coverUrl" class="rec-cover" :src="coverSrc" alt=""
            referrerpolicy="no-referrer" />
          <div v-else class="rec-cover rec-cover-none">♪</div>
          <div class="rec-song-info">
            <div class="rec-song-name">{{ result.song?.name || '未知歌曲' }}</div>
            <div class="rec-song-meta">
              <span>{{ result.song?.singerName || '未知歌手' }}</span>
            </div>
            <div class="rec-song-pos">
              <el-tag size="small" :type="result.level === 'HIT' ? 'success' : 'warning'">
                {{ result.level === 'HIT' ? '识别成功' : '可能是这首' }}
              </el-tag>
              <span v-if="result.via === 'external'">外置识别 · 已匹配本地曲库</span>
              <span v-else-if="result.offsetSec > 0">片段约在 {{ Math.round(result.offsetSec) }} 秒处</span>
              <span v-if="result.confidence != null">置信度 {{ result.confidence }}%</span>
            </div>
          </div>
        </div>
        <div class="rec-act">
          <el-button type="primary" @click="play">立即播放</el-button>
          <el-button @click="reset">再试一次</el-button>
        </div>
      </template>
      <template v-else>
        <template v-if="result.external">
          <div class="rec-miss">
            <div class="rec-miss-icon">
              <svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
              </svg>
            </div>
            <div class="rec-miss-title">这好像是《{{ result.external.title }}》</div>
            <div class="rec-hint">
              {{ result.external.artist || '未知歌手' }}<template v-if="result.external.album"> · 专辑 {{ result.external.album }}</template>
              ，本地曲库还没有这首歌
            </div>
          </div>
          <div class="rec-act">
            <el-button type="primary" @click="gotoRequest(result.external)">求歌入库（已填歌名）</el-button>
            <el-button @click="reset">再试一次</el-button>
          </div>
        </template>
        <template v-else>
          <div class="rec-miss">
            <div class="rec-miss-icon">
              <svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10" />
                <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
                <line x1="12" y1="17" x2="12.01" y2="17" />
              </svg>
            </div>
            <div class="rec-miss-title">没有认出这段音乐</div>
            <div class="rec-hint">可能是曲库里还没有这首歌，或者环境噪音太大</div>
          </div>
          <div class="rec-act">
            <el-button type="primary" @click="gotoRequest()">去求歌</el-button>
            <el-button @click="reset">再试一次</el-button>
          </div>
        </template>
      </template>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { usePlayerStore } from '../stores/player'
import { resolveFileUrl } from '../utils'

// 递增探测窗口（秒）：数字直采 3 秒通常足够命中；嘈杂场景由后续更长的窗口兜住。
// 每轮都是完整独立片段，后端阈值按 ≤10 秒窗口标定，无需按长度折算。
const ROUNDS = [3, 6, 10, 10]

const MODES = [
  { key: 'tab', label: '系统声音', icon: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="14" rx="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>' },
  { key: 'mic', label: '麦克风', icon: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/><path d="M19 10v2a7 7 0 0 1-14 0v-2"/><line x1="12" y1="19" x2="12" y2="23"/></svg>' },
  { key: 'file', label: '本地文件', icon: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>' }
]
const MODE_DESC = {
  tab: '听电脑正在播放的声音：网页里的歌选「标签页」，后台软件的声音选「整个屏幕」（见下方指引）',
  mic: '用麦克风听环境里的音乐（外放音箱场景）',
  file: '直接上传一小段音频文件识别'
}

const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const player = usePlayerStore()
const phase = ref('idle')       // idle -> listening(多轮) -> result；文件走 uploading
const mode = ref('tab')
const elapsed = ref(0)
const result = ref(null)
const fileInput = ref(null)
const canCapture = typeof window !== 'undefined' && window.isSecureContext

let sourceStream = null        // 采集源（display/mic），全程持有，结束释放
let recStream = null           // audio-only，跨轮复用给每一段 MediaRecorder
let recorder = null
let roundTimer = null
let tickTimer = null
let flashTimer = null
let aborted = false            // 弹窗关闭/重置：丢弃一切在途结果
let matched = false            // 已命中：停止后续轮次
let manualStopping = false     // 用户点了「停止识别」：本轮结束后收尾
let finished = false
const best = ref(null)         // 轮次中出现过的最好 LIKELY，兜底展示

const coverSrc = computed(() => resolveFileUrl(result.value?.song?.coverUrl))

function stopDevices() {
  if (roundTimer) { clearTimeout(roundTimer); roundTimer = null }
  if (recorder && recorder.state !== 'inactive') {
    try { recorder.stop() } catch (e) { /* 忽略：随流释放 */ }
  }
  recorder = null
  if (sourceStream) { sourceStream.getTracks().forEach((t) => t.stop()); sourceStream = null }
  recStream = null
}

function reset() {
  aborted = true
  stopDevices()
  if (tickTimer) { clearInterval(tickTimer); tickTimer = null }
  stopTitleFlash()
  phase.value = 'idle'
  result.value = null
  best.value = null
  elapsed.value = 0
}

async function start() {
  if (!canCapture) return
  let stream
  try {
    stream = mode.value === 'tab'
      ? await navigator.mediaDevices.getDisplayMedia({
          video: true,
          audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: false },
          // 整屏分享时把「共享系统音频」预勾上（浏览器不支持时忽略此项，不报错）
          systemAudio: 'include'
        })
      : await navigator.mediaDevices.getUserMedia({
          audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: false }
        })
  } catch (e) {
    if (e && e.name === 'NotAllowedError') return   // 用户取消共享/拒绝授权
    ElMessage.error(e && e.name === 'NotFoundError' ? '没有可用的音频采集设备' : '无法开始录音：' + (e.message || e))
    return
  }
  // 共享了画面但没带出音频：识曲无意义，按所选分享面给定向提示后引导重选
  if (stream.getAudioTracks().length === 0) {
    const surface = stream.getVideoTracks()[0]?.getSettings?.()?.displaySurface  // 先读再停流
    stream.getTracks().forEach((t) => t.stop())
    ElMessage.warning(
      surface === 'window'
        ? '单个窗口拿不到声音（浏览器限制）：识别后台软件请改选「整个屏幕」并勾选系统音频，网页里的歌用标签页最稳'
        : '这次共享没带出音频：若选了整个屏幕，请勾选「同时也共享系统音频」；找不到该选项说明浏览器版本偏旧，建议用标签页方式'
    )
    return
  }
  sourceStream = stream
  recStream = new MediaStream(stream.getAudioTracks())
  askNotifyPermission()
  aborted = false
  matched = false
  manualStopping = false
  finished = false
  best.value = null
  elapsed.value = 0
  phase.value = 'listening'
  tickTimer = setInterval(() => { elapsed.value++ }, 1000)
  runRounds()
}

/** 录一段固定时长的完整片段；被中断/手动停止也照常 resolve（blob 可能为空）。 */
function recordClip(ms) {
  return new Promise((resolve) => {
    const chunks = []
    const mime = window.MediaRecorder && MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
      ? 'audio/webm;codecs=opus' : ''
    let r
    try {
      r = new MediaRecorder(recStream, mime ? { mimeType: mime } : undefined)
    } catch (e) {
      resolve(null)
      return
    }
    r.ondataavailable = (e) => { if (e.data && e.data.size) chunks.push(e.data) }
    r.onstop = () => resolve({ blob: new Blob(chunks, { type: r.mimeType || 'audio/webm' }) })
    r.start(500)
    recorder = r
    roundTimer = setTimeout(() => { try { r.stop() } catch (e) { resolve(null) } }, ms)
  })
}

/** 边录边认：第 N 轮探测发出后不等结果，立刻开录第 N+1 轮；最后/手动轮带 last 标记。 */
async function runRounds() {
  for (let i = 0; i < ROUNDS.length; i++) {
    if (aborted || matched || (manualStopping && !recorder)) { finish(); return }
    const clip = await recordClip(ROUNDS[i] * 1000)
    if (aborted) return
    if (!clip || !clip.blob.size) continue
    const last = i === ROUNDS.length - 1
    const final = last || manualStopping
    const p = probe(clip.blob, final)
    if (final) {
      await p            // 最后一轮/手动停止：等结果（含外置识别）收尾
      finish()
      return
    }
  }
  finish()
}

/** 单轮识别：HIT 立即收尾；LIKELY 记为候选继续听；MISS/异常忽略。
 *  只有 last 轮的 MISS 才会触发后端外置识别（ACRCloud），省额度。 */
async function probe(blob, isFinal) {
  try {
    const r = await api.recognize(blob, mode.value, isFinal)
    if (aborted || matched || !r) return r
    if (r.matched) {
      if (r.level === 'HIT') {
        matched = true
        stopDevices()
        finish(r)
      } else if (!best.value) {
        best.value = r
      }
    }
    return r
  } catch (e) {
    return null
  }
}

function finish(finalResult) {
  if (finished) return
  finished = true
  stopDevices()
  if (tickTimer) { clearInterval(tickTimer); tickTimer = null }
  result.value = finalResult || best.value || { matched: false }
  phase.value = 'result'
  if (finalResult && typeof document !== 'undefined' && document.hidden) notifyHit(finalResult)
}

function manualStop() {
  manualStopping = true
  if (recorder && recorder.state !== 'inactive') {
    if (roundTimer) { clearTimeout(roundTimer); roundTimer = null }
    try { recorder.stop() } catch (e) { /* onstop 会走收尾 */ }
  }
}

function onFile(e) {
  const f = e.target.files && e.target.files[0]
  e.target.value = ''
  if (!f) return
  if (f.size > 20 * 1024 * 1024) return ElMessage.warning('文件超过 20MB，请截取片段')
  phase.value = 'uploading'
  upload(f, 'file')
}

async function upload(file, sourceOverride) {
  try {
    result.value = await api.recognize(file, sourceOverride || mode.value, true)
    phase.value = 'result'
  } catch (e) {
    reset()
  }
}

function play() {
  if (result.value && result.value.song) {
    player.playSong(result.value.song)
    visible.value = false
  }
}

/** 外置识别认出了歌名（本地没有）时带 detail 预填求歌表单。 */
function gotoRequest(ext) {
  visible.value = false
  const detail = ext && ext.title
    ? { title: ext.title, artist: ext.artist || '', source: 1 }
    : undefined
  window.dispatchEvent(detail
    ? new CustomEvent('md-open-song-request', { detail })
    : new CustomEvent('md-open-song-request'))
}

// ---------- 切走页面时命中：浏览器通知 + 标签页标题闪烁 ----------

function askNotifyPermission() {
  try {
    if ('Notification' in window && Notification.permission === 'default') {
      Notification.requestPermission()
    }
  } catch (e) { /* 无通知环境忽略 */ }
}

function notifyHit(r) {
  const body = `《${r.song?.name || ''}》${r.song?.singerName || ''}`.trim()
  try {
    if ('Notification' in window && Notification.permission === 'granted') {
      new Notification('识别成功', { body })
    }
  } catch (e) { /* 忽略 */ }
  const orig = document.title
  let on = false
  flashTimer = setInterval(() => {
    document.title = on ? orig : '♪ 识别成功：' + body
    on = !on
  }, 800)
  const stopFlash = () => {
    if (flashTimer) { clearInterval(flashTimer); flashTimer = null }
    document.title = orig
    window.removeEventListener('visibilitychange', stopFlash)
  }
  window.addEventListener('visibilitychange', stopFlash)
}

function stopTitleFlash() {
  // 标题闪烁由 visibilitychange 自清理；弹窗关闭兜底再清一次
  if (flashTimer) { clearInterval(flashTimer); flashTimer = null }
}

onBeforeUnmount(() => reset())
</script>

<style scoped>
.rec-modes {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}
.rec-mode {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 14px 4px;
  border: 1px solid var(--border, rgba(127, 127, 127, 0.25));
  border-radius: 10px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-muted, #8a8378);
  transition: all 0.15s;
}
.rec-mode:hover { border-color: var(--accent, #ff6b1a); }
.rec-mode.on {
  border-color: var(--accent, #ff6b1a);
  color: var(--text, #eee);
  background: color-mix(in srgb, var(--accent, #ff6b1a) 12%, transparent);
}
.rec-mode-icon { display: flex; font-size: 20px; }
.rec-desc {
  font-size: 12.5px;
  color: var(--text-faint, #b0a89c);
  line-height: 1.6;
  margin-bottom: 16px;
}
/* bug61：系统声音模式的共享音频分步指引 */
.rec-steps {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin: -8px 0 14px;
  padding: 10px 12px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--accent, #ff6b1a) 6%, transparent);
  font-size: 12px;
  color: var(--text-muted, #8a8378);
}
.rec-act {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}
.rec-start {
  width: 200px;
  font-size: 14px;
  letter-spacing: 1px;
}
.rec-file-tip { font-size: 12px; color: var(--text-faint, #b0a89c); }
.rec-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 22px 0 10px;
}
.rec-pulse {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #f56c6c;
  animation: rec-pulse 1s ease-in-out infinite;
}
@keyframes rec-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(245, 108, 108, 0.5); }
  50% { box-shadow: 0 0 0 12px rgba(245, 108, 108, 0); }
}
.rec-sec {
  font-size: 42px;
  font-weight: 600;
  line-height: 1;
}
.rec-sec-unit { font-size: 16px; margin-left: 2px; }
.rec-hint { font-size: 13px; color: var(--text-muted, #8a8378); text-align: center; }
.rec-song {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 8px 4px 4px;
}
.rec-cover {
  width: 86px;
  height: 86px;
  border-radius: 10px;
  object-fit: cover;
  flex-shrink: 0;
  background: var(--bg-soft, rgba(127, 127, 127, 0.12));
}
.rec-cover-none {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30px;
  color: var(--text-faint, #b0a89c);
}
.rec-song-info { min-width: 0; }
.rec-song-name {
  font-size: 17px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rec-song-meta {
  font-size: 13px;
  color: var(--text-muted, #8a8378);
  margin: 4px 0 8px;
}
.rec-song-pos {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-faint, #b0a89c);
  flex-wrap: wrap;
}
.rec-miss {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 18px 0 10px;
}
.rec-miss-icon { font-size: 38px; color: var(--text-faint, #b0a89c); }
.rec-miss-title { font-size: 15px; font-weight: 600; }
</style>
