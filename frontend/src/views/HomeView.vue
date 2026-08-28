<template>
  <div class="home-wrap">
    <template v-if="heroSongs.length">
      <!-- hero：左侧曲目信息 + 右侧封面轮播（preview-v2） -->
      <section class="hero" ref="heroEl">
        <div class="hero-meta">
          <div class="eyebrow">Featured · 今日精选</div>
          <h1>{{ focusSong.name }}</h1>
          <p class="artist">
            {{ singerName(focusSong) }}
            <template v-if="focusSong.playCount != null">· {{ focusSong.playCount }} 次播放</template>
          </p>
          <div class="actions">
            <button class="btn-solid" @click="play(focusIdx)">
              <svg viewBox="0 0 24 24" fill="currentColor"><path d="M7 5v14l12-7z" /></svg>
              立即播放
            </button>
            <button class="btn-ghost" :class="{ on: userStore.isCollected(focusSong.id) }"
              :title="userStore.isCollected(focusSong.id) ? '取消收藏' : '收藏'" @click="toggleCollect(focusSong)">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 21s-8-4.5-8-10a4.5 4.5 0 0 1 8-3 4.5 4.5 0 0 1 8 3c0 5.5-8 10-8 10z" />
              </svg>
              {{ userStore.isCollected(focusSong.id) ? '已收藏' : '收藏' }}
            </button>
          </div>
          <!-- 签名式实时频谱：Web Audio Analyser 驱动，随正在播放的真实音频跳动 -->
          <canvas class="spectrum" ref="spectrumEl" aria-hidden="true"></canvas>
        </div>

        <!-- coverflow：全部候选曲渲染，按与焦点的相对位置定位；切换时 CSS 过渡 -->
        <div class="carousel" ref="carouselEl">
          <figure v-for="(s, idx) in heroSongs" :key="s.id" class="cover" :class="coverClass(idx)"
            :title="s.name" @click="onCoverClick(idx)">
            <img v-if="s.coverUrl && heroFailed.indexOf(s.id) < 0" :src="resolveFileUrl(s.coverUrl)" alt=""
              @error="heroFailed.push(s.id)">
            <div v-else class="art-ph" :class="'c' + ((Number(s.id || 0) % 6) + 1)"></div>
            <figcaption>
              <b>{{ s.name }}</b>
              <span>{{ singerName(s) }}</span>
            </figcaption>
          </figure>
        </div>
      </section>

      <!-- 分类胶囊（preview-v2 chips） -->
      <section class="section">
        <div class="section-head">
          <h2>选择分类</h2>
        </div>
        <div class="chips" role="group" aria-label="分类">
          <button v-for="t in tabs" :key="t.key" class="chip" :aria-pressed="String(tab === t.key)"
            @click="switchTab(t.key)">
            {{ t.label }}
          </button>
        </div>
        <!-- 曲风细分：在榜单/推荐之下再按歌曲类型筛选 -->
        <div class="chips sub" role="group" aria-label="曲风">
          <button class="chip" :class="{ ghost: true }" :aria-pressed="String(!style)" @click="selectStyle('')">全部曲风</button>
          <button v-for="item in STYLES" :key="item" class="chip ghost" :aria-pressed="String(style === item)"
            @click="selectStyle(item)">
            {{ item }}
          </button>
        </div>
      </section>
    </template>

    <!-- 歌曲网格（preview-v2 popular songs） -->
    <section class="section">
      <div class="section-head">
        <h2>{{ currentTitle }}</h2>
        <router-link to="/songs" class="more-link">所有歌曲</router-link>
      </div>

      <!-- 单行轨道：一次只显示一行，箭头整页翻（新卡从右侧顶入），首页保持一屏 -->
      <SongGrid :songs="songs" pager @play="play">
        <template #ops="{ row }">
          <span class="menu-wrap">
            <button class="hv-op" title="加入歌单" @click.stop="menuSong = menuSong === row.id ? null : row.id">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
              </svg>
            </button>
            <div v-if="menuSong === row.id" class="menu-pop">
              <div v-for="p in myPlaylists" :key="p.id" class="menu-item"
                @click="addToPlaylist(p.id, row.id)">{{ p.name }}</div>
              <div v-if="!myPlaylists.length" class="menu-item muted">先去创建歌单</div>
            </div>
          </span>
          <button class="hv-op" title="举报"
            @click.stop="reportTarget = { targetType: 1, targetId: row.id, label: row.name }">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
              <line x1="12" y1="9" x2="12" y2="13" /><line x1="12" y1="17" x2="12.01" y2="17" />
            </svg>
          </button>
          <button v-if="isAdmin" class="hv-op" title="编辑歌曲信息" @click.stop="openEdit(row)">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
            </svg>
          </button>
        </template>
      </SongGrid>

      <!-- 空状态 -->
      <div v-if="!songs.length && !loading" class="empty-state">
        <div class="empty-icon">
          <svg width="64" height="64" viewBox="0 0 24 24" fill="none">
            <rect x="3" y="10" width="3" height="9" rx="1.5" fill="var(--accent)" opacity="0.85" />
            <rect x="8" y="5" width="3" height="18" rx="1.5" fill="var(--accent)" opacity="0.85" />
            <rect x="13" y="8" width="3" height="12" rx="1.5" fill="var(--accent)" opacity="0.85" />
            <rect x="18" y="3" width="3" height="20" rx="1.5" fill="var(--accent)" opacity="0.85" />
          </svg>
        </div>
        <h3>{{ userStore.isLogin ? '还没有内容' : '登录后效果更佳' }}</h3>
        <!-- bug73：联合筛选下，当前板块+曲风可能组合出空集——给出解法而不是干瞪眼 -->
        <p v-if="style">「{{ currentTabLabel }} + {{ style }}」下暂时没有歌曲，试试放宽曲风</p>
        <p v-else>听歌、收藏、创建歌单，或成为歌手发布你的作品</p>
        <div class="empty-slogan">♪ 悦享音乐，让每一首歌都被听见 ♪</div>
        <div class="empty-actions">
          <button v-if="!userStore.isLogin" class="btn btn-primary" @click="$router.push('/login')">登录</button>
          <button class="btn btn-secondary" @click="$router.push('/songs')">浏览歌曲</button>
        </div>
      </div>
    </section>

    <div class="spacer"></div>

    <ReportDialog v-model="reportVisible" :target="reportTarget" />
    <SongEditDialog v-model="editVisible" :song="editSong" @saved="loadTab" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import api from '../api'
import { useUserStore } from '../stores/user'
import { usePlayerStore, getAudioEl } from '../stores/player'
import { listOf, resolveFileUrl, singerName, STYLES } from '../utils'
import { revealStagger, prefersReduced } from '../utils/motion'
import { getAnalyser } from '../utils/audioAnalyser'
import ReportDialog from '../components/ReportDialog.vue'
import SongEditDialog from '../components/SongEditDialog.vue'
import SongGrid from '../components/SongGrid.vue'

const userStore = useUserStore()
const playerStore = usePlayerStore()
const tabs = [
  { key: 'reco', label: '为你推荐' },
  { key: 'hot', label: '热歌榜' },
  { key: 'rise', label: '飙升榜' }
]
const tab = ref('reco')
const style = ref('')
const songs = ref([])
const loading = ref(false)
const myPlaylists = ref([])
const reportVisible = ref(false)
const reportTarget = ref(null)
const menuSong = ref(null)
const editVisible = ref(false)
const editSong = ref(null)

/* ---------- 焦点轮播（coverflow） ---------- */
const HERO_MAX = 6
const heroSongs = computed(() => songs.value.slice(0, HERO_MAX))
const focusIdx = ref(0)
const heroFailed = ref([]) // 封面加载失败的曲目 id
const focusSong = computed(() => heroSongs.value[focusIdx.value] || heroSongs.value[0])

// 相对焦点的最短有向距离：0 焦点 / -1 左 / +1 右 / 其余隐藏在两侧
function posOf(idx) {
  const n = heroSongs.value.length
  if (!n) return 0
  let pos = (idx - focusIdx.value + n) % n
  if (pos > n / 2) pos -= n
  return pos
}
function coverClass(idx) {
  const pos = posOf(idx)
  if (pos === 0) return 'focus'
  if (pos === -1) return 'side l'
  if (pos === 1) return 'side r'
  return 'far ' + (pos < 0 ? 'l' : 'r')
}
function onCoverClick(idx) {
  if (posOf(idx) === 0) play(focusIdx.value)
  else focusIdx.value = idx
}
const echoOn = computed(() => playerStore.playing)

// bug15：正在播放的歌若在轮播候选里，三张主封面同步聚焦到它
function syncFocusToPlaying() {
  const id = playerStore.current && playerStore.current.id
  if (id == null) return
  const idx = heroSongs.value.findIndex((s) => Number(s.id) === Number(id))
  if (idx >= 0) focusIdx.value = idx
}
watch(() => playerStore.current && playerStore.current.id, syncFocusToPlaying, { immediate: true })

watch(heroSongs, () => {
  focusIdx.value = 0
  // 换榜/切分类后列表重排，重新对焦到正在播放的歌（还在候选内时）
  syncFocusToPlaying()
})

/* ---------- GSAP 入场 + 签名实时频谱（Web Audio 真音频驱动） ---------- */
const heroEl = ref(null)
const carouselEl = ref(null)
const spectrumEl = ref(null)

const SPEC_BARS = 44
const specBands = new Float32Array(SPEC_BARS)
const specFreq = new Uint8Array(128)
let specAnalyser = null
let specRaf = 0

// 品牌三色：橙 → 洋红 → 青
const SPEC_STOPS = [[255, 107, 26], [225, 29, 146], [6, 153, 184]]
const SPEC_COLORS = Array.from({ length: SPEC_BARS }, (_, i) => {
  const t = (i / (SPEC_BARS - 1)) * 2
  const [a, b] = t < 1 ? [SPEC_STOPS[0], SPEC_STOPS[1]] : [SPEC_STOPS[1], SPEC_STOPS[2]]
  const k = t < 1 ? t : t - 1
  return `rgb(${Math.round(a[0] + (b[0] - a[0]) * k)},${Math.round(a[1] + (b[1] - a[1]) * k)},${Math.round(a[2] + (b[2] - a[2]) * k)})`
})

function drawSpectrum() {
  specRaf = 0
  // bug86 自愈：切页回来/组件重建后分析器可能为空，播放中随帧补挂
  if (playerStore.playing && !specAnalyser) specAnalyser = getAnalyser(getAudioEl())
  const c = spectrumEl.value
  let alive = false
  if (c) {
    const dpr = Math.min(2, window.devicePixelRatio || 1)
    const w = c.clientWidth
    const h = c.clientHeight
    if (w && h) {
      if (c.width !== Math.round(w * dpr) || c.height !== Math.round(h * dpr)) {
        c.width = Math.round(w * dpr)
        c.height = Math.round(h * dpr)
      }
      const g = c.getContext('2d')
      g.setTransform(dpr, 0, 0, dpr, 0, 0)
      g.clearRect(0, 0, w, h)
      if (specAnalyser && playerStore.playing) specAnalyser.getByteFrequencyData(specFreq)
      const gap = 3
      // 宽度瞬变期（歌词栏开合挤压）算出的 bw 可为负：钳到 1 防止
      // roundRect 收负半径抛 RangeError 打死 rAF 循环
      const bw = Math.max(1, (w - gap * (SPEC_BARS - 1)) / SPEC_BARS)
      for (let i = 0; i < SPEC_BARS; i++) {
        const t = i / (SPEC_BARS - 1)
        const bin = Math.min(127, Math.round(2 + Math.pow(t, 1.6) * 92))
        const v = specAnalyser && playerStore.playing ? (specFreq[bin] || 0) / 255 : 0
        specBands[i] += (v - specBands[i]) * (v > specBands[i] ? 0.5 : 0.1) // 快起慢落
        if (specBands[i] > 0.004) alive = true
        const bh = Math.max(2, (0.06 + specBands[i] * 0.94) * h)
        const x = i * (bw + gap)
        g.globalAlpha = 0.5 + specBands[i] * 0.5
        g.fillStyle = SPEC_COLORS[i]
        g.beginPath()
        if (g.roundRect) g.roundRect(x, h - bh, bw, bh, Math.max(0, Math.min(bw / 2, 2)))
        else g.rect(x, h - bh, bw, bh)
        g.fill()
      }
      g.globalAlpha = 1
    }
  }
  // bug86：续帧不再依赖画布当帧存在——画布短暂缺席（路由切换/列表重渲染）后回来，
  // 循环仍在，动效自恢复；此前续帧嵌在 if(w&&h) 里，画布消失一帧循环就永久死亡
  if ((playerStore.playing || alive) && !prefersReduced) specRaf = requestAnimationFrame(drawSpectrum)
}

watch(echoOn, (on) => {
  if (on && !prefersReduced) specAnalyser = getAnalyser(getAudioEl())
  if (!specRaf && !prefersReduced) specRaf = requestAnimationFrame(drawSpectrum)
}, { immediate: true }) // bug13：切页回来时 playing 已是 true 不触发 watch，immediate 补挂分析器

onMounted(async () => {
  await nextTick()
  if (heroEl.value) {
    // 轮播卡片是绝对定位 + transform 定位，GSAP 的 y 动画会覆盖其 transform，
    // 因此整块淡入，只对文字元产行错位入场
    revealStagger(heroEl.value.querySelectorAll('.eyebrow, h1, .artist, .actions, .spectrum'), { y: 18, stagger: 0.07 })
    if (carouselEl.value) revealStagger([carouselEl.value], { y: 22 })
  }
  if (!specRaf && !prefersReduced) specRaf = requestAnimationFrame(drawSpectrum)
})

onBeforeUnmount(() => { if (specRaf) cancelAnimationFrame(specRaf) })

const isAdmin = computed(() => userStore.role === 2)

function openEdit(row) {
  editSong.value = row
  editVisible.value = true
}

// preview-v2：板块标题固定「热门歌曲」，分类状态由上方 chips 表达
const currentTitle = '热门歌曲'

const currentTabLabel = computed(() => {
  const t = tabs.find((x) => x.key === tab.value)
  return t ? t.label : '推荐'
})

/* bug73：两列分类改为联合筛选——板块（推荐/热歌/飙升）与曲风相互独立、同时生效。
   此前点曲风会把板块顶掉（tab 被改成 style），点板块又清空曲风，只能二选一。 */
function switchTab(key) {
  tab.value = key
  loadTab()
}

function selectStyle(item) {
  style.value = item
  loadTab()
}

function play(index) {
  playerStore.playQueue(songs.value, index)
}

async function toggleCollect(row) {
  await userStore.toggleCollect(row.id)
}

async function addToPlaylist(pid, songId) {
  menuSong.value = null
  await api.addPlaylistSong(pid, songId)
}

async function loadTab() {
  loading.value = true
  try {
    // bug73：曲风作为独立筛选叠加在当前板块上（多取一些再过滤，保证过滤后仍有余量）
    let list = []
    if (tab.value === 'reco') {
      let reco = []
      if (userStore.isLogin) {
        try {
          reco = listOf(await api.recommendList(60))
        } catch (e) {
          reco = [] // 推荐服务异常时降级热歌榜
        }
      }
      list = reco.length ? reco : listOf(await api.songChart({ type: 'hot', limit: 60 }))
    } else {
      list = listOf(await api.songChart({ type: tab.value, limit: 60 }))
    }
    if (style.value) {
      list = list.filter((s) => s.style === style.value)
    }
    songs.value = list.slice(0, 20)
  } finally {
    loading.value = false
  }
}

async function loadMyPlaylists() {
  if (!userStore.isLogin) return
  const data = await api.myPlaylists()
  myPlaylists.value = listOf(data && (data.created || data))
}

onMounted(async () => {
  loadMyPlaylists()
  loadTab()
})

watch(reportTarget, (v) => { reportVisible.value = !!v })
</script>

<style scoped>
/* 滚动由外壳 .content-body 负责，这里不再自建滚动容器 */
.home-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

/* ============================================================
   hero：左侧曲目信息 + 右侧封面轮播
   ============================================================ */
.hero {
  padding: 10px 36px 2px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 32px;
  align-items: center;
}

.hero-meta {
  max-width: 420px;
  min-width: 0;
}

.eyebrow {
  font-family: var(--font-mono);
  font-size: 11.5px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.eyebrow::before {
  content: "";
  width: 22px;
  height: 1px;
  background: var(--accent);
}

.hero-meta h1 {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: clamp(26px, 3.2vw, 40px);
  line-height: 1.14;
  letter-spacing: -0.01em;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-all;
}

.hero-meta .artist {
  color: var(--text-muted);
  font-size: 15px;
  margin-top: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hero-meta .actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}

.btn-solid,
.btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  padding: 11px 22px;
  border-radius: var(--radius-pill);
  font-size: 14px;
  font-weight: 600;
  transition: transform 0.15s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}
.btn-solid {
  background: var(--accent);
  color: var(--accent-text);
}
.btn-solid:hover {
  box-shadow: 0 6px 24px rgba(255, 107, 26, 0.35);
}
.btn-solid svg,
.btn-ghost svg {
  width: 15px;
  height: 15px;
}
.btn-ghost {
  border: 1px solid var(--border);
  color: var(--text);
}
.btn-ghost:hover {
  border-color: rgba(0, 0, 0, 0.25);
}
.btn-ghost.on {
  color: var(--magenta);
  border-color: rgba(255, 47, 179, 0.45);
}

/* 签名式实时频谱：canvas，真音频 FFT 驱动（见 script drawSpectrum）。
   44 根柱沿品牌三色橙→洋红→青渐变，静息时只剩低矮基线 */
.spectrum {
  margin-top: 22px;
  width: 100%;
  height: 64px;
  display: block;
}

/* ---------- 轮播：coverflow，切换时 transform/尺寸/透明度过渡 ----------
   容器 780px 比焦点卡(330px)两侧各富余 225px：侧卡带 3D 旋转的投影会比
   卡片本身宽约 40px，这些空间让侧卡/远卡完整落在容器内，不会被窗口裁切。
   偏移用 CSS 变量表达，窄屏逐级收紧（见下方媒体查询）。 */
.carousel {
  position: relative;
  width: 780px;
  height: 296px;
  perspective: 1300px;
  flex-shrink: 0;
  --side-x: 240px;
  --far-x: 290px;
}

.cover {
  margin: 0;
  position: absolute;
  left: 50%;
  top: 50%;
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-lg);
  cursor: pointer;
  background: var(--surface-2);
  /* 入场由 GSAP 淡入整块容器，这里只负责位置切换过渡 */
  transition:
    transform 0.55s cubic-bezier(0.2, 0.8, 0.3, 1),
    width 0.55s cubic-bezier(0.2, 0.8, 0.3, 1),
    height 0.55s cubic-bezier(0.2, 0.8, 0.3, 1),
    opacity 0.45s ease,
    filter 0.45s ease;
  will-change: transform, width, height;
}
.cover img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.art-ph {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255, 255, 255, 0.55);
  font-size: 26px;
}
.art-ph::after {
  content: "♪";
}

/* 焦点卡：330px 居中，无旋转 */
.cover.focus {
  width: 290px;
  height: 290px;
  transform: translate(-50%, -50%);
  z-index: 30;
  outline: 1px solid rgba(0, 0, 0, 0.06);
  outline-offset: -1px;
  box-shadow: var(--shadow-lg), 0 0 60px rgba(255, 107, 26, 0.14);
}
/* 侧卡：200px，向两侧退开并 3D 旋转（透视在容器上） */
.cover.side {
  width: 180px;
  height: 180px;
  opacity: 0.7;
  filter: brightness(0.9);
  z-index: 20;
}
.cover.side.l {
  transform: translate(-50%, -50%) translateX(calc(-1 * var(--side-x))) rotateY(16deg);
}
.cover.side.r {
  transform: translate(-50%, -50%) translateX(var(--side-x)) rotateY(-16deg);
}
.cover.side:hover {
  opacity: 0.95;
  filter: brightness(1);
}
/* 更远的卡：藏在两侧外，渐隐（切换时从边缘滑入滑出） */
.cover.far {
  width: 135px;
  height: 135px;
  opacity: 0;
  pointer-events: none;
  z-index: 10;
}
.cover.far.l {
  transform: translate(-50%, -50%) translateX(calc(-1 * var(--far-x))) rotateY(24deg) scale(0.8);
}
.cover.far.r {
  transform: translate(-50%, -50%) translateX(var(--far-x)) rotateY(-24deg) scale(0.8);
}

.cover figcaption {
  position: absolute;
  inset: auto 0 0 0;
  padding: 20px;
  z-index: 3;
  background: linear-gradient(180deg, transparent, rgba(5, 5, 8, 0.82));
  text-align: left;
  opacity: 0;
  transition: opacity 0.4s ease 0.15s;
  pointer-events: none;
}
.cover.focus figcaption {
  opacity: 1;
}
.cover figcaption b {
  font-family: var(--font-display);
  font-weight: 500;
  font-size: 17px;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cover figcaption span {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.7);
}

/* ============================================================
   sections
   ============================================================ */
.section {
  padding: 16px 36px 0;
}

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
}
.section-head h2 {
  font-size: 17px;
  font-weight: 700;
}
.section-head a {
  font-size: 13px;
  color: var(--text-muted);
  text-decoration: none;
}
.section-head a:hover {
  color: var(--text);
}

.chips {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.chip {
  padding: 8px 20px;
  border-radius: var(--radius-pill);
  font-size: 13.5px;
  font-weight: 500;
  border: 1px solid var(--border);
  color: var(--text-muted);
  background: transparent;
  transition: all 0.2s ease;
}
.chip:hover {
  color: var(--text);
  border-color: rgba(0, 0, 0, 0.24);
}
.chip[aria-pressed="true"] {
  background: var(--accent);
  border-color: var(--accent);
  color: var(--accent-text);
  font-weight: 700;
}

/* 曲风行：更小更轻的次级胶囊，与榜单主行拉开层级 */
.chips.sub {
  margin-top: 10px;
  gap: 8px;
}
.chip.ghost {
  padding: 5px 14px;
  font-size: 12.5px;
}
.chip.ghost[aria-pressed="true"] {
  background: var(--surface-2);
  border-color: var(--accent);
  color: var(--accent);
}

/* ---------- 歌曲卡片：见 components/SongGrid.vue（上下结构） ---------- */
/* 封面右上角插槽按钮：与 SongGrid .op-btn 同规格 */
.hv-op {
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
.hv-op:hover {
  background: #fff;
  color: var(--accent);
}

.menu-wrap {
  position: relative;
}
.menu-pop {
  position: absolute;
  right: 0;
  top: 28px;
  min-width: 120px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 10px;
  box-shadow: var(--shadow-md);
  padding: 4px;
  z-index: 50;
}
.menu-item {
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 12.5px;
  cursor: pointer;
}
.menu-item:hover {
  background: var(--accent-soft);
  color: var(--accent);
}

.spacer {
  height: 8px;
  flex-shrink: 0;
}

/* ---------- 响应式 ---------- */
/* 歌词栏展开时内容净宽少 402px，但媒体查询只看窗口宽度感知不到：
   两栏 hero 里固定 780px 的轮播会把 meta 列挤成窄条——歌名截断、
   频谱归零。lyrics-on 由 App.vue 挂在 .content-row 上，提前降级单列 */
.lyrics-on .hero {
  grid-template-columns: 1fr;
  justify-items: center;
  text-align: center;
}
.lyrics-on .eyebrow {
  justify-content: center;
}
.lyrics-on .hero-meta .actions {
  justify-content: center;
}
.lyrics-on .carousel {
  order: -1;
  width: min(780px, 100%);
  --side-x: 200px;
  --far-x: 260px;
}

/* 两栏 hero（meta+coverflow）需要 232 侧栏+72 内边距+780 轮播+~330 meta ≈ 1414，
   再窄改单列纵向排布，避免挤压文字和裁切侧卡 */
@media (max-width: 1440px) {
  .hero {
    grid-template-columns: 1fr;
    justify-items: center;
    text-align: center;
  }
  .hero-meta {
    max-width: 420px;
  }
  .eyebrow {
    justify-content: center;
  }
  .hero-meta .actions {
    justify-content: center;
  }
  .carousel {
    order: -1;
    width: min(780px, 100%);
    /* 单列下适度收紧，保证旋转投影完整落在本列内 */
    --side-x: 200px;
    --far-x: 260px;
  }
}

/* 再窄（侧栏已收缩成图标栏）：只留焦点卡，双列 coverflow 放不下 */
@media (max-width: 960px) {
  .carousel {
    width: min(100%, 400px);
    height: auto;
    aspect-ratio: 1;
  }
  .cover.side,
  .cover.far {
    display: none;
  }
}

@media (max-width: 720px) {
  .hero,
  .section {
    padding-inline: 12px;
  }
  .hero {
    padding-top: 16px;
  }
  .carousel {
    width: 100%;
    height: auto;
    aspect-ratio: 1;
    justify-content: center;
  }
  .cover.side,
  .cover.far {
    display: none;
  }
  .cover.focus {
    width: min(100%, 260px);
    height: auto;
    aspect-ratio: 1;
  }
  .hero-meta .actions {
    margin-top: 18px;
  }
  .spectrum {
    display: none;
  }
}
</style>
