<template>
  <transition name="dl-fade">
    <div v-if="visible" class="desktop-lyrics" :style="{ left: posX + 'px' }" @dblclick="$emit('close')">
      <div class="dl-line" :class="{ dim: !currentLine }">{{ currentLine || (loadingLyric ? '歌词加载中…' : '暂无歌词') }}</div>
      <div class="dl-next">{{ nextLine || '' }}</div>
      <button class="dl-close" title="关闭桌面歌词（双击歌词也可关闭）" @click.stop="$emit('close')">×</button>
      <div class="dl-grip" title="拖动调整位置" @pointerdown="onGripDown" @pointermove="onGripMove"
        @pointerup="onGripUp">⋮⋮</div>
    </div>
  </transition>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import api from '../api'
import { usePlayerStore } from '../stores/player'
import { resolveFileUrl } from '../utils'

defineEmits(['close'])

const player = usePlayerStore()
const visible = ref(true)
const lines = ref([]) // [{ t: 秒, text }]
const loadingLyric = ref(false)
const posX = ref(Math.round(window.innerWidth / 2 - 260))

const currentLine = computed(() => {
  if (!lines.value.length) return ''
  const t = player.currentTime || 0
  let idx = -1
  for (let i = 0; i < lines.value.length; i++) {
    if (lines.value[i].t <= t) idx = i
    else break
  }
  return idx >= 0 ? lines.value[idx].text : ''
})

const nextLine = computed(() => {
  if (!lines.value.length) return ''
  const t = player.currentTime || 0
  for (let i = 0; i < lines.value.length; i++) {
    if (lines.value[i].t > t) return lines.value[i].text
  }
  return ''
})

watch(
  () => player.current && player.current.id,
  async (songId) => {
    lines.value = []
    if (!songId) return
    loadingLyric.value = true
    try {
      const detail = await api.songDetail(songId)
      const lyricUrl = detail && (detail.lyricUrl || detail.lyric)
      if (!lyricUrl) return
      const res = await fetch(resolveFileUrl(lyricUrl))
      if (!res.ok) return
      const text = await res.text()
      lines.value = parseLrc(text)
    } catch (e) {
      /* 歌词加载失败静默 */
    } finally {
      loadingLyric.value = false
    }
  },
  { immediate: true }
)

function parseLrc(text) {
  const out = []
  const re = /\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?\]/g
  for (const raw of text.split(/\r?\n/)) {
    let m
    const stamps = []
    re.lastIndex = 0
    while ((m = re.exec(raw))) {
      stamps.push(Number(m[1]) * 60 + Number(m[2]) + Number('0.' + (m[3] || '0')))
    }
    const content = raw.replace(re, '').trim()
    if (!content) continue
    for (const t of stamps) out.push({ t, text: content })
  }
  // 无时间标签的纯文本：整段展示
  if (!out.length) {
    const plain = text.trim()
    if (plain) out.push({ t: 0, text: plain.split(/\r?\n/)[0] })
  }
  return out.sort((a, b) => a.t - b.t)
}

let dragging = false
function onGripDown(e) {
  dragging = true
  e.target.setPointerCapture && e.target.setPointerCapture(e.pointerId)
}
function onGripMove(e) {
  if (!dragging) return
  const half = 260
  posX.value = Math.max(8, Math.min(window.innerWidth - half * 2 - 8, e.clientX - half))
}
function onGripUp() {
  dragging = false
}
</script>

<style scoped>
.desktop-lyrics {
  position: fixed;
  bottom: 96px;
  width: 520px;
  padding: 10px 34px 10px 16px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: var(--shadow-lg);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  z-index: 900;
  user-select: none;
  cursor: default;
}
.dl-line {
  font-size: 17px;
  font-weight: 700;
  color: var(--text);
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.dl-line.dim {
  color: var(--text-faint);
  font-weight: 500;
}
.dl-next {
  font-size: 12px;
  color: var(--text-muted);
  text-align: center;
  margin-top: 3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-height: 15px;
}
.dl-close {
  position: absolute;
  top: 4px;
  right: 6px;
  border: none;
  background: transparent;
  color: var(--text-faint);
  font-size: 15px;
  cursor: pointer;
  line-height: 1;
  padding: 3px;
}
.dl-close:hover {
  color: var(--error);
}
.dl-grip {
  position: absolute;
  top: 4px;
  left: 8px;
  color: var(--text-faint);
  font-size: 11px;
  letter-spacing: -1px;
  cursor: grab;
}
.dl-fade-enter-active,
.dl-fade-leave-active {
  transition: opacity 0.2s;
}
.dl-fade-enter-from,
.dl-fade-leave-to {
  opacity: 0;
}
</style>
