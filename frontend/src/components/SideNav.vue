<template>
  <aside class="sidebar">
    <!-- 品牌区（preview-v2：纯文字 logo，橙 + 白双色） -->
    <div class="brand">
      <div class="brand-logo"></div>
      <div class="brand-text">
        <div class="brand-name">Music <em>Dreamer</em></div>
      </div>
    </div>

    <nav class="sidebar-nav" ref="navEl">
      <router-link v-for="n in libNavs" :key="n.to" :to="n.to" class="nav-item"
        :class="{ active: isActive(n.to) }" :title="n.desc">
        <span class="nav-icon" v-html="n.icon"></span>
        <span class="nav-text">{{ n.text }}</span>
      </router-link>

      <div v-if="user.isLogin" class="playlist-nav">
        <!-- 主区域：点击直达"我的歌单"页；右侧小箭头才展开下拉快选 -->
        <div class="playlist-row">
          <router-link to="/library" class="nav-item playlist-trigger"
            :class="{ active: isLibraryArea && route.path === '/library' }"
            title="我收藏的歌、创建/收藏的歌单与专辑">
            <span class="nav-icon">
              <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M3 7h18M3 12h18M3 17h12" />
              </svg>
            </span>
            <span class="nav-text">音乐库</span>
          </router-link>
          <button class="pl-expand" :class="{ open: playlistsOpen }" :aria-expanded="playlistsOpen"
            title="展开歌单快选" @click="playlistsOpen = !playlistsOpen">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="6 9 12 15 18 9" />
            </svg>
          </button>
        </div>
        <div v-show="playlistsOpen" class="playlist-children">
          <router-link v-for="p in playlists" :key="'c' + p.id" :to="'/playlist/' + p.id" class="playlist-link"
            :class="{ active: route.params.id && Number(route.params.id) === Number(p.id) }" :title="p.name">
            <span class="dot" :class="'c' + ((Number(p.id || 0) % 6) + 1)"></span>
            <span>{{ p.name }}</span>
          </router-link>
          <!-- 收藏的别人歌单：♥ 标记，和自建的区分开 -->
          <router-link v-for="p in collectedPl" :key="'f' + p.id" :to="'/playlist/' + p.id" class="playlist-link fav"
            :class="{ active: route.params.id && Number(route.params.id) === Number(p.id) }" :title="'收藏 · ' + p.name">
            <svg class="fav-heart" width="12" height="12" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 21s-8-4.5-8-10a4.5 4.5 0 0 1 8-3 4.5 4.5 0 0 1 8 3c0 5.5-8 10-8 10z" />
            </svg>
            <span>{{ p.name }}</span>
          </router-link>
        </div>
      </div>

      <template v-if="user.isLogin">
        <div class="nav-section-title">工具</div>
        <router-link v-if="user.isSinger" to="/upload" class="nav-item"
          :class="{ active: isActive('/upload') }" title="上传作品：本地文件或 B 站 / YouTube 链接导入，提交后进入审核">
          <span class="nav-icon">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
              <polyline points="17,8 12,3 7,8" />
              <line x1="12" y1="3" x2="12" y2="15" />
            </svg>
          </span>
          <span class="nav-text">上传中心</span>
        </router-link>
        <div v-else-if="user.role === 0" class="nav-item" @click="openApply"
          title="提交申请成为认证歌手，解锁作品上传">
          <span class="nav-icon">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" />
              <path d="M19 10v2a7 7 0 0 1-14 0v-2" />
              <line x1="12" y1="19" x2="12" y2="23" />
              <line x1="8" y1="23" x2="16" y2="23" />
            </svg>
          </span>
          <span class="nav-text">歌手认证</span>
        </div>
        <div v-if="user.isLogin" class="nav-item" @click="openSongRequest"
          title="没有找到想听的歌？录一段旋律，靠哼唱识别帮你找到它">
          <span class="nav-icon">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 18V5l12-2v13" />
              <circle cx="6" cy="18" r="3" />
              <circle cx="18" cy="16" r="3" />
            </svg>
          </span>
          <span class="nav-text">求歌</span>
        </div>
        <router-link v-if="user.isAdmin" to="/admin" class="nav-item"
          :class="{ active: isActive('/admin') }" title="审核歌曲/歌手、用户管理、举报处理与系统工具">
          <span class="nav-icon">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="3" />
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" />
            </svg>
          </span>
          <span class="nav-text">管理后台</span>
        </router-link>
        <router-link to="/settings" class="nav-item" :class="{ active: isActive('/settings') }"
          title="修改昵称、密码与账号资料">
          <span class="nav-icon">
            <!-- bug59：与其他导航项统一为线性 SVG 图标，替换 emoji ⚙️（各平台渲染不一致） -->
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
              <circle cx="12" cy="7" r="4" />
            </svg>
          </span>
          <span class="nav-text">个人设置</span>
        </router-link>
      </template>
    </nav>

    <div class="side-pl" v-if="!user.isLogin">
      <a href="#" @click.prevent="$router.push('/login')">登录后查看歌单</a>
    </div>
  </aside>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '../stores/user'
import api from '../api'
import { listOf, splitMyPlaylists } from '../utils'
import { revealStagger } from '../utils/motion'

const route = useRoute()
const user = useUserStore()
const playlists = ref([])
const collectedPl = ref([])
const playlistsOpen = ref(false)
const navEl = ref(null)
const isLibraryArea = computed(() => route.path === '/library' || route.path.startsWith('/playlist/') || route.path.startsWith('/album/'))

onMounted(() => {
  if (navEl.value) revealStagger(navEl.value.querySelectorAll('.nav-item'), { y: 10, stagger: 0.06 })
})

async function loadPlaylists() {
  if (!user.isLogin) return
  try {
    const s = splitMyPlaylists(await api.myPlaylists())
    playlists.value = s.created
    collectedPl.value = s.collected
  } catch (e) {
    playlists.value = []
    collectedPl.value = []
  }
}

function refreshPlaylists() {
  loadPlaylists()
}

watch(() => user.token, (token) => {
  if (token) loadPlaylists()
  else { playlists.value = []; collectedPl.value = [] }
}, { immediate: true })

watch(isLibraryArea, (active) => {
  if (active) playlistsOpen.value = true
}, { immediate: true })

window.addEventListener('md-playlists-changed', refreshPlaylists)
onBeforeUnmount(() => window.removeEventListener('md-playlists-changed', refreshPlaylists))


const libNavs = [
  {
    to: '/',
    text: '首页',
    desc: '每日精选、热门榜单与曲风分类，发现好歌',
    icon: '<svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 10.5 12 3l9 7.5V21h-6v-6H9v6H3z"/></svg>'
  },
  {
    to: '/songs',
    text: '全部歌曲',
    desc: '全站曲库广场，按曲风浏览、搜索与收藏',
    icon: '<svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>'
  },
  {
    to: '/recent',
    text: '最近播放',
    desc: '你听过的歌按时间倒序排列，随时接着听',
    icon: '<svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12,6 12,12 16,14"/></svg>'
  }
]

function isActive(path) {
  return route.path === path
}

function openApply() {
  window.dispatchEvent(new Event('md-open-singer-apply'))
}

function openSongRequest() {
  window.dispatchEvent(new Event('md-open-song-request'))
}
</script>
