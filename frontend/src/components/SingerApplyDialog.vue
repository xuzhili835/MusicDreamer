<template>
  <el-dialog v-model="visible" title="申请歌手认证" width="480px" :close-on-click-modal="false" append-to-body>
    <el-alert
      title="通过认证后即可上传歌曲、使用链接导入等歌手功能"
      type="info"
      :closable="false"
      style="margin-bottom: 14px"
    />
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="真实姓名" prop="realName">
        <el-input v-model="form.realName" maxlength="50" placeholder="请输入真实姓名" />
      </el-form-item>
      <el-form-item label="身份证号" prop="idCard">
        <el-input v-model="form.idCard" maxlength="18" placeholder="用于实名认证" />
      </el-form-item>
      <el-form-item label="艺人声明" prop="artistStatement">
        <el-input
          v-model="form.artistStatement"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          placeholder="介绍一下你的音乐风格、代表作品等"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit">提交申请</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { useUserStore } from '../stores/user'

const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue'])

const user = useUserStore()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const formRef = ref(null)
const loading = ref(false)
const form = reactive({
  realName: '',
  idCard: '',
  artistStatement: ''
})

const rules = {
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  idCard: [
    { required: true, message: '请输入身份证号', trigger: 'blur' },
    { pattern: /^\d{17}[\dXx]$/, message: '身份证号格式不正确', trigger: 'blur' }
  ]
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  loading.value = true
  try {
    await api.singerApply({
      realName: form.realName.trim(),
      idCard: form.idCard.trim(),
      artistStatement: form.artistStatement.trim() || null
    })
    ElMessage.success('认证申请已提交，请等待管理员审核')
    user.singerStatus = 1
    user.persist()
    visible.value = false
    form.realName = ''
    form.idCard = ''
    form.artistStatement = ''
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}
</script>
