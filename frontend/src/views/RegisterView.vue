<template>
  <div class="modal-overlay auth-modal" style="display: flex">
    <div class="modal-content">
      <a class="back-home" @click.prevent="$router.push('/')" href="#">← 返回首页</a>
      <div class="auth-wave" ref="waveEl" aria-hidden="true">
        <i v-for="n in 40" :key="n"></i>
      </div>
      <div class="auth-eyebrow">MUSIC DREAMER · 悦享音乐</div>

      <div class="modal-header" style="border: none; padding: 0 0 14px">
        <h3>创建账号</h3>
        <div class="app-slogan">注册后即可收藏、建歌单、上传作品</div>
      </div>

      <div class="modal-body">
        <div class="input-group">
          <label for="reg-username">用户名</label>
          <input id="reg-username" v-model="form.username" type="text" placeholder="3-50 位用户名" />
        </div>
        <div class="input-group">
          <label for="reg-nickname">昵称</label>
          <input id="reg-nickname" v-model="form.nickname" type="text" placeholder="2-50 位昵称，用于显示" />
        </div>
        <div class="input-group">
          <label for="reg-email">邮箱</label>
          <input id="reg-email" v-model="form.email" type="text" placeholder="用于激活与找回密码" />
        </div>
        <div class="input-group">
          <label for="reg-password">密码</label>
          <input id="reg-password" v-model="form.password" type="password" placeholder="至少 6 位" />
        </div>
        <div class="input-group">
          <label for="reg-confirm">确认密码</label>
          <input id="reg-confirm" v-model="confirm" type="password" placeholder="再输入一次"
            @keyup.enter="doRegister" />
        </div>

        <button class="btn btn-primary" style="width: 100%; margin-top: 4px" :disabled="loading" @click="doRegister">
          {{ loading ? '创建中...' : '注册' }}
        </button>

        <div style="text-align: center; margin-top: 14px; font-size: 13px; color: var(--text-muted)">
          已有账号？<router-link to="/login" style="color: var(--accent); font-weight: 600">直接登录</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { message } from '../utils/feedback'
import { gsap, prefersReduced } from '../utils/motion'
import api from '../api'

const router = useRouter()
const form = reactive({ username: '', nickname: '', email: '', password: '' })
const confirm = ref('')
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

async function doRegister() {
  if (!form.username || !form.nickname || !form.email || !form.password) return message.warning('请填写完整')
  if (form.password.length < 6) return message.warning('密码至少 6 位')
  if (form.password !== confirm.value) return message.warning('两次密码不一致')
  loading.value = true
  try {
    await api.register(form)
    message.success('注册成功，请登录')
    router.push('/login')
  } finally {
    loading.value = false
  }
}
</script>
