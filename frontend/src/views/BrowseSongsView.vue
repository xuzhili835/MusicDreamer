<template>
  <div class="browse-songs" v-loading="loading">
    <header class="content-header">
      <div>
        <div class="eyebrow">Discover · 广场</div>
        <h1>{{ headerTitle }}</h1>
      </div>
      <span class="count">{{ headerCount }}</span>
    </header>

    <!-- 广场三页签：歌曲 / 歌单 / 专辑 -->
    <div class="plaza-tabs" role="tablist" aria-label="广场分类">
      <button v-for="t in PLAZA_TABS" :key="t.key" class="plaza-tab" role="tab"
        :aria-selected="String(plazaTab === t.key)" @click="switchPlazaTab(t.key)">
        {{ t.label }}
      </button>
    </div>

    <!-- 歌曲：原全部歌曲（搜索/曲风/网格） -->
    <template v-if="plazaTab === 'songs'">
      <div v-if="!keyword" class="filters" role="group" aria-label="曲风筛选">
        <button class="chip" :aria-pressed="!style" @click="selectStyle('')">全部</button>
        <button v-for="item in STYLES" :key="item" class="chip" :aria-pressed="style === item"
          @click="selectStyle(item)">{{ item }}</button>
      </div>

      <SongGrid v-if="songs.length" :songs="songs" @play="play" />

      <div v-if="!songs.length && !loading" class="empty-state">
        <h3>{{ keyword ? '没有找到匹配的歌曲' : '暂无可播放歌曲' }}</h3>
        <p>{{ keyword ? '换个关键词试试' : '稍后再来看看' }}</p>
      </div>

      <el-pagination v-if="total > size" v-model:current-page="page" :page-size="size" :total="total"
        layout="prev, pager, next" class="pager" @current-change="load" />
    </template>

    <!-- 歌单：公开歌单广场（别人的创作，点击进详情可收藏） -->
    <template v-else-if="plazaTab === 'playlists'">
      <div class="plaza-grid wide">
        <router-link v-for="p in plazaAll" :key="p.id" :to="'/playlist/' + p.id" class="pl-card" :title="p.name">
          <div class="pl-cover">
            <img v-if="p.coverUrl" :src="resolveFileUrl(p.coverUrl)" alt="" loading="lazy" @error="p.coverUrl = ''">
            <GenCover v-else :name="p.name" :id="p.id" glyph="♪" />
          </div>
          <b class="pl-name">{{ p.name }}</b>
          <span class="pl-count">{{ p.creatorName ? p.creatorName + ' · ' : '' }}{{ p.songCount || 0 }} 首</span>
        </router-link>
      </div>
      <div v-if="!plazaAll.length && !plazaLoading" class="empty-state">
        <h3>还没有公开歌单</h3>
        <p>把歌单设为公开，就会出现在这里与大家分享。</p>
      </div>
    </template>

    <!-- 专辑：公开专辑广场 -->
    <template v-else>
      <div class="plaza-grid wide">
        <router-link v-for="a in albumPlaza" :key="a.id" :to="'/album/' + a.id" class="pl-card" :title="a.name">
          <div class="pl-cover">
            <img v-if="a.coverUrl" :src="resolveFileUrl(a.coverUrl)" alt="" loading="lazy" @error="a.coverUrl = ''">
            <GenCover v-else :name="a.name" :id="a.id" glyph="♫" />
          </div>
          <b class="pl-name">{{ a.name }}</b>
          <span class="pl-count">{{ a.singerName || '歌手' }} · {{ a.songCount || 0 }} 首</span>
        </router-link>
      </div>
      <div v-if="!albumPlaza.length && !plazaLoading" class="empty-state">
        <h3>还没有公开专辑</h3>
        <p>歌手发布的专辑会出现在这里。</p>
      </div>
    </template>
  </div>
</template>

<script setup>
// 广场 = 发现别人的公开内容：歌曲 / 歌单 / 专辑 三页签。
// 歌曲页签沿用原"全部歌曲"（搜索/曲风/分页）；歌单、专辑为公开广场网格。
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '../api'
import SongGrid from '../components/SongGrid.vue'
import GenCover from '../components/GenCover.vue'
import { pageOf, listOf, resolveFileUrl, STYLES } from '../utils'
import { usePlayerStore } from '../stores/player'

const route = useRoute()
const router = useRouter()
const playerStore = usePlayerStore()

const PLAZA_TABS = [
  { key: 'songs', label: '歌曲' },
  { key: 'playlists', label: '歌单' },
  { key: 'albums', label: '专辑' }
]
const plazaTab = ref('songs')
const plazaLoading = ref(false)

const songs = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(24)
const style = ref('')
const keyword = ref('')
const loading = ref(false)
const plazaAll = ref([])
const albumPlaza = ref([])

const headerTitle = computed(() => {
  if (plazaTab.value === 'songs') return keyword.value ? `“${keyword.value}” 的结果` : '全部歌曲'
  return plazaTab.value === 'playlists' ? '公开歌单' : '公开专辑'
})
const headerCount = computed(() => {
  if (plazaTab.value === 'songs') return `${total.value} 首`
  if (plazaTab.value === 'playlists') return `${plazaAll.value.length} 张歌单`
  return `${albumPlaza.value.length} 张专辑`
})

function play(index) {
  playerStore.playQueue(songs.value, index)
}

function selectStyle(value) {
  style.value = value
  page.value = 1
  load()
}

function switchPlazaTab(key) {
  plazaTab.value = key
  router.replace({ query: { ...route.query, tab: key } })
  if (key === 'playlists') loadPlazaAll()
  if (key === 'albums') loadAlbumPlaza()
}

async function loadPlazaAll() {
  plazaLoading.value = true
  try {
    const data = await api.publicPlaylists({ page: 1, size: 60 })
    plazaAll.value = listOf(data)
  } catch (e) {
    plazaAll.value = []
  } finally {
    plazaLoading.value = false
  }
}

async function loadAlbumPlaza() {
  plazaLoading.value = true
  try {
    const data = await api.publicAlbums({ page: 1, size: 60 })
    albumPlaza.value = listOf(data)
  } catch (e) {
    albumPlaza.value = []
  } finally {
    plazaLoading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const data = keyword.value
      ? await api.searchSongs({ keyword: keyword.value, page: page.value, size: size.value })
      : await api.searchByStyle({ style: style.value || undefined, page: page.value, size: size.value })
    const result = pageOf(data)
    songs.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
  }
}

watch(() => route.query.tab, (t) => {
  if (PLAZA_TABS.some((x) => x.key === t)) plazaTab.value = t
}, { immediate: true })

watch([() => route.query.keyword, () => route.query.style], ([keywordQuery, styleQuery]) => {
  keyword.value = typeof keywordQuery === 'string' ? keywordQuery.trim() : ''
  style.value = keyword.value ? '' : (typeof styleQuery === 'string' ? styleQuery : '')
  page.value = 1
  // 仅搜索词存在时才强制回歌曲页签；纯浏览歌单/专辑时保持当前页签
  if (keyword.value) plazaTab.value = 'songs'
  if (plazaTab.value === 'songs') load()
}, { immediate: true })

if (plazaTab.value === 'playlists') loadPlazaAll()
if (plazaTab.value === 'albums') loadAlbumPlaza()
</script>

<style scoped>
.browse-songs { animation: v2-rise 0.5s cubic-bezier(0.2, 0.8, 0.3, 1) both; }
.content-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 30px 0 18px; }
.eyebrow { font-family: var(--font-mono); font-size: 11.5px; letter-spacing: 0.22em; text-transform: uppercase; color: var(--text-muted); display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.eyebrow::before { content: ''; width: 22px; height: 1px; background: var(--accent); }
h1 { font-family: var(--font-display); font-size: 26px; font-weight: 700; }
.count { color: var(--text-muted); font-family: var(--font-mono); font-size: 12px; }

/* 广场页签 */
.plaza-tabs {
  display: flex;
  gap: 8px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 20px;
}
.plaza-tab {
  display: inline-flex;
  align-items: center;
  padding: 8px 18px;
  border: 1px solid transparent;
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--text-muted);
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}
.plaza-tab:hover { color: var(--text); background: var(--surface-2); }
.plaza-tab[aria-selected="true"] {
  background: var(--accent);
  color: var(--accent-text);
  font-weight: 700;
}

/* 广场网格（歌单/专辑页签用更宽的卡片） */
.plaza-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(148px, 1fr)); gap: 14px; }
.plaza-grid.wide { grid-template-columns: repeat(auto-fill, minmax(172px, 1fr)); }
.pl-card {
  display: flex; flex-direction: column; gap: 7px; padding: 9px;
  background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm); text-decoration: none; min-width: 0;
  transition: transform 0.3s cubic-bezier(0.2, 0.8, 0.3, 1), box-shadow 0.3s ease, border-color 0.2s ease;
}
.pl-card:hover { transform: translateY(-3px); box-shadow: var(--shadow-md); border-color: rgba(0, 0, 0, 0.14); }
.pl-cover { position: relative; aspect-ratio: 1; border-radius: 9px; overflow: hidden; background: var(--surface-2); }
.pl-cover img { width: 100%; height: 100%; object-fit: cover; display: block; }
.pl-ph { position: absolute; inset: 0; display: grid; place-items: center; font-size: 30px; color: rgba(255, 255, 255, 0.6); }
.pl-name { font-size: 13px; font-weight: 600; color: var(--text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.pl-count { font-family: var(--font-mono); font-size: 11px; color: var(--text-faint); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.filters { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 20px; }
.chip { min-height: 34px; padding: 6px 13px; border: 1px solid var(--border); border-radius: var(--radius-pill); color: var(--text-muted); font-size: 13px; }
.chip:hover { color: var(--text); border-color: var(--border-strong); }
.chip[aria-pressed="true"] { background: var(--accent); border-color: var(--accent); color: var(--accent-text); font-weight: 700; }
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 70px 0 40px; text-align: center; }
.empty-state h3 { font-size: 17px; }
.empty-state p { color: var(--text-muted); }
.pager { margin-top: 24px; justify-content: center; }
</style>
