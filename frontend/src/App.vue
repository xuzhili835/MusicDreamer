<template>
  <div v-if="isBare" class="app-bare">
    <router-view />
  </div>
  <template v-else>
    <!-- 外壳：上=侧栏+内容（+内嵌歌词栏），下=全宽播放条（压住整个窗口下缘，与画布一体） -->
    <div class="app-shell">
      <div class="main-container">
        <SideNav />
        <div class="app-main">
          <div class="content-row" :class="{ 'lyrics-on': lyrics.panelOpen }">
          <main class="content">
        <!-- 内容区顶部：搜索 / 用户资料 -->
        <div class="content-top">
          <div class="search-container">
            <svg class="search-icon" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
              stroke-width="2">
              <circle cx="11" cy="11" r="8" />
              <path d="M21 21l-4.35-4.35" />
            </svg>
            <input v-model="keyword" type="text" placeholder="搜索歌曲、歌手…" aria-label="搜索"
              @keyup.enter="doSearch" />
          </div>
          <button class="recog-btn" title="听歌识曲" aria-label="听歌识曲" @click="openRecognize">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" />
              <path d="M19 10v2a7 7 0 0 1-14 0v-2" />
              <line x1="12" y1="19" x2="12" y2="23" />
              <line x1="8" y1="23" x2="16" y2="23" />
            </svg>
          </button>

          <div class="top-user">
            <div v-if="user.isLogin" class="user-chip" title="退出登录" @click="logout">
              <span class="avatar"><span class="avatar-inner">{{ (user.nickname || user.userId || '?').toString().slice(0, 1) }}</span></span>
              <span class="who">
                <span class="nick">{{ user.displayName }}</span>
                <span class="role-tag" :class="roleClass">{{ roleLabel }}</span>
              </span>
            </div>
            <button v-else class="btn btn-primary" style="padding: 9px 22px" @click="$router.push('/login')">
              登录
            </button>
          </div>
        </div>

          <!-- 视图主体 -->
          <div class="content-body">
            <router-view />
          </div>
          </main>
          <!-- 内嵌歌词栏：内容区右缘的常驻列，「詞」开关控制展开 -->
          <LyricsPanel />
          </div>
        </div>
      </div>
      <PlayerBar />
      <TaskFloat />
    </div>
    <SingerApplyDialog v-model="applyVisible" />
    <SongRequestDialog v-model="reqVisible" :prefill="reqPrefill" @consume-prefill="reqPrefill = null" />
    <RecognizeDialog v-model="recogVisible" />
  </template>
</template>

<script setup>
import { onMounted, ref, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, confirm } from './utils/feedback'
import SideNav from './components/SideNav.vue'
import PlayerBar from './components/PlayerBar.vue'
import LyricsPanel from './components/LyricsPanel.vue'
import SingerApplyDialog from './components/SingerApplyDialog.vue'
import SongRequestDialog from './components/SongRequestDialog.vue'
import RecognizeDialog from './components/RecognizeDialog.vue'
import TaskFloat from './components/TaskFloat.vue'
import api from './api'
import { usePlayerStore } from './stores/player'
import { useUserStore } from './stores/user'
import { useTaskStore } from './stores/tasks'
import { useLyricsStore } from './stores/lyrics'

const route = useRoute()
const router = useRouter()
const player = usePlayerStore()
const user = useUserStore()
const taskStore = useTaskStore()
const lyrics = useLyricsStore()
const keyword = ref('')
const applyVisible = ref(false)
const reqVisible = ref(false)
const reqPrefill = ref(null)
const recogVisible = ref(false)

const isBare = computed(() => !!route.meta.bare)
const roleLabel = computed(() => ({ 0: '听众', 1: '音乐人', 2: '管理员' }[user.role] || '听众'))
const roleClass = computed(() => ({ 0: 'role-listener', 1: '', 2: 'role-admin' }[user.role] || 'role-listener'))

watch(
  () => route.query.keyword,
  (v) => {
    if (route.path === '/songs') keyword.value = v || ''
  },
  { immediate: true }
)

// 侧栏"歌手认证"/"求歌"入口（事件总线式简单通信）；外置识别未命中时携带歌名预填
if (typeof window !== 'undefined') {
  window.addEventListener('md-open-singer-apply', () => { applyVisible.value = true })
  window.addEventListener('md-open-song-request', (e) => {
    reqPrefill.value = (e && e.detail) || null
    reqVisible.value = true
  })
}

function openRecognize() {
  if (!user.isLogin) {
    message.warning('登录后才能识曲')
    router.push('/login')
    return
  }
  recogVisible.value = true
}

function doSearch() {
  const kw = (keyword.value || '').trim()
  if (!kw) {
    message.warning('请输入搜索内容')
    return
  }
  router.push({ path: '/songs', query: { keyword: kw } })
}

function logout() {
  confirm('确定退出登录吗？', { title: '提示' })
    .then(() => {
      // 后端注销尽力而为，不 await：本地立即登出跳转，避免后端慢/不可达时锁住界面
      api.logout().catch(() => {})
      user.clear()
      player.stop()
      message.success('已退出登录')
      router.push('/login')
    })
    .catch(() => {})
}

onMounted(() => {
  player.initAudio()
  if (user.isLogin) {
    user.refreshInfo()
    user.loadCollectIds()
    // 恢复后台下载跟踪：刷新/重开页面后继续轮询进行中的媒体任务
    taskStore.restore()
  }
})
</script>
