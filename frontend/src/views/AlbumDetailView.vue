<template>
  <div class="al-detail" v-loading="loading">
    <div class="al-head" v-if="album" :style="headTint ? { background: headTint.bg } : {}" :class="{ tinted: !!headTint }">
      <div class="al-cover">
        <el-image v-if="album.coverUrl" :src="resolveFileUrl(album.coverUrl)" fit="cover"
          style="width: 100%; height: 100%">
          <template #error><GenCover :name="album.name" :id="album.id" glyph="♫" /></template>
        </el-image>
        <GenCover v-else :name="album.name" :id="album.id" glyph="♫" />
      </div>
      <div class="al-meta">
        <div class="eyebrow">Album · 专辑</div>
        <h2>{{ album.name }}</h2>
        <p class="desc">{{ album.singerName || '独立歌手' }} · {{ album.description || '暂无简介' }}</p>
        <div class="actions">
          <button class="btn btn-primary" :disabled="!songs.length" @click="playerStore.playQueue(songs, 0)">
            <svg viewBox="0 0 24 24" fill="currentColor" width="15" height="15"><path d="M7 5v14l12-7z" /></svg>
            播放全部
          </button>
          <!-- 收藏是"收别人的专辑"：自己的专辑不显示收藏按钮 -->
          <button v-if="userStore.isLogin && !isOwner" class="btn btn-secondary" @click="toggleFavorite">
            {{ favorited ? '取消收藏' : '收藏专辑' }}
          </button>
          <button v-if="isOwner" class="btn btn-secondary" @click="addVisible = true">添加歌曲</button>
          <button v-if="isOwner" class="btn btn-danger" @click="remove">删除专辑</button>
        </div>
      </div>
    </div>

    <SongGrid :songs="songs" :show-collect="userStore.isLogin" @play="(i) => playerStore.playQueue(songs, i)">
      <template #ops="{ row }">
        <button v-if="isOwner" class="op-btn" title="从专辑移除" @click.stop="removeSong(row)">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="5" y1="12" x2="19" y2="12" />
          </svg>
        </button>
      </template>
    </SongGrid>

    <div v-if="!songs.length && !loading" class="empty-state">
      <h3>{{ loadError || '专辑还没有歌曲' }}</h3>
      <p>{{ loadError ? '请稍后重试，或联系管理员检查服务。' : (isOwner ? '点上方"添加歌曲"把作品收进来' : '歌手还在整理这张专辑') }}</p>
      <button class="btn btn-primary" @click="loadError ? load() : $router.push('/songs')">去广场逛逛</button>
    </div>

    <!-- 主人添加歌曲：bug65——只能从自己的歌曲里挑选 -->
    <el-dialog v-model="addVisible" title="添加歌曲到专辑（仅自己上传的歌）" width="480px">
      <el-input v-model="pickKeyword" placeholder="搜索歌名 / 歌手" clearable style="margin-bottom: 10px" />
      <div class="pick-list">
        <div v-for="s in pickSongs" :key="s.id" class="pick-row">
          <span class="pick-name">{{ s.name }}</span>
          <span class="pick-artist">{{ singerName(s) }}</span>
          <el-button v-if="!songIds.has(s.id)" size="small" type="primary" plain @click="addSong(s)">加入</el-button>
          <el-button v-else size="small" disabled>已在专辑</el-button>
        </div>
        <div v-if="!pickSongs.length && !pickLoading" class="pick-empty">没有可添加的歌曲</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
// 专辑详情：访客可播放/收藏（引用语义）；主人（歌手）可加歌/移除/删除。
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, confirm } from '../utils/feedback'
import api from '../api'
import { useUserStore } from '../stores/user'
import { usePlayerStore } from '../stores/player'
import SongGrid from '../components/SongGrid.vue'
import { resolveFileUrl, singerName, listOf, playlistSongs } from '../utils'
import GenCover from '../components/GenCover.vue'
import { coverTint } from '../utils/color'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const playerStore = usePlayerStore()
const album = ref(null)
const songs = ref([])
const favorited = ref(false)
const loading = ref(false)
const loadError = ref('')

const isOwner = computed(() =>
  userStore.isLogin && album.value && Number(album.value.userId) === Number(userStore.userId))
const songIds = computed(() => new Set(songs.value.map((s) => Number(s.id))))

// 头部主色背景（bug18）：从专辑封面（后端自动取最新歌曲封面）提取浅色渐变
const headTint = ref(null)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await api.albumDetail(route.params.id)
    album.value = (data && data.album) || data
    // 专辑歌曲行是关联表行（songId），映射成统一 id（bug5：否则移除/播放拿不到 row.id）
    songs.value = playlistSongs(data)
    favorited.value = !!(album.value && album.value.favored)
    headTint.value = null
    if (album.value && album.value.coverUrl) {
      coverTint(resolveFileUrl(album.value.coverUrl)).then((t) => { if (t) headTint.value = t })
    }
  } catch (e) {
    album.value = null
    songs.value = []
    loadError.value = (e && e.response && e.response.data && e.response.data.message)
      || (e && e.message) || '专辑加载失败'
  } finally {
    loading.value = false
  }
}

async function toggleFavorite() {
  if (favorited.value) await api.albumUnfavorite(route.params.id)
  else await api.albumFavorite(route.params.id)
  favorited.value = !favorited.value
  message.success(favorited.value ? '已收藏专辑' : '已取消收藏')
}

async function remove() {
  await confirm('删除专辑将同时移除其中歌曲关联，确认删除？', { title: '删除专辑', okText: '删除' })
  await api.albumDelete(route.params.id)
  message.success('已删除')
  window.dispatchEvent(new Event('md-playlists-changed'))
  router.push('/library?tab=albums')
}

async function removeSong(row) {
  await api.albumRemoveSong(route.params.id, row.id)
  message.success('已移除')
  load()
}

/* 添加歌曲弹窗：bug65——专辑只能收录自己的歌，主人视角从"我的歌曲"里挑，不再全站搜索 */
const addVisible = ref(false)
const pickKeyword = ref('')
const pickSongs = ref([])
const pickLoading = ref(false)
let pickTimer = null
let pickAll = []

watch(addVisible, (open) => {
  if (open) loadPick()
})
watch(pickKeyword, () => {
  clearTimeout(pickTimer)
  pickTimer = setTimeout(loadPick, 300)
})

async function loadPick() {
  pickLoading.value = true
  try {
    // 一次拉全自己的歌（含审核中），本地过滤：只有已发布的能进专辑（后端同口径兜底）
    const mine = listOf(await api.songMine({ page: 1, size: 200 }))
    pickAll = mine.filter((s) => s.status === undefined || s.status === 2)
    applyPickFilter()
  } finally {
    pickLoading.value = false
  }
}

function applyPickFilter() {
  const kw = pickKeyword.value.trim().toLowerCase()
  pickSongs.value = !kw ? pickAll : pickAll.filter((s) =>
    (s.name || '').toLowerCase().includes(kw) || (singerName(s) || '').toLowerCase().includes(kw))
}

async function addSong(s) {
  await api.albumAddSong(route.params.id, s.id)
  message.success(`已加入《${s.name}》`)
  load()
}

watch(() => route.params.id, load, { immediate: true })
</script>

<style scoped>
.al-detail {
  animation: v2-rise 0.5s cubic-bezier(0.2, 0.8, 0.3, 1) both;
}

.al-head {
  display: flex;
  gap: 24px;
  padding: 30px 24px;
  margin-bottom: 8px;
  border-radius: var(--radius-lg);
  align-items: flex-end;
}
/* 主色头部（bug18）：封面主色浅渐变，圆角卡片化 */
.al-head.tinted {
  background-size: cover;
  box-shadow: var(--shadow-sm);
}
.al-cover {
  width: 150px;
  height: 150px;
  border-radius: var(--radius-lg);
  flex: none;
  position: relative;
  overflow: hidden;
  background: var(--surface-2);
  box-shadow: var(--shadow-md);
}
.al-cover .ph {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: rgba(255, 255, 255, 0.7);
  font-size: 40px;
}

.al-meta {
  flex: 1;
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
  margin-bottom: 10px;
}
.eyebrow::before {
  content: "";
  width: 22px;
  height: 1px;
  background: var(--accent);
}
.al-meta h2 {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 26px;
  letter-spacing: -0.01em;
}
.desc {
  color: var(--text-muted);
  font-size: 13.5px;
  margin: 8px 0 16px;
}
.actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.op-btn {
  width: 26px;
  height: 26px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface-2);
  color: var(--text-muted);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.op-btn:hover {
  color: var(--error);
  border-color: rgba(255, 90, 95, 0.45);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 60px 0 40px;
  text-align: center;
}
.empty-state h3 {
  font-size: 17px;
  font-weight: 700;
}
.empty-state p {
  font-size: 13.5px;
  color: var(--text-muted);
}
.empty-state .btn {
  margin-top: 14px;
}

/* 添加歌曲弹窗 */
.pick-list {
  max-height: 320px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.pick-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 10px;
  border-radius: 8px;
}
.pick-row:hover {
  background: var(--surface-2);
}
.pick-name {
  flex: 1;
  min-width: 0;
  font-size: 13.5px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.pick-artist {
  font-size: 12px;
  color: var(--text-muted);
  flex-shrink: 0;
}
.pick-empty {
  text-align: center;
  color: var(--text-faint);
  padding: 20px 0;
  font-size: 13px;
}

@media (max-width: 720px) {
  .al-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
