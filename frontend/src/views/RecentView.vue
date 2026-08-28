<template>
  <div class="recent">
    <header class="content-header">
      <div>
        <div class="eyebrow">History</div>
        <h1>最近播放</h1>
      </div>
      <span v-if="total > 0" class="stat">累计播放 {{ total }} 次</span>
    </header>

    <SongGrid v-if="songs.length" :songs="songs" @play="play" />

    <div v-if="!songs.length && !loading" class="empty-state">
      <div class="empty-icon">
        <svg width="64" height="64" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="var(--accent)" stroke-width="1.5" />
          <polyline points="12,6 12,12 16,14" stroke="var(--accent)" stroke-width="1.5" />
        </svg>
      </div>
      <h3>还没有播放记录</h3>
      <p>去首页听几首歌，这里就会出现你的足迹</p>
      <button class="btn btn-primary" @click="$router.push('/')">去首页</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '../api'
import { usePlayerStore } from '../stores/player'
import SongGrid from '../components/SongGrid.vue'
import { listOf } from '../utils'

const playerStore = usePlayerStore()
const songs = ref([])
const total = ref(0)
const loading = ref(false)

function play(index) {
  playerStore.playQueue(songs.value, index)
}

onMounted(async () => {
  loading.value = true
  try {
    // bug77：recent 接口字段是 songId/singer——映射成 SongGrid/播放器消费的 id/singerName，
    // 否则 id 为 undefined → 播放报错、封面主色取不到（卡片底色不同步）
    songs.value = listOf(await api.recentPlays(50)).map((r) => ({
      ...r,
      id: r.songId,
      singerName: r.singer || ''
    }))
    // 播放明细接口自带累计总数（播完一次记一条），失败静默
    try {
      const h = await api.playHistory({ page: 1, size: 1 })
      total.value = Number((h && h.total) || 0)
    } catch (e) { /* ignore */ }
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.recent {
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
  font-size: 26px;
  letter-spacing: -0.01em;
}

.stat {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 12px;
  padding: 5px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-pill);
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
  margin-top: 12px;
}
.empty-state p {
  font-size: 13.5px;
  color: var(--text-muted);
}
.empty-state .btn {
  margin-top: 14px;
}
</style>
