<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="440px"
    :close-on-click-modal="false"
    append-to-body
  >
    <el-form label-position="top">
      <el-form-item :label="label">
        <el-input
          v-model="reason"
          type="textarea"
          :rows="3"
          maxlength="200"
          show-word-limit
          :placeholder="placeholder"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="confirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: Boolean,
  title: { type: String, default: '请填写原因' },
  label: { type: String, default: '原因' },
  placeholder: { type: String, default: '请输入原因（必填）' },
  requireReason: { type: Boolean, default: true }
})
const emit = defineEmits(['update:modelValue', 'confirm'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const reason = ref('')
const loading = ref(false)

function confirm() {
  const val = reason.value.trim()
  if (props.requireReason && !val) {
    ElMessage.warning('请填写原因')
    return
  }
  loading.value = true
  emit('confirm', val)
  loading.value = false
  reason.value = ''
  visible.value = false
}
</script>
