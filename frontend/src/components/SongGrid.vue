<template>
  <div class="grid-root" :class="{ pager }">
    <button v-if="pager && page > 0" class="rail-btn l" aria-label="上一页" @click="page--">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
        <polyline points="15 18 9 12 15 6" />
      </svg>
    </button>

    <div class="cards" :class="{ rail: pager }" role="listbox" aria-label="歌曲列表" ref="gridEl"
      :style="pager ? { transform: `translateX(${-page * viewW}px)` } : undefined">
      <div v-for="(row, i) in songs" :key="row.id + '-' + i" class="card"
        :class="{ playing: isPlaying(row) }" :title="row.name" :style="cardStyle(row)"
        @click="$emit('play', i)" @dblclick="$emit('play', i)">
        <div class="cover" :class="row.coverUrl ? '' : ph(row)">
          <img v-if="row.coverUrl && !failed[keyOf(row, i)]" :src="resolveFileUrl(row.coverUrl)" alt=""
            @error="onImgError(row, i)">
          <span v-else class="ph" :class="ph(row)"></span>

          <!-- 悬停：中央播放钮（bug79：当前曲播放中应显示暂停态，而非一律播放三角） -->
          <span class="play-fab" aria-hidden="true">
            <svg v-if="isPlaying(row) && playerStore.playing" viewBox="0 0 24 24" fill="currentColor">
              <rect x="6" y="5" width="4" height="14" rx="1" /><rect x="14" y="5" width="4" height="14" rx="1" />
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="currentColor"><path d="M7 5v14l12-7z" /></svg>
          </span>

          <!-- 右上角操作 -->
          <span class="cover-ops" @click.stop>
            <button v-if="showCollect && userStore.isLogin" class="op-btn"
              :class="{ on: userStore.isCollected(row.id) }"
              :title="userStore.isCollected(row.id) ? '取消收藏' : '收藏'"
              @click.stop="collect(row)">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 21s-8-4.5-8-10a4.5 4.5 0 0 1 8-3 4.5 4.5 0 0 1 8 3c0 5.5-8 10-8 10z" />
              </svg>
            </button>
            <slot name="ops" :row="row" :index="i"></slot>
          </span>

          <!-- 播放中：青色跳动波形 -->
          <span v-if="isPlaying(row) && playerStore.playing" class="mini-echo" aria-hidden="true">
            <i></i><i></i><i></i>
          </span>
        </div>
        <b class="name">{{ row.name }}</b>
        <span class="sub">
          <span class="artist">{{ singerName(row) }}<template v-if="row.album"> · {{ row.album }}</template></span>
          <span class="dur">{{ fmtDuration(row.duration) }}</span>
        </span>
      </div>
    </div>

    <button v-if="pager && page < maxPage" class="rail-btn r" aria-label="下一页" @click="page++">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
        <polyline points="9 18 15 12 9 6" />
      </svg>
    </button>
  </div>
</template>

<script setup>
// WeUI 日光版「热门歌曲」卡片：主色渐变底 + 深墨字，1:1 封面在上，
// 挂载时 GSAP 错位淡入，播放中青色描边 + mini-echo。
// pager=true 时改为单行轨道：一次只显示一行，左右箭头整页翻（新卡从右侧顶入）。
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { usePlayerStore } from '../stores/player'
import { useUserStore } from '../stores/user'
import { resolveFileUrl, singerName, fmtDuration } from '../utils'
import { coverTint, placeholderTint } from '../utils/color'
import { revealStagger } from '../utils/motion'

const props = defineProps({
  songs: { type: Array, default: () => [] },
  showCollect: { type: Boolean, default: true },
  pager: { type: Boolean, default: false }
})
defineEmits(['play'])

const playerStore = usePlayerStore()
const userStore = useUserStore()
const gridEl = ref(null)
// 封面加载失败标记：key -> true，失败后显示渐变占位（避免 el-image 占位反复挂载导致的闪烁）
const failed = reactive({})
// 卡片配色：songId -> { bg, accent }，封面主色提取（无封面/加载失败用占位渐变派生）
const tints = reactive({})

function keyOf(row, i) {
  return row.id + '-' + i
}
function isPlaying(row) {
  return playerStore.current && playerStore.current.id === row.id
}
function ph(row) {
  return 'c' + ((Number(row.id || 0) % 6) + 1)
}
function cardStyle(row) {
  const t = tints[row.id]
  return t ? { background: t.bg, '--card-accent': t.accent } : undefined
}
function onImgError(row, i) {
  failed[keyOf(row, i)] = true
  if (!tints[row.id]) tints[row.id] = placeholderTint(row.id)
}
async function collect(row) {
  await userStore.toggleCollect(row.id)
}

// 曲目变化时补齐配色（有结果即缓存，结果不覆盖已有）
watch(() => props.songs, (list) => {
  list.forEach((row) => {
    if (tints[row.id] || row.id == null) return
    if (row.coverUrl) {
      coverTint(resolveFileUrl(row.coverUrl)).then((t) => { if (t) tints[row.id] = t })
    } else {
      tints[row.id] = placeholderTint(row.id)
    }
  })
}, { immediate: true })

/* ---------- 单行轨道翻页 ---------- */
const page = ref(0)
const viewW = ref(0)
const contentW = ref(0)
const maxPage = computed(() => Math.max(0, Math.ceil((contentW.value - viewW.value) / Math.max(1, viewW.value))))

function measure() {
  if (!props.pager || !gridEl.value) return
  viewW.value = gridEl.value.parentElement.clientWidth
  contentW.value = gridEl.value.scrollWidth
  if (page.value > maxPage.value) page.value = maxPage.value
}

let ro = null
onMounted(() => {
  measure()
  if (props.pager && window.ResizeObserver) {
    ro = new ResizeObserver(measure)
    ro.observe(gridEl.value.parentElement)
  }
})
onBeforeUnmount(() => { if (ro) ro.disconnect() })
watch(() => props.songs, () => { page.value = 0; nextTick(measure) })

function animateIn() {
  if (gridEl.value) revealStagger(gridEl.value.querySelectorAll('.card'), { y: 16, stagger: 0.04 })
}
onMounted(animateIn)
watch(() => props.songs, () => nextTick(animateIn))
</script>

<style scoped>
/* 普通模式：自适应多行网格；pager 模式：裁切视口 + 轨道平移（见 .rail） */
.grid-root {
  position: relative;
}
.grid-root.pager {
  overflow: hidden;
  /* 给悬停上浮/3D 倾斜留出裁切余量，不裁卡片本体 */
  padding: 5px 0 6px;
  margin: -5px 0 -6px;
}

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(168px, 1fr));
  gap: 16px;
}
.cards.rail {
  display: flex;
  flex-wrap: nowrap;
  transition: transform 0.5s cubic-bezier(0.2, 0.8, 0.3, 1);
  will-change: transform;
}
.cards.rail .card {
  flex: 0 0 168px;
}

/* 轨道翻页箭头：悬浮在行两缘，封面垂直中心处 */
.rail-btn {
  position: absolute;
  top: 92px;
  z-index: 6;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1px solid var(--border);
  background: rgba(255, 254, 250, 0.92);
  backdrop-filter: blur(6px);
  box-shadow: var(--shadow-md);
  color: var(--text);
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: transform 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}
.rail-btn svg { width: 16px; height: 16px; }
.rail-btn.l { left: 4px; }
.rail-btn.r { right: 4px; }
.rail-btn:hover {
  color: var(--accent);
  border-color: rgba(255, 107, 26, 0.5);
  transform: scale(1.08);
}

.card {
  display: flex;
  flex-direction: column;
  gap: 9px;
  padding: 10px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
  transform-style: preserve-3d;
  transition: transform 0.35s cubic-bezier(0.2, 0.8, 0.3, 1), border-color 0.2s ease, box-shadow 0.35s ease;
  cursor: pointer;
  min-width: 0;
}
/* 主色底：边线与悬停态向点缀色靠拢（未取到主色时保持原样） */
.card[style*="--card-accent"] {
  border-color: color-mix(in srgb, var(--card-accent) 22%, var(--border));
}
.card[style*="--card-accent"]:hover {
  border-color: color-mix(in srgb, var(--card-accent) 55%, transparent);
}
/* 悬停：透视倾斜 + 封面上浮，形成景深 */
.card:hover {
  transform: perspective(900px) rotateX(6deg) rotateY(-7deg) translateY(-4px);
  border-color: rgba(0, 0, 0, 0.14);
  box-shadow: var(--shadow-md);
}
.card:hover .cover {
  transform: translateZ(26px);
}

/* 封面：1:1 方形；主色卡让封面投一点同色系的软影 */
.cover {
  position: relative;
  aspect-ratio: 1 / 1;
  border-radius: 10px;
  overflow: hidden;
  background: var(--surface-2);
  transition: transform 0.35s cubic-bezier(0.2, 0.8, 0.3, 1);
}
.card[style*="--card-accent"] .cover {
  box-shadow: 0 10px 22px -12px var(--card-accent);
}
.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.cover .ph {
  position: absolute;
  inset: 0;
}
.cover.c1, .cover.c2, .cover.c3, .cover.c4, .cover.c5, .cover.c6 { color: transparent; }

/* 播放中 = 青色描边 */
.card.playing .cover {
  outline: 2px solid var(--cyan);
  outline-offset: 2px;
}
.card.playing .name {
  color: var(--cyan);
}

/* 悬停中央播放钮（品牌橙） */
.play-fab {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  opacity: 0;
  background: rgba(0, 0, 0, 0.28);
  transition: opacity 0.2s ease;
}
.play-fab svg {
  width: 40px;
  height: 40px;
  padding: 11px;
  box-sizing: border-box;
  border-radius: 50%;
  background: var(--accent);
  color: var(--accent-text);
  box-shadow: var(--glow-accent);
}
.card:hover .play-fab {
  opacity: 1;
}

/* 封面右上角操作 */
.cover-ops {
  position: absolute;
  top: 7px;
  right: 7px;
  display: flex;
  gap: 5px;
  opacity: 0;
  transition: opacity 0.15s ease;
}
/* 操作按钮只在悬停时出现（触屏设备无悬停则常显）；播放中不再常驻，保持封面干净 */
.card:hover .cover-ops {
  opacity: 1;
}
@media (hover: none) {
  .cover-ops { opacity: 1; }
}

.op-btn {
  width: 25px;
  height: 25px;
  border: none;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.9);
  color: rgba(0, 0, 0, 0.7);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
  transition: background 0.15s, color 0.15s;
}
.op-btn:hover {
  background: #fff;
  color: var(--accent);
}
.op-btn.on {
  color: var(--magenta);
}

/* 文字区：歌名 + 歌手/时长（主色底上仍是深墨字） */
.name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.sub {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
}
.artist {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.dur {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-faint);
  flex-shrink: 0;
}
/* 主色卡的时长用点缀色，呼应底色 */
.card[style*="--card-accent"] .dur {
  color: var(--card-accent);
}

/* 播放中：封面左下角 mini-echo */
.mini-echo {
  position: absolute;
  left: 8px;
  bottom: 8px;
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 14px;
  width: 16px;
  padding: 3px 4px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.85);
}
.mini-echo i {
  flex: 1;
  background: var(--cyan);
  border-radius: 1px;
  animation: sg-echo 1s ease-in-out infinite;
}
.mini-echo i:nth-child(1) { height: 60%; }
.mini-echo i:nth-child(2) { height: 100%; animation-delay: 0.2s; }
.mini-echo i:nth-child(3) { height: 40%; animation-delay: 0.35s; }
@keyframes sg-echo {
  0%, 100% { transform: scaleY(0.4); }
  50% { transform: scaleY(1); }
}
</style>
