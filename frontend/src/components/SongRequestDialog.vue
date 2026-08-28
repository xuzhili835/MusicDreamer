<template>
  <el-dialog v-model="visible" title="求歌" width="520px" :close-on-click-modal="false" append-to-body>
    <el-alert
      title="曲库里没有想听的歌？留下歌名，管理员会搜索补充入库"
      type="info"
      :closable="false"
      style="margin-bottom: 14px"
    />
    <el-form :model="form" label-width="64px" @submit.prevent>
      <el-form-item label="歌名" required>
        <el-input v-model="form.title" maxlength="100" placeholder="歌名（必填）" @keyup.enter="submit" />
      </el-form-item>
      <el-form-item label="歌手">
        <el-input v-model="form.artist" maxlength="100" placeholder="歌手（选填，越准越容易找到）" />
      </el-form-item>
      <el-button type="primary" :loading="submitting" style="width: 100%" @click="submit">
        提交求歌
      </el-button>
    </el-form>

    <div v-if="mine.length" class="my-requests">
      <div class="my-requests-title">我的求歌</div>
      <div v-for="r in mine" :key="r.id" class="my-req-row">
        <span class="my-req-name">
          《{{ r.title }}》<template v-if="r.artist">- {{ r.artist }}</template>
        </span>
        <span class="my-req-time">{{ fmtTime(r.createdAt) }}</span>
        <el-tag size="small" :type="r.status === 0 ? 'warning' : (r.status === 1 ? 'success' : 'info')">
          {{ statusLabel(r.status) }}
        </el-tag>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { listOf, fmtTime } from '../utils'

const props = defineProps({ modelValue: Boolean, prefill: Object })
const emit = defineEmits(['update:modelValue', 'consume-prefill'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const form = reactive({ title: '', artist: '' })
const submitting = ref(false)
const mine = ref([])
let pendingSource = 0   // 识曲外置识别预填时为 1（识曲来源），提交后归位

watch(visible, (v) => { if (v) { applyPrefill(); loadMine() } })

/** 外置识别带歌名打开时自动填表（一次性，用完即清，来源标记为"识曲"）。 */
function applyPrefill() {
  if (props.prefill) {
    form.title = props.prefill.title || ''
    form.artist = props.prefill.artist || ''
    pendingSource = props.prefill.source ?? 0
    emit('consume-prefill')
  }
}

const statusLabel = (s) => ({ 0: '待处理', 1: '已入库', 2: '未通过' }[s] ?? s)

async function loadMine() {
  try {
    mine.value = listOf(await api.requestMine())
  } catch (e) {
    /* 未登录等：静默 */
  }
}

async function submit() {
  if (!form.title.trim()) return ElMessage.warning('请填写歌名')
  submitting.value = true
  try {
    await api.requestSubmit({ title: form.title.trim(), artist: form.artist.trim() || null, source: pendingSource })
    ElMessage.success('已提交，可在下方列表查看进度')
    form.title = ''
    form.artist = ''
    pendingSource = 0
    loadMine()
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.my-requests {
  margin-top: 18px;
  border-top: 1px solid var(--border, rgba(127, 127, 127, 0.18));
  padding-top: 12px;
}
.my-requests-title {
  font-size: 13px;
  color: var(--text-muted, #8a8378);
  margin-bottom: 8px;
}
.my-req-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
  font-size: 13.5px;
}
.my-req-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.my-req-time {
  font-size: 12px;
  color: var(--text-faint, #b0a89c);
  flex-shrink: 0;
}
</style>
