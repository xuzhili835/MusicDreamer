<template>
  <div class="search" v-loading="loading">
    <header class="content-header">
      <div>
        <div class="eyebrow">Search</div>
        <h1>{{ keyword ? `“${keyword}” 的搜索结果` : '搜索' }}</h1>
      </div>
      <div class="tabs" role="tablist" aria-label="搜索类型">
        <button class="chip" role="tab" :aria-selected="tab === 'songs'" @click="tab = 'songs'">歌曲</button>
        <button class="chip" role="tab" :aria-selected="tab === 'singers'" @click="tab = 'singers'">歌手</button>
      </div>
    </header>

    <template v-if="tab === 'songs'">
      <SongGrid :songs="songs" @play="(i) => playerStore.playQueue(songs, i)" />

      <div v-if="!songs.length && !loading" class="empty-state">
        <h3>{{ keyword ? '没有找到匹配的歌曲' : '输入关键词搜索歌曲' }}</h3>
        <p>{{ keyword ? '换个关键词试试？' : '使用顶部搜索框，输入歌名、歌手或专辑' }}</p>
      </div>

      <el-pagination v-if="total > size" v-model:current-page="page" :page-size="size" :total="total"
        layout="prev, pager, next" @current-change="doSearch" class="pager" />
    </template>

    <template v-else>
      <div class="singer-grid">
        <div v-for="s in singers" :key="s.id" class="singer-card">
          <div class="singer-avatar">
            <el-image v-if="s.avatar" :src="resolveFileUrl(s.avatar)" fit="cover"
              style="width: 100%; height: 100%">
              <template #error><span class="ph">{{ initial(s) }}</span></template>
            </el-image>
            <span v-else class="ph">{{ initial(s) }}</span>
          </div>
          <b>{{ s.nickname || s.username }}</b>
        </div>
      </div>
      <div v-if="!singers.length && !loading" class="empty-state">
        <h3>{{ keyword ? '没有找到匹配的歌手' : '输入关键词搜索歌手' }}</h3>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import api from '../api'
import { usePlayerStore } from '../stores/player'
import SongGrid from '../components/SongGrid.vue'
import { listOf, pageOf, resolveFileUrl } from '../utils'

const route = useRoute()
const playerStore = usePlayerStore()
const tab = ref('songs')
const keyword = ref('')
const songs = ref([])
const singers = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

async function doSearch() {
  if (!keyword.value) return
  loading.value = true
  try {
    if (tab.value === 'songs') {
      const { list, total: t } = pageOf(await api.searchSongs({ keyword: keyword.value, page: page.value, size: size.value }))
      songs.value = list
      total.value = t
    } else {
      singers.value = listOf(await api.searchSingers({ keyword: keyword.value, page: 1, size: 30 }))
    }
  } finally {
    loading.value = false
  }
}

watch(() => route.query.keyword, (v) => {
  if (v) { keyword.value = String(v); page.value = 1; doSearch() }
}, { immediate: true })

watch(tab, () => doSearch())

function initial(s) {
  return (s.nickname || s.username || '?').slice(0, 1)
}
</script>

<style scoped>
.search {
  animation: v2-rise 0.5s cubic-bezier(0.2, 0.8, 0.3, 1) both;
}

.content-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 30px 0 22px;
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

.content-header h1 {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 24px;
  letter-spacing: -0.01em;
  max-width: 560px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 分类胶囊（preview-v2 chips） */
.tabs {
  display: flex;
  gap: 10px;
}
.chip {
  padding: 8px 20px;
  border-radius: var(--radius-pill);
  font-size: 13.5px;
  font-weight: 500;
  border: 1px solid var(--border);
  color: var(--text-muted);
  transition: 0.2s;
}
.chip:hover {
  color: var(--text);
  border-color: rgba(0, 0, 0, 0.24);
}
.chip[aria-selected="true"] {
  background: var(--accent);
  border-color: var(--accent);
  color: var(--accent-text);
  font-weight: 700;
}

.pager {
  margin-top: 18px;
  justify-content: center;
}

/* 歌手卡：纸白 + 圆角头像 */
.singer-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
}
.singer-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 12px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
  font-size: 13.5px;
  cursor: default;
  transition: transform 0.2s, border-color 0.2s, box-shadow 0.2s;
}
.singer-card:hover {
  transform: translateY(-2px);
  border-color: rgba(0, 0, 0, 0.14);
  box-shadow: var(--shadow-md);
}
.singer-avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  overflow: hidden;
  position: relative;
  background: var(--surface-2);
  outline: 1px solid var(--border-strong);
}
.singer-avatar .ph {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 20px;
  color: var(--accent);
  background: var(--accent-soft);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 70px 0 40px;
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
</style>
