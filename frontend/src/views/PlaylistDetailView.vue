<template>
  <div class="pl-detail" v-loading="loading">
    <div class="pl-head" v-if="playlist">
      <div class="pl-cover">
        <el-image v-if="playlist.coverUrl" :src="resolveFileUrl(playlist.coverUrl)" fit="cover"
          style="width: 100%; height: 100%">
          <template #error><GenCover :name="playlist.name" :id="playlist.id" glyph="♪" /></template>
        </el-image>
        <GenCover v-else :name="playlist.name" :id="playlist.id" glyph="♪" />
      </div>
      <div class="pl-meta">
        <div class="eyebrow">Playlist</div>
        <h2>{{ playlist.name }}</h2>
        <p class="desc">
          {{ playlist.description || '暂无描述' }}
          <span v-if="!isOwner && playlist.creatorName" class="creator">by {{ playlist.creatorName }}</span>
        </p>
        <div class="actions">
          <button class="btn btn-primary" :disabled="!songs.length" @click="playerStore.playQueue(songs, 0)">
            <svg viewBox="0 0 24 24" fill="currentColor" width="15" height="15"><path d="M7 5v14l12-7z" /></svg>
            播放全部
          </button>
          <!-- 收藏是"收别人的歌单"：自己的歌单不显示收藏按钮 -->
          <button v-if="userStore.isLogin && !isOwner" class="btn btn-secondary" @click="toggleFavorite">
            {{ favorited ? '取消收藏' : '收藏歌单' }}
          </button>
          <button v-if="isOwner" class="btn btn-secondary" @click="openEdit">编辑信息</button>
          <button v-if="isOwner" class="btn btn-secondary" @click="addVisible = true">添加歌曲</button>
          <button v-if="isOwner" class="btn btn-danger" @click="remove">删除歌单</button>
        </div>
      </div>
    </div>

    <SongGrid :songs="songs" :show-collect="userStore.isLogin" @play="(i) => playerStore.playQueue(songs, i)">
      <template #ops="{ row }">
        <button v-if="isOwner" class="op-btn" title="从歌单移除" @click.stop="removeSong(row)">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="5" y1="12" x2="19" y2="12" />
          </svg>
        </button>
      </template>
    </SongGrid>

    <div v-if="!songs.length && !loading" class="empty-state">
      <h3>{{ loadError || '歌单还没有歌曲' }}</h3>
      <p>{{ loadError ? '请稍后重试，或联系管理员检查音乐服务。' : (isOwner ? '点上方"添加歌曲"把喜欢的歌收进来' : '歌单主人还没收录歌曲') }}</p>
      <button class="btn btn-primary" @click="loadError ? load() : (isOwner ? (addVisible = true) : $router.push('/'))">
        {{ loadError ? '重新加载' : (isOwner ? '添加歌曲' : '去首页逛逛') }}
      </button>
    </div>

    <!-- 主人添加歌曲：从全站曲库挑选（bug3，弹窗与专辑页同款） -->
    <el-dialog v-model="addVisible" title="添加歌曲到歌单" width="480px">
      <el-input v-model="pickKeyword" placeholder="搜索歌名 / 歌手" clearable style="margin-bottom: 10px" />
      <div class="pick-list">
        <div v-for="s in pickSongs" :key="s.id" class="pick-row">
          <span class="pick-name">{{ s.name }}</span>
          <span class="pick-artist">{{ singerName(s) }}</span>
          <el-button v-if="!songIds.has(s.id)" size="small" type="primary" plain @click="addSong(s)">加入</el-button>
          <el-button v-else size="small" disabled>已在歌单</el-button>
        </div>
        <div v-if="!pickSongs.length && !pickLoading" class="pick-empty">没有可添加的歌曲</div>
      </div>
    </el-dialog>

    <!-- 编辑歌单信息（仅创建者）：改名/描述/公开开关 -->
    <el-dialog v-model="editVisible" title="编辑歌单信息" width="420px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="歌单名称"><el-input v-model="editForm.name" maxlength="50" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="editForm.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="公开">
          <el-switch v-model="editForm.isPublic" />
          <span class="edit-hint">{{ editForm.isPublic ? '所有人可在广场看到并收藏' : '仅自己可见' }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, confirm } from '../utils/feedback'
import api from '../api'
import { useUserStore } from '../stores/user'
import { usePlayerStore } from '../stores/player'
import SongGrid from '../components/SongGrid.vue'
import GenCover from '../components/GenCover.vue'
import { playlistSongs, resolveFileUrl, singerName, pageOf } from '../utils'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const playerStore = usePlayerStore()
const playlist = ref(null)
const songs = ref([])
const favorited = ref(false)
const loading = ref(false)
const loadError = ref('')
const editVisible = ref(false)
const saving = ref(false)
const editForm = ref({ name: '', description: '', isPublic: true })

const isOwner = computed(() =>
  userStore.isLogin && playlist.value && Number(playlist.value.userId) === Number(userStore.userId))
const songIds = computed(() => new Set(songs.value.map((s) => Number(s.id))))

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await api.getPlaylist(route.params.id)
    playlist.value = data && data.playlist ? data.playlist : data
    songs.value = playlistSongs(data)
    favorited.value = !!(playlist.value && playlist.value.favored)
  } catch (e) {
    playlist.value = null
    songs.value = []
    loadError.value = (e && e.response && e.response.data && e.response.data.message)
      || (e && e.message) || '歌单加载失败'
  } finally {
    loading.value = false
  }
}

async function toggleFavorite() {
  if (favorited.value) await api.unfavoritePlaylist(route.params.id)
  else await api.favoritePlaylist(route.params.id)
  favorited.value = !favorited.value
  window.dispatchEvent(new Event('md-playlists-changed'))
  message.success(favorited.value ? '已收藏歌单' : '已取消收藏')
}

async function remove() {
  await confirm('删除歌单将同时移除其中歌曲关联，确认删除？', { title: '删除歌单', okText: '删除' })
  await api.deletePlaylist(route.params.id)
  message.success('已删除')
  window.dispatchEvent(new Event('md-playlists-changed'))
  router.push('/my/playlists')
}

async function removeSong(row) {
  await api.removePlaylistSong(route.params.id, row.id)
  message.success('已移除')
  load()
}

function openEdit() {
  const p = playlist.value || {}
  editForm.value = {
    name: p.name || '',
    description: p.description || '',
    isPublic: !!p.isPublic
  }
  editVisible.value = true
}

async function saveEdit() {
  if (!editForm.value.name.trim()) return message.warning('歌单名称不能为空')
  saving.value = true
  try {
    await api.updatePlaylist(route.params.id, {
      name: editForm.value.name.trim(),
      description: editForm.value.description,
      isPublic: editForm.value.isPublic
    })
    editVisible.value = false
    message.success('歌单已更新')
    window.dispatchEvent(new Event('md-playlists-changed'))
    load()
  } finally {
    saving.value = false
  }
}

/* 添加歌曲弹窗：搜索全站歌曲 */
const addVisible = ref(false)
const pickKeyword = ref('')
const pickSongs = ref([])
const pickLoading = ref(false)
let pickTimer = null

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
    const data = pickKeyword.value
      ? await api.searchSongs({ keyword: pickKeyword.value, page: 1, size: 30 })
      : await api.searchByStyle({ page: 1, size: 30 })
    pickSongs.value = pageOf(data).list
  } finally {
    pickLoading.value = false
  }
}

async function addSong(s) {
  await api.addPlaylistSong(route.params.id, s.id)
  message.success(`已加入《${s.name}》`)
  window.dispatchEvent(new Event('md-playlists-changed'))
  load()
}

watch(() => route.params.id, load, { immediate: true })
</script>

<style scoped>
.pl-detail {
  animation: v2-rise 0.5s cubic-bezier(0.2, 0.8, 0.3, 1) both;
}

.pl-head {
  display: flex;
  gap: 24px;
  padding: 30px 0 24px;
  align-items: flex-end;
}
.pl-cover {
  width: 150px;
  height: 150px;
  border-radius: var(--radius-lg);
  flex: none;
  position: relative;
  overflow: hidden;
  background: var(--surface-2);
  box-shadow: var(--shadow-md);
}
.pl-cover .ph {
  position: absolute;
  inset: 0;
}

.pl-meta {
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
.pl-meta h2 {
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
.creator {
  display: inline-block;
  margin-left: 10px;
  padding: 1px 10px;
  border-radius: var(--radius-pill);
  background: var(--surface-2);
  color: var(--text);
  font-size: 12px;
  font-weight: 600;
}
.edit-hint {
  margin-left: 10px;
  color: var(--text-muted);
  font-size: 12px;
}
.actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

/* SongGrid ops 插槽里的移除按钮（样式与组件内 op-btn 一致） */
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

/* 添加歌曲弹窗（与专辑页同款） */
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
  .pl-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
