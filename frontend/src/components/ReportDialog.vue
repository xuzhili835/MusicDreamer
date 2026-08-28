<template>
  <el-dialog v-model="visible" title="举报" width="460px" :close-on-click-modal="false" append-to-body>
    <el-alert
      v-if="targetLabel"
      :title="`举报对象：${targetLabel}`"
      type="info"
      :closable="false"
      style="margin-bottom: 14px"
    />
    <el-form label-position="top">
      <el-form-item label="举报原因" required>
        <el-radio-group v-model="reason">
          <el-radio :value="1">侵权</el-radio>
          <el-radio :value="2">违规内容</el-radio>
          <el-radio :value="3">垃圾信息</el-radio>
          <el-radio :value="4">其他</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="详细描述">
        <el-input
          v-model="description"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="补充说明（选填）"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="danger" :loading="loading" @click="submit">提交举报</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const props = defineProps({
  modelValue: Boolean,
  // 1歌曲 2评论 3歌单 4动态
  targetType: { type: Number, default: 1 },
  targetId: { type: [Number, String], default: null },
  targetLabel: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const reason = ref(1)
const description = ref('')
const loading = ref(false)

async function submit() {
  if (!props.targetId) return
  loading.value = true
  try {
    await api.reportSubmit({
      targetType: props.targetType,
      targetId: Number(props.targetId),
      reason: reason.value,
      description: description.value.trim() || null
    })
    ElMessage.success('举报已提交，管理员会尽快处理')
    visible.value = false
    description.value = ''
    reason.value = 1
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}
</script>
