<template>
  <div class="library">
    <div class="page-head">
      <div>
        <div class="eyebrow">Library · 音乐库</div>
        <h1>我的音乐库</h1>
      </div>
      <button v-if="tab === 'playlists'" class="btn btn-primary" @click="createVisible = true">＋ 新建歌单</button>
      <button v-else-if="tab === 'albums' && userStore.isSinger" class="btn btn-primary" @click="albumCreateVisible = true">
        ＋ 发布专辑
      </button>
    </div>

    <!-- 类型切换：歌曲 / 歌单 / 专辑（收藏=别人的公开内容，创建=自己的作品） -->
    <div class="lib-tabs" role="tablist" aria-label="音乐库分类">
      <button v-for="t in TABS" :key="t.key" class="lib-tab" role="tab" :aria-selected="String(tab === t.key)"
        @click="switchTab(t.key)">
        {{ t.label }}
        <span v-if="t.count != null" class="tab-count">{{ t.count }}</span>
      </button>
    </div>

    <!-- 歌曲页签：收藏的歌曲 -->
    <template v-if="tab === 'songs'">
      <SongGrid v-if="songs.length" :songs="songs" @play="playSong" />
      <div v-if="!songs.length && !loading" class="empty-state">
        <h3>还没有收藏歌曲</h3>
        <p>在首页或搜索结果中点 ♥ 收藏喜欢的歌曲，它们会显示在这里</p>
        <button class="btn btn-primary" @click="$router.push('/')">去发现音乐</button>
      </div>
    </template>

    <!-- 歌单页签：我创建的 + 我收藏的（收藏为引用，随源变动） -->
    <template v-else-if="tab === 'playlists'">
      <div class="section-title">我创建的歌单<span class="grow"></span></div>
      <div class="pl-grid" ref="createdEl">
        <div v-for="p in created" :key="p.id" class="pl-card" @click="$router.push('/playlist/' + p.id)">
          <div class="pl-cover">
            <GenCover :name="p.name" :id="p.id" glyph="♪" />
          </div>
          <div class="pl-name">{{ p.name }}</div>
          <div class="pl-meta">{{ p.songCount ?? 0 }} 首 · {{ p.isPublic ? '公开' : '私有' }}</div>
        </div>
      </div>
      <div v-if="!created.length" class="empty-state slim">
        <h3>还没有创建歌单</h3>
        <p>把喜欢的歌编成一张歌单，随时整张播放。</p>
        <button class="btn btn-primary" @click="createVisible = true">新建歌单</button>
      </div>

      <div class="section-title">我收藏的歌单<span class="grow"></span></div>
      <div class="pl-grid" ref="collectedEl">
        <div v-for="p in collected" :key="p.id" class="pl-card collected" @click="$router.push('/playlist/' + p.id)">
          <div class="pl-cover">
            <GenCover :name="p.name" :id="p.id" glyph="♪" />
            <span class="fav-chip">♥ 收藏</span>
          </div>
          <div class="pl-name">{{ p.name }}</div>
          <div class="pl-meta">{{ p.creatorName ? p.creatorName + ' · ' : '' }}{{ p.songCount ?? 0 }} 首</div>
        </div>
      </div>
      <div v-if="!collected.length" class="empty-state slim">
        <h3>还没有收藏歌单</h3>
        <p>在广场点收藏，就能把别人的好歌单存到这里。</p>
      </div>
    </template>

    <!-- 专辑页签：歌手的我发布 + 大家的我收藏 -->
    <template v-else>
      <template v-if="userStore.isSinger">
        <div class="section-title">我发布的专辑<span class="grow"></span></div>
        <div class="pl-grid">
          <div v-for="a in myAlbums" :key="a.id" class="pl-card" @click="$router.push('/album/' + a.id)">
            <div class="pl-cover">
              <GenCover :name="a.name" :id="a.id" glyph="♫" />
            </div>
            <div class="pl-name">{{ a.name }}</div>
            <div class="pl-meta">{{ a.songCount ?? 0 }} 首 · {{ a.isPublic ? '已发布' : '未发布' }}</div>
          </div>
        </div>
        <div v-if="!myAlbums.length" class="empty-state slim">
          <h3>还没有发布专辑</h3>
          <p>把作品整理成专辑，让听众整张收藏。</p>
        </div>
      </template>

      <div class="section-title">我收藏的专辑<span class="grow"></span></div>
      <div class="pl-grid">
        <div v-for="a in favAlbums" :key="a.id" class="pl-card collected" @click="$router.push('/album/' + a.id)">
          <div class="pl-cover">
            <GenCover :name="a.name" :id="a.id" glyph="♫" />
            <span class="fav-chip">♥ 收藏</span>
          </div>
          <div class="pl-name">{{ a.name }}</div>
          <div class="pl-meta">{{ a.singerName || '歌手' }} · {{ a.songCount ?? 0 }} 首</div>
        </div>
      </div>
      <div v-if="!favAlbums.length" class="empty-state slim">
        <h3>还没有收藏专辑</h3>
        <p>在广场的专辑区点收藏，整张专辑存进音乐库。</p>
      </div>
    </template>

    <div class="spacer"></div>

    <el-dialog v-model="createVisible" title="新建歌单" width="420px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="歌单名称"><el-input v-model="form.name" placeholder="给歌单起个名字" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" placeholder="可选" /></el-form-item>
        <el-form-item label="公开"><el-switch v-model="form.isPublic" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="create">创建歌单</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="albumCreateVisible" title="发布专辑" width="420px">
      <el-form :model="albumForm" label-width="80px">
        <el-form-item label="专辑名称"><el-input v-model="albumForm.name" placeholder="给专辑起个名字" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="albumForm.description" type="textarea" placeholder="可选" /></el-form-item>
        <el-form-item label="发布"><el-switch v-model="albumForm.isPublic" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="albumCreateVisible = false">取消</el-button>
        <el-button type="primary" @click="createAlbum">创建专辑</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// 音乐库：一个入口收敛三类内容（歌曲/歌单/专辑）× 两种来源（我创建的/我收藏的）。
// 收藏的歌单/专辑是引用——源歌单增删歌曲、取消公开、删除都会实时反映。
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from '../utils/feedback'
import api from '../api'
import { useUserStore } from '../stores/user'
import { usePlayerStore } from '../stores/player'
import { splitMyPlaylists, pageOf } from '../utils'
import { revealStagger } from '../utils/motion'
import SongGrid from '../components/SongGrid.vue'
import GenCover from '../components/GenCover.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const playerStore = usePlayerStore()

const TABS = [
  { key: 'songs', label: '歌曲' },
  { key: 'playlists', label: '歌单' },
  { key: 'albums', label: '专辑' }
]
const tab = ref('songs')
const loading = ref(false)

/* ---------- 歌曲：收藏列表 ---------- */
const songs = ref([])
function playSong(index) {
  playerStore.playQueue(songs.value, index)
}
async function loadSongs() {
  loading.value = true
  try {
    const result = pageOf(await api.collectList({ page: 1, size: 100 }))
    songs.value = result.list.map((song) => ({ ...song, id: song.id != null ? song.id : song.songId }))
  } finally {
    loading.value = false
  }
}

/* ---------- 歌单：创建 + 收藏 ---------- */
const created = ref([])
const collected = ref([])
const createVisible = ref(false)
const form = ref({ name: '', description: '', isPublic: true })
const createdEl = ref(null)
const collectedEl = ref(null)

async function create() {
  if (!form.value.name) return message.warning('请输入歌单名称')
  const data = await api.createPlaylist(form.value)
  message.success('歌单已创建')
  window.dispatchEvent(new Event('md-playlists-changed'))
  createVisible.value = false
  form.value = { name: '', description: '', isPublic: true }
  // 直接跳进新歌单去选歌（bug3：新建后无处加歌）
  const newId = data && (data.playlistId || data.id)
  if (newId) {
    router.push('/playlist/' + newId)
    return
  }
  loadPlaylists()
}

async function loadPlaylists() {
  const s = splitMyPlaylists(await api.myPlaylists())
  created.value = s.created
  collected.value = s.collected
  await nextTick()
  if (createdEl.value) revealStagger(createdEl.value.querySelectorAll('.pl-card'), { y: 14, stagger: 0.04 })
  if (collectedEl.value) revealStagger(collectedEl.value.querySelectorAll('.pl-card'), { y: 14, stagger: 0.04 })
}

/* ---------- 专辑：歌手发布 + 收藏 ---------- */
const myAlbums = ref([])
const favAlbums = ref([])
const albumCreateVisible = ref(false)
const albumForm = ref({ name: '', description: '', isPublic: true })

async function createAlbum() {
  if (!albumForm.value.name) return message.warning('请输入专辑名称')
  await api.albumCreate(albumForm.value)
  message.success('专辑已创建')
  albumCreateVisible.value = false
  albumForm.value = { name: '', description: '', isPublic: true }
  loadAlbums()
}

async function loadAlbums() {
  try {
    if (userStore.isSinger) {
      myAlbums.value = (await api.myAlbums()) || []
    }
  } catch (e) { myAlbums.value = [] }
  try {
    favAlbums.value = (await api.favAlbums()) || []
  } catch (e) { favAlbums.value = [] }
}

/* ---------- 页签调度 ---------- */
const tabCount = computed(() => ({
  songs: songs.value.length || null,
  playlists: (created.value.length + collected.value.length) || null,
  albums: (myAlbums.value.length + favAlbums.value.length) || null
}))

function switchTab(key) {
  tab.value = key
  router.replace({ query: { ...route.query, tab: key } })
}

onMounted(() => {
  const q = route.query.tab
  tab.value = TABS.some((t) => t.key === q) ? q : 'songs'
  loadSongs()
  loadPlaylists()
  loadAlbums()
})
</script>

<style scoped>
.library { animation: v2-rise 0.5s cubic-bezier(0.2, 0.8, 0.3, 1) both; }

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 30px 0 18px;
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
.eyebrow::before { content: ''; width: 22px; height: 1px; background: var(--accent); }
h1 { font-family: var(--font-display); font-size: 26px; font-weight: 700; }

/* 类型页签 */
.lib-tabs {
  display: flex;
  gap: 8px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 20px;
}
.lib-tab {
  display: inline-flex;
  align-items: center;
  gap: 7px;
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
.lib-tab:hover { color: var(--text); background: var(--surface-2); }
.lib-tab[aria-selected="true"] {
  background: var(--accent);
  color: var(--accent-text);
  font-weight: 700;
}
.tab-count {
  font-family: var(--font-mono);
  font-size: 11px;
  opacity: 0.75;
}

/* 收藏卡：洋红 ♥ 角标，与自建卡区分 */
.pl-card.collected .pl-cover { position: relative; }
.fav-chip {
  position: absolute;
  left: 8px;
  bottom: 8px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.92);
  color: var(--magenta);
  font-size: 11px;
  font-weight: 700;
}

.empty-state.slim { padding: 34px 0 26px; }

.spacer { height: 40px; }
</style>
