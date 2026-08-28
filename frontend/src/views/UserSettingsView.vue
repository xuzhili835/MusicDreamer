<template>
  <div class="container">
    <div class="page-header">
      <h1>个人设置</h1>
      <div class="page-subtitle">管理您的个人资料和账户设置</div>
    </div>

    <div class="settings-card">
      <h2>基本信息</h2>

      <div class="form-group">
        <label>用户名</label>
        <div class="readonly-field">{{ userStore.userId ? `用户${userStore.userId}` : '未登录' }}</div>
        <div class="field-help">用户名用于登录，注册后不可修改</div>
      </div>

      <div class="form-group">
        <label for="nickname-input">昵称</label>
        <input
          id="nickname-input"
          v-model="nicknameForm.nickname"
          type="text"
          placeholder="请输入您的昵称"
          :disabled="!userStore.isLogin"
        />
        <div class="field-help">昵称将在个人资料和评论中显示（2-50个字符）</div>
      </div>

      <button
        class="btn btn-primary"
        :disabled="!canSaveNickname || saving"
        @click="saveNickname"
      >
        {{ saving ? '保存中...' : '保存昵称' }}
      </button>
    </div>

    <div class="settings-card">
      <h2>账户操作</h2>
      <button class="btn btn-secondary" @click="pwdVisible = true">
        修改密码
      </button>
      <button class="btn btn-text" style="margin-left: 10px" @click="logout">
        退出登录
      </button>
    </div>

    <!-- bug66：我的歌曲（自上传中心迁移而来）：作品与审核状态统一在个人设置查看 -->
    <div class="settings-card" v-if="userStore.isSinger">
      <h2>我的歌曲</h2>
      <el-table :data="mySongs" v-loading="mySongsLoading" size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="歌名" min-width="140" />
        <el-table-column label="播放" width="70" align="center">
          <template #default="{ row }">{{ row.playCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="(SONG_STATUS[row.status] || {}).type">
              {{ (SONG_STATUS[row.status] || {}).label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="驳回/下架原因" min-width="140">
          <template #default="{ row }">
            <span class="muted">{{ row.rejectReason || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status === 1 && row.rejectReason" size="small" type="warning"
              @click="resubmit(row)">重新提交</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="field-help" style="margin-top: 8px">
        上传的作品与审核状态都在这里；提交新作品请前往上传中心
      </div>
    </div>

    <!-- bug82：我的求歌记录；bug80：管理员处理后在这里可见，新变化标「新」 -->
    <div class="settings-card">
      <h2>我的求歌</h2>
      <el-table :data="myRequests" v-loading="myRequestsLoading" size="small">
        <el-table-column label="歌名" min-width="150">
          <template #default="{ row }">
            <span>{{ row.title }}<template v-if="row.artist"> - {{ row.artist }}</template></span>
            <el-tag v-if="isNewReq(row)" size="small" type="danger" effect="plain" style="margin-left: 6px">新</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag size="small" :title="row.rejectReason || ''"
              :type="row.status === 1 ? 'success' : (row.status === 2 ? 'info' : 'warning')">
              {{ reqLabel(row) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="field-help" style="margin-top: 8px">
        管理员处理后会在这里更新；已入库显示歌曲编号，被拒显示原因（悬停查看）
      </div>
    </div>

    <!-- 修改密码弹窗：旧密码核验 + 新密码二次确认（bug1，替代不存在的 /change-password 路由） -->
    <el-dialog v-model="pwdVisible" title="修改密码" width="420px" append-to-body>
      <div class="form-group">
        <label for="old-password">当前密码</label>
        <input id="old-password" v-model="pwdForm.oldPassword" type="password" placeholder="输入当前密码" />
      </div>
      <div class="form-group">
        <label for="new-password">新密码</label>
        <input id="new-password" v-model="pwdForm.newPassword" type="password" placeholder="6-64 位" />
      </div>
      <div class="form-group">
        <label for="confirm-password">确认新密码</label>
        <input id="confirm-password" v-model="pwdForm.confirm" type="password" placeholder="再次输入新密码"
          @keyup.enter="changePassword" />
      </div>
      <template #footer>
        <button class="btn btn-secondary" @click="pwdVisible = false">取消</button>
        <button class="btn btn-primary" style="margin-left: 10px" :disabled="pwdSaving" @click="changePassword">
          {{ pwdSaving ? '提交中...' : '确认修改' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { message } from '../utils/feedback'
import { listOf, SONG_STATUS } from '../utils'
import api from '../api'

const router = useRouter()
const userStore = useUserStore()

const nicknameForm = reactive({
  nickname: userStore.nickname || ''
})

const saving = ref(false)

const pwdVisible = ref(false)
const pwdSaving = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })

const canSaveNickname = computed(() => {
  return userStore.isLogin &&
         nicknameForm.nickname.trim() &&
         nicknameForm.nickname !== userStore.nickname &&
         nicknameForm.nickname.length >= 2 &&
         nicknameForm.nickname.length <= 50
})

onMounted(() => {
  if (!userStore.isLogin) {
    message.warning('请先登录')
    router.push('/login')
  } else {
    loadMySongs()
    loadMyRequests()
  }
})

/* bug66：我的歌曲（自上传中心迁移） */
const mySongs = ref([])
const mySongsLoading = ref(false)

async function loadMySongs() {
  if (!userStore.isSinger) return
  mySongsLoading.value = true
  try {
    mySongs.value = listOf(await api.songMine({ page: 1, size: 50 }))
  } finally {
    mySongsLoading.value = false
  }
}

async function resubmit(row) {
  await api.songResubmit(row.id)
  message.success('已重新提交审核')
  loadMySongs()
}

/* bug82：我的求歌记录；bug80：以 localStorage 水位标记未读的处理结果 */
const myRequests = ref([])
const myRequestsLoading = ref(false)
const REQ_SEEN_KEY = 'md-req-seen'
let reqSeenAt = Number(localStorage.getItem(REQ_SEEN_KEY) || 0)

async function loadMyRequests() {
  myRequestsLoading.value = true
  try {
    myRequests.value = listOf(await api.requestMine())
    // 本次渲染沿用进入页面前的水位；随后抬到最新——本次标「新」，下次刷新即消
    const maxHandled = myRequests.value.reduce((m, r) => Math.max(m, tsOf(r.handledAt)), 0)
    localStorage.setItem(REQ_SEEN_KEY, String(Math.max(reqSeenAt, maxHandled)))
  } finally {
    myRequestsLoading.value = false
  }
}

function tsOf(v) {
  const t = Date.parse(v || '')
  return Number.isFinite(t) ? t : 0
}

function isNewReq(r) {
  return r.status !== 0 && tsOf(r.handledAt) > reqSeenAt
}

function reqLabel(r) {
  if (r.status === 1) return r.resultSongId ? `已入库 #${r.resultSongId}` : '已入库'
  if (r.status === 2) return '已拒绝'
  return '待处理'
}

function fmtTime(v) {
  const t = new Date(v)
  return Number.isNaN(t.getTime()) ? '-' : t.toLocaleString('zh-CN', { hour12: false })
}

async function saveNickname() {
  if (!canSaveNickname.value) return

  saving.value = true
  try {
    await api.updateUserInfo({ nickname: nicknameForm.nickname.trim() })

    // 更新本地存储
    userStore.nickname = nicknameForm.nickname.trim()
    userStore.persist()

    message.success('昵称更新成功')
  } catch (error) {
    message.error('昵称更新失败：' + (error.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

async function changePassword() {
  if (!pwdForm.oldPassword) return message.warning('请输入当前密码')
  if (pwdForm.newPassword.length < 6 || pwdForm.newPassword.length > 64) {
    return message.warning('新密码需 6-64 位')
  }
  if (pwdForm.newPassword !== pwdForm.confirm) return message.warning('两次输入的新密码不一致')
  pwdSaving.value = true
  try {
    await api.changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    message.success('密码修改成功，下次登录请使用新密码')
    pwdVisible.value = false
    Object.assign(pwdForm, { oldPassword: '', newPassword: '', confirm: '' })
  } finally {
    pwdSaving.value = false
  }
}

function logout() {
  userStore.clear()
  message.success('已退出登录')
  router.push('/')
}
</script>

<style scoped>
.container {
  max-width: 600px;
  margin: 0 auto;
  padding: 20px;
}

.page-header {
  margin-bottom: 30px;
}

.page-header h1 {
  font-size: 24px;
  font-weight: 600;
  margin: 0 0 8px 0;
}

.page-subtitle {
  color: var(--text-muted);
  font-size: 14px;
}

.settings-card {
  background: var(--surface);
  border-radius: var(--radius-md);
  padding: 24px;
  margin-bottom: 20px;
  border: 1px solid var(--border);
}

.settings-card h2 {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 20px 0;
  color: var(--text);
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  font-weight: 500;
  margin-bottom: 6px;
  color: var(--text);
}

.form-group input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  font-size: 14px;
  transition: border-color 0.2s ease;
}

.form-group input:focus {
  outline: none;
  border-color: var(--accent);
}

.form-group input:disabled {
  background-color: var(--surface-2);
  color: var(--text-muted);
}

.readonly-field {
  padding: 10px 12px;
  background-color: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  font-size: 14px;
  color: var(--text-muted);
}

.field-help {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
}

.muted {
  color: var(--text-muted);
  font-size: 12px;
}

.btn {
  padding: 10px 20px;
  border-radius: var(--radius-sm);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-primary {
  background: var(--accent);
  color: var(--accent-text);
  border: none;
}

.btn-primary:hover:not(:disabled) {
  background: var(--accent-dark);
}

.btn-secondary {
  background: var(--surface-2);
  color: var(--text);
  border: 1px solid var(--border);
}

.btn-secondary:hover {
  background: var(--surface-3);
}

.btn-text {
  background: transparent;
  color: var(--text-muted);
  border: none;
}

.btn-text:hover {
  color: var(--text);
}
</style>