import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'

const routes = [
  { path: '/', name: 'home', component: () => import('../views/HomeView.vue') },
  { path: '/login', name: 'login', meta: { bare: true }, component: () => import('../views/LoginView.vue') },
  { path: '/register', name: 'register', meta: { bare: true }, component: () => import('../views/RegisterView.vue') },
  { path: '/activate', name: 'activate', meta: { bare: true }, component: () => import('../views/ActivateView.vue') },
  { path: '/songs', name: 'songs', component: () => import('../views/BrowseSongsView.vue') },
  { path: '/search', redirect: (to) => ({ path: '/songs', query: to.query }) },
  { path: '/playlist/:id', name: 'playlist-detail', component: () => import('../views/PlaylistDetailView.vue') },
  { path: '/album/:id', name: 'album-detail', component: () => import('../views/AlbumDetailView.vue') },
  // 音乐库收敛了旧"我的收藏/我的歌单"两个入口
  { path: '/library', name: 'library', component: () => import('../views/LibraryView.vue'), meta: { requiresAuth: true } },
  { path: '/collection', redirect: '/library?tab=songs' },
  { path: '/my/playlists', redirect: '/library?tab=playlists' },
  { path: '/recent', name: 'recent', component: () => import('../views/RecentView.vue'), meta: { requiresAuth: true } },
  { path: '/upload', name: 'upload', component: () => import('../views/UploadView.vue'), meta: { requiresAuth: true, minRole: 1 } },
  { path: '/settings', name: 'settings', component: () => import('../views/UserSettingsView.vue'), meta: { requiresAuth: true } },
  { path: '/admin', name: 'admin', component: () => import('../views/AdminView.vue'), meta: { requiresAuth: true, minRole: 2 } },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  const user = useUserStore()
  if (to.meta.requiresAuth && !user.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.minRole !== undefined && user.role < to.meta.minRole) {
    ElMessage.error('无权访问该页面')
    return { path: '/' }
  }
  return true
})

export default router
