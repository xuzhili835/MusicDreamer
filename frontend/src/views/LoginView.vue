<template>
  <div class="modal-overlay auth-modal" style="display: flex">
    <div class="modal-content">
      <a class="back-home" @click.prevent="$router.push('/')" href="#">← 返回首页</a>
      <div class="auth-wave" ref="waveEl" aria-hidden="true">
        <i v-for="n in 40" :key="n"></i>
      </div>
      <div class="auth-eyebrow">MUSIC DREAMER · 悦享音乐</div>

      <div class="modal-header" style="border: none; padding: 0 0 14px">
        <h3>欢迎回来</h3>
        <div class="app-slogan">登录，继续你的歌单与收藏</div>
      </div>

      <div class="modal-body">
        <div class="input-group">
          <label for="login-username">用户名</label>
          <input id="login-username" v-model="form.username" type="text" placeholder="输入用户名"
            @keyup.enter="doLogin" />
        </div>
        <div class="input-group">
          <label for="login-password">密码</label>
          <input id="login-password" v-model="form.password" type="password" placeholder="输入密码"
            @keyup.enter="doLogin" />
        </div>
        <label class="download-option" style="margin: 4px 0 10px">
          <input v-model="form.remember" type="checkbox" />
          <span>记住我（7 天内免登录）</span>
        </label>

        <button class="btn btn-primary" style="width: 100%; margin-top: 4px" :disabled="loading" @click="doLogin">
          {{ loading ? '登录中...' : '登录' }}
        </button>

        <div style="text-align: center; margin-top: 14px; font-size: 13px; color: var(--text-muted)">
          还没有账号？<router-link to="/register" style="color: var(--accent); font-weight: 600">创建账号</router-link>
        </div>

        <details style="margin-top: 12px">
          <summary style="font-size: 12px; color: var(--text-faint); cursor: pointer; user-select: none">
            忘记密码？
          </summary>
          <div style="margin-top: 10px">
            <div class="input-group">
              <label>用户名</label>
              <input v-model="reset.username" type="text" placeholder="账号用户名" />
            </div>
            <div class="input-group">
              <label>注册邮箱</label>
              <input v-model="reset.email" type="text" placeholder="you@example.com" />
            </div>
            <div style="display: flex; gap: 8px; margin-top: 4px">
              <input v-model="reset.newPassword" type="password" placeholder="新密码（6-64 位）" style="flex: 1" @keyup.enter="doReset" />
              <button class="btn btn-primary" style="padding: 5px 12px; font-size: 12px" @click="doReset">重置</button>
            </div>
            <div style="font-size: 11px; color: var(--text-faint); margin-top: 6px">用户名与注册邮箱匹配即可直接重置</div>
          </div>
        </details>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from '../utils/feedback'
import { gsap, prefersReduced } from '../utils/motion'
import api from '../api'
import { useUserStore } from '../stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const form = reactive({ username: '', password: '', remember: false })
const reset = reactive({ username: '', email: '', newPassword: '' })
const loading = ref(false)
const waveEl = ref(null)
let waveTl = null

onMounted(() => {
  if (!waveEl.value || prefersReduced) return
  const bars = waveEl.value.querySelectorAll('i')
  waveTl = gsap.timeline()
  bars.forEach((b) => {
    waveTl.to(b, {
      scaleY: 0.3 + Math.random() * 0.7,
      duration: 0.6 + Math.random() * 0.6,
      repeat: -1,
      yoyo: true,
      ease: 'sine.inOut'
    }, Math.random() * 0.8)
  })
})
onBeforeUnmount(() => { if (waveTl) waveTl.kill() })

async function doLogin() {
  if (!form.username || !form.password) return message.warning('请输入用户名和密码')
  loading.value = true
  try {
    const data = await api.login(form)
    userStore.setLogin(data)
    userStore.refreshInfo()
    message.success('登录成功')
    router.push(route.query.redirect || '/')
  } finally {
    loading.value = false
  }
}

async function doReset() {
  if (!reset.username || !reset.email || !reset.newPassword) {
    return message.warning('请填写用户名、注册邮箱和新密码')
  }
  if (reset.newPassword.length < 6) return message.warning('新密码至少 6 位')
  await api.resetPassword(reset)
  message.success('密码已重置，请使用新密码登录')
}
</script>
