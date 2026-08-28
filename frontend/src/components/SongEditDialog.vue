<template>
  <el-dialog :model-value="modelValue" title="编辑歌曲信息" width="460px"
    @update:model-value="(v) => $emit('update:modelValue', v)">
    <el-form :model="form" label-width="80px">
      <el-form-item label="歌名"><el-input v-model="form.name" /></el-form-item>
      <el-form-item v-if="isAdmin" label="歌手名">
        <el-select v-model="form.singerName" filterable remote clearable autocomplete="off"
          :remote-method="searchSingerOpts"
          :loading="singerLoading" placeholder="搜索并选择已有歌手（清空则用账号昵称）" style="width:100%">
          <el-option v-for="sg in singerOpts" :key="sg.id" :label="sg.nickname" :value="sg.nickname" />
        </el-select>
      </el-form-item>
      <!-- bug67：专辑不再在歌曲编辑里维护（由专辑页统一管理）；bug68：语言维度下线 -->
      <el-form-item label="风格">
        <el-select v-model="form.style" style="width:180px">
          <el-option v-for="s in STYLES" :key="s" :label="s" :value="s" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
// 歌曲信息编辑弹窗：首页「全部内容」管理员态与后台「全量歌曲」共用
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { STYLES, listOf } from '../utils'
import { useUserStore } from '../stores/user'

const props = defineProps({
  modelValue: Boolean,
  song: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const userStore = useUserStore()
const isAdmin = userStore.role === 2
const form = ref({ name: '', style: '', singerName: '' })
const saving = ref(false)

/* bug21：歌手名只能从已有歌手里选（远程搜索），防手滑改出重名歌手 */
const singerOpts = ref([])
const singerLoading = ref(false)
async function searchSingerOpts(kw) {
  if (!kw || !kw.trim()) {
    singerOpts.value = []
    return
  }
  singerLoading.value = true
  try {
    singerOpts.value = listOf(await api.searchSingers({ keyword: kw.trim(), page: 1, size: 20 }))
    // 当前值来自详情（不在搜索结果里）：补一个选项，避免显示成裸 id/空
    if (form.value.singerName && !singerOpts.value.some((sg) => sg.nickname === form.value.singerName)) {
      singerOpts.value.unshift({ id: -1, nickname: form.value.singerName })
    }
  } finally {
    singerLoading.value = false
  }
}

watch(
  () => props.modelValue,
  async (v) => {
    if (!v || !props.song) return
    // 列表卡片不一定带全字段，以详情为准；详情字段为空时回退列表值（bug11：不再整单覆盖）
    form.value = {
      name: props.song.name || '',
      style: props.song.style || '',
      singerName: props.song.singerName || ''
    }
    try {
      const d = await api.songDetail(props.song.id)
      if (d) {
        form.value = {
          name: d.name || form.value.name,
          style: d.style || form.value.style,
          singerName: d.singerName || form.value.singerName
        }
      }
    } catch (e) { /* 列表字段兜底 */ }
    // 回填的歌手名先放进选项，打开下拉就能看到当前值
    if (form.value.singerName) {
      singerOpts.value = [{ id: -1, nickname: form.value.singerName }]
    } else {
      singerOpts.value = []
    }
  }
)

async function save() {
  saving.value = true
  try {
    await api.songEdit(props.song.id, { ...form.value })
    ElMessage.success('已保存')
    emit('update:modelValue', false)
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>
