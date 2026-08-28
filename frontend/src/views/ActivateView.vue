<template>
  <div class="modal-overlay auth-modal" style="display: flex">
    <div class="modal-content" style="text-align: center">
      <div class="auth-brand-line">
        <svg width="40" height="40" viewBox="0 0 24 24">
          <rect x="3" y="10" width="3" height="9" rx="1.5" fill="var(--accent)" />
          <rect x="8" y="5" width="3" height="18" rx="1.5" fill="var(--accent)" />
          <rect x="13" y="8" width="3" height="12" rx="1.5" fill="var(--accent)" />
          <rect x="18" y="3" width="3" height="20" rx="1.5" fill="var(--accent)" />
        </svg>
      </div>
      <h3 style="margin: 0 0 6px">{{ title }}</h3>
      <p class="muted" style="margin: 0 0 18px">{{ sub || '请稍候...' }}</p>
      <button class="btn btn-primary" @click="$router.push('/login')">去登录</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '../api'

const route = useRoute()
const title = ref('正在激活…')
const sub = ref('')

onMounted(async () => {
  const token = route.query.token
  if (!token) {
    title.value = '缺少激活令牌'
    sub.value = '请从邮件中的激活链接进入'
    return
  }
  try {
    await api.activate(token)
    title.value = '激活成功'
    sub.value = '现在可以使用该账号登录了'
  } catch (e) {
    title.value = '激活失败'
    sub.value = '令牌无效或已过期，请重新注册'
  }
})
</script>
