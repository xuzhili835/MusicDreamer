<template>
  <div class="upload">
    <div class="page-head">
      <div>
        <div class="eyebrow">Studio · 上传中心</div>
        <h1>上传中心</h1>
      </div>
    </div>
    <el-tabs v-model="tab">
      <!-- 本地文件上传 -->
      <el-tab-pane label="本地上传" name="file">
        <el-card>
          <el-form :model="form" label-width="100px" class="upload-form">
            <el-form-item label="音频文件">
              <el-upload :auto-upload="false" :limit="1" :on-change="onFile" accept=".mp3,.flac,.aac"
                :file-list="fileList">
                <el-button type="primary">选择音频（MP3/FLAC/AAC ≤ 50MB）</el-button>
              </el-upload>
            </el-form-item>
            <el-form-item label="歌名"><el-input v-model="form.name" style="width:320px" /></el-form-item>
            <el-form-item v-if="isAdmin" label="歌手名">
              <div class="singer-row">
                <el-select v-model="form.singerName" filterable remote clearable autocomplete="off"
                  :remote-method="searchSingerOpts"
                  :loading="singerLoading" placeholder="搜索并选择已有歌手（留空则用你的昵称）"
                  style="width:320px">
                  <el-option v-for="sg in singerOpts" :key="sg.id" :label="sg.nickname" :value="sg.nickname" />
                </el-select>
                <a class="new-singer" href="#" @click.prevent="openSingerDialog('form')">没找到？新建歌手账号</a>
              </div>
            </el-form-item>
            <el-form-item label="专辑"><el-input v-model="form.album" style="width:320px" /></el-form-item>
            <el-form-item label="风格" required>
              <el-select v-model="form.style" style="width:180px" placeholder="必选">
                <el-option v-for="s in STYLES" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
            <!-- bug68：语言维度前端下线（后端字段保留） -->
            <el-form-item label="封面图片">
              <el-upload :auto-upload="false" :limit="1" :on-change="onCover" accept="image/*" :file-list="coverList">
                <el-button>选择封面（可选，提升列表观感）</el-button>
              </el-upload>
            </el-form-item>
            <el-form-item label="歌词文件">
              <el-upload :auto-upload="false" :limit="1" :on-change="onLyric" accept=".lrc,.txt" :file-list="lyricList">
                <el-button>选择歌词（可选，LRC 带时间轴）</el-button>
              </el-upload>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="uploading" @click="submitFile">提交（进入审核）</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <!-- 链接导入（媒体任务） -->
      <el-tab-pane label="链接导入" name="link">
        <el-card>
          <el-form label-width="100px">
            <el-form-item label="来源链接">
              <div class="url-row">
                <el-input v-model="link.url" placeholder="支持 B 站 / YouTube 视频链接" style="width:460px" />
                <!-- bug74：提交前先解析，立刻看到智能裁剪结果，不用等审核后才发现 -->
                <el-button :loading="previewing" :disabled="!link.url" @click="preview">解析预览</el-button>
              </div>
            </el-form-item>
            <div v-if="previewInfo" class="import-preview preview-box">
              <div>视频标题：{{ previewInfo.rawTitle || '—' }}</div>
              <div>
                智能裁剪后歌名：<b>「{{ previewInfo.title || '—' }}」</b>（在上方「歌名」填写可覆盖）
                <template v-if="previewInfo.duration"> · 时长 {{ fmtDur(previewInfo.duration) }}</template>
                <template v-if="previewInfo.uploader"> · UP主 {{ previewInfo.uploader }}</template>
              </div>
            </div>
            <el-form-item label="歌名">
              <el-input v-model="link.title" style="width:320px" placeholder="留空则自动从视频标题智能提取" />
            </el-form-item>
            <el-form-item v-if="isAdmin" label="歌手名">
              <div class="singer-row">
                <el-select v-model="link.singerName" filterable remote clearable autocomplete="off"
                  :remote-method="searchSingerOpts"
                  :loading="singerLoading" placeholder="搜索并选择已有歌手（留空则用你的昵称）" style="width:320px">
                  <el-option v-for="sg in singerOpts" :key="sg.id" :label="sg.nickname" :value="sg.nickname" />
                </el-select>
                <!-- bug75：搜不到的歌手由管理员一键建号（拼音用户名/admin123） -->
                <a class="new-singer" href="#" @click.prevent="openSingerDialog('link')">没找到？新建歌手账号</a>
              </div>
            </el-form-item>
            <el-form-item label="风格" required>
              <el-select v-model="link.style" style="width:180px" placeholder="必选">
                <el-option v-for="s in STYLES" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
            <el-form-item label="选项">
              <el-checkbox v-model="link.wantTranscribe">自动获取歌词（在线词库匹配 → 视频字幕 → 本地 AI 转写）</el-checkbox>
            </el-form-item>
            <!-- bug16：导入前就告诉歌手这首歌将以什么身份/分类展示 -->
            <div class="import-preview">
              导入后将以「{{ displaySinger }}」作为歌手展示<template v-if="link.style">，归入「{{ link.style }}」</template>，提交后进入审核
            </div>
            <el-form-item>
              <el-button type="primary" :disabled="!link.url || running" @click="startDownload">开始导入</el-button>
              <el-button v-if="running" type="danger" plain @click="cancel">取消任务</el-button>
            </el-form-item>
          </el-form>

          <div v-if="task" class="task-box">
            <div class="task-line">
              <el-tag :type="taskTag">{{ task.status }}</el-tag>
              <span class="stage">{{ task.stage || '排队中…' }}</span>
            </div>
            <el-progress :percentage="Number(task.progress) || 0" :status="task.status === 'SUCCESS' ? 'success' : undefined" />
            <!-- bug71：讲清进度条语义——入库即可审核，后置处理不是审核进度 -->
            <div class="task-hint">音频入库后即进入管理员审核；进度条后半段是响度/歌词等后置处理，不影响审核结果</div>
            <div v-if="task.error" class="task-error">错误：{{ task.error }}</div>
            <el-button v-if="task.status === 'SUCCESS' && task.musicId" size="small" type="success"
              @click="afterImport">
              {{ isAdmin ? '已入库，去首页看看' : '已提交审核，去个人设置查看' }}
            </el-button>
          </div>
        </el-card>
      </el-tab-pane>

      <!-- bug66：我的歌曲已迁往「个人设置」，上传中心只保留上传入口 -->
    </el-tabs>

    <!-- bug75：管理员指定的歌手不存在时，一键创建歌手账号（拼音用户名，初始密码 admin123） -->
    <el-dialog v-model="singerDialog.visible" title="新建歌手账号" width="420px" append-to-body>
      <el-form label-width="72px" @submit.prevent>
        <el-form-item label="歌手名">
          <el-input v-model="singerDialog.name" maxlength="50" placeholder="歌手展示名（昵称）" />
        </el-form-item>
      </el-form>
      <div class="dlg-tip">
        用户名由歌手名拼音自动生成（重名加序号），初始密码统一为 admin123——创建后请通知本人尽快登录修改。
        同名歌手账号已存在时直接复用，不会重复创建。
      </div>
      <template #footer>
        <el-button @click="singerDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="singerDialog.creating" @click="createSinger">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from '../utils/feedback'
import api from '../api'
import { listOf, STYLES, isTaskTerminal } from '../utils'
import { useUserStore } from '../stores/user'
import { useTaskStore } from '../stores/tasks'

const router = useRouter()
const userStore = useUserStore()
const isAdmin = userStore.role === 2
const tab = ref('file')
const form = ref({ name: '', album: '', style: '', singerName: '' })
const fileList = ref([])
const picked = ref(null)
const coverList = ref([])
const lyricList = ref([])
const pickedCover = ref(null)
const pickedLyric = ref(null)
const uploading = ref(false)

// 链接导入：歌名可自定义（留空智能提取）、风格必选（bug12/16），
// 歌词链路已合并为一个开关（词库→字幕→转写），不再单列"下载字幕"
const link = ref({ url: '', title: '', singerName: '', style: '', wantTranscribe: true })
const task = ref(null)
let singerApplied = false
const taskStore = useTaskStore()
// 任务轮询在全局 store（切页后仍跟踪，右下角悬浮指示器可见），此处只引用展示
const running = computed(() => !!task.value && !isTaskTerminal(task.value.status))

// bug16：导入前预览"将以什么昵称展示"
const displaySinger = computed(() => {
  const n = (link.value.singerName || userStore.nickname || '').trim()
  return n || '你的昵称'
})

/* bug21：管理员指定歌手时只能从已有歌手里选（远程搜索，防手滑造重名歌手） */
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
  } finally {
    singerLoading.value = false
  }
}

// 任务结束时回填歌手名（管理员导入他人作品的场景）
watch(() => task.value && task.value.status, (status) => {
  if (!status || !isTaskTerminal(status)) return
  const name = (link.value.singerName || '').trim()
  if (status === 'SUCCESS' && task.value.musicId && name && !singerApplied) {
    singerApplied = true
    api.songEdit(task.value.musicId, { singerName: name })
      .then(() => message.success(`歌手名已回填：${name}`))
      .catch(() => message.warning('歌手名回填失败，可在首页编辑歌曲信息补填'))
  }
})

function onFile(file) {
  if (file.raw && file.raw.size > 50 * 1024 * 1024) {
    message.error('文件超过 50MB 限制')
    fileList.value = []
    return
  }
  picked.value = file.raw
  if (!form.value.name) form.value.name = file.name.replace(/\.(mp3|flac|aac)$/i, '')
}

function onCover(file) {
  pickedCover.value = file.raw || null
}

function onLyric(file) {
  pickedLyric.value = file.raw || null
}

// 浏览器端读音频元数据时长（读不到回退 0，不打断提交）
function readDuration(file) {
  return new Promise((resolve) => {
    try {
      const url = URL.createObjectURL(file)
      const a = new Audio()
      a.preload = 'metadata'
      a.onloadedmetadata = () => { URL.revokeObjectURL(url); resolve(Math.round(a.duration) || 0) }
      a.onerror = () => { URL.revokeObjectURL(url); resolve(0) }
      a.src = url
    } catch (e) {
      resolve(0)
    }
  })
}

async function submitFile() {
  if (!picked.value) return message.warning('请先选择音频文件')
  if (!form.value.name) return message.warning('请填写歌名')
  if (!form.value.style) return message.warning('请选择风格（必选项）')
  uploading.value = true
  try {
    const up = await api.uploadAudio(picked.value)
    // 封面/歌词可选：选了才上传，地址随歌曲一起提交
    let coverUrl = null
    let lyricUrl = null
    if (pickedCover.value) coverUrl = (await api.uploadImage(pickedCover.value)).url
    if (pickedLyric.value) lyricUrl = (await api.uploadLyric(pickedLyric.value)).url
    await api.songSubmit({
      ...form.value,
      fileUrl: up.url,
      coverUrl,
      lyricUrl,
      fileFormat: (picked.value.name.split('.').pop() || 'mp3').toUpperCase(),
      duration: await readDuration(picked.value)
    })
    message.success('已提交，等待管理员审核')
    form.value = { name: '', album: '', style: '', singerName: '' }
    fileList.value = []
    picked.value = null
    coverList.value = []
    pickedCover.value = null
    lyricList.value = []
    pickedLyric.value = null
  } finally {
    uploading.value = false
  }
}

// 从粘贴文案里提取链接：B站分享是“标题 链接”，定位 http/www 起始截取
function extractUrl(text) {
  const s = String(text || '').trim()
  const i = s.search(/https?:\/\/|www\./i)
  if (i < 0) return s
  const u = s.slice(i).split(/[\s一-龥]/)[0]
  return u.replace(/[,.;:!?，。；！？)）\]】>"']+$/, '')
}

async function startDownload() {
  if (!link.value.style) return message.warning('请选择风格（必选项）')
  const data = await api.mediaDownload({
    url: extractUrl(link.value.url),
    title: (link.value.title || '').trim() || null,
    singerName: (link.value.singerName || '').trim() || null,
    style: link.value.style,
    wantTranscribe: link.value.wantTranscribe
  })
  // 登记进全局任务 store：切页后由 store 继续轮询并通知结果
  task.value = taskStore.track(data.taskId, 'DOWNLOAD')
}

// bug17：导入成功后分流——歌手去「个人设置-我的歌曲」看审核状态（bug66 迁移），管理员去首页
function afterImport() {
  if (isAdmin) {
    router.push('/')
  } else {
    router.push('/settings')
  }
}

async function cancel() {
  await taskStore.cancel(task.value.taskId)
}

/* bug74：链接解析预览——提交前看到智能裁剪结果与时长 */
const previewing = ref(false)
const previewInfo = ref(null)

async function preview() {
  const u = extractUrl(link.value.url || '')
  if (!u) return message.warning('请先填写链接')
  previewing.value = true
  try {
    previewInfo.value = await api.importPreview(u)
    // 歌名空着时预填裁剪结果，仍可手改（提交时走 mtitle 覆盖）
    if (!link.value.title && previewInfo.value.title) {
      link.value.title = previewInfo.value.title
    }
  } finally {
    previewing.value = false
  }
}

function fmtDur(sec) {
  const s = Math.round(Number(sec) || 0)
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
}

/* bug75：新建歌手账号（管理员）：拼音用户名自动生成，初始密码 admin123 */
const singerDialog = ref({ visible: false, name: '', target: 'form', creating: false })

function openSingerDialog(target) {
  singerDialog.value = {
    visible: true,
    // 默认带上当前搜索/已填的歌手名，少敲一次
    name: (target === 'form' ? form.value.singerName : link.value.singerName)
      || singerOpts.value[0]?.nickname || '',
    target,
    creating: false
  }
}

async function createSinger() {
  const name = (singerDialog.value.name || '').trim()
  if (!name) return message.warning('请填写歌手名')
  singerDialog.value.creating = true
  try {
    const r = await api.ensureSinger(name)
    message.success(
      r?.created
        ? `已创建歌手账号：${r.username}（初始密码 admin123，请通知本人尽快修改）`
        : `已有同名歌手账号：${r.username}，直接选用`
    )
    if (singerDialog.value.target === 'form') form.value.singerName = name
    else link.value.singerName = name
    singerOpts.value = [{ id: r.userId, nickname: name }, ...singerOpts.value.filter((s) => s.nickname !== name)]
    singerDialog.value.visible = false
  } finally {
    singerDialog.value.creating = false
  }
}

const taskTag = () => {
  const s = (task.value && task.value.status) || ''
  if (s === 'SUCCESS') return 'success'
  if (s === 'FAILED') return 'danger'
  if (s === 'CANCELLED') return 'info'
  return 'warning'
}

/* bug71：回到上传中心时接续展示进行中（或刚结束）的导入任务——
   任务在全局 store 轮询，此前这里只绑本地 ref，切页回来就“看了一个寂寞” */
onMounted(() => {
  const pending = taskStore.tasks.find((t) => t.taskType === 'DOWNLOAD')
  if (pending) task.value = pending
})
</script>

<style scoped>
.upload-form { max-width: 640px; }
.task-box { margin-top: 10px; border-top: 1px dashed var(--border); padding-top: 12px; }
.task-line { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.stage { font-size: 13px; color: var(--text-muted); }
.task-hint { font-size: 12px; color: var(--text-faint); margin-top: 6px; }
.task-error { color: var(--error); font-size: 13px; margin-top: 6px; }
.sub { color: var(--text-faint); font-size: 12px; }
/* bug16：导入预览（身份/分类） */
.import-preview {
  margin: -4px 0 14px 100px;
  font-size: 12.5px;
  color: var(--text-muted);
}
/* bug74：解析预览结果块 */
.preview-box {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--accent, #ff6b1a) 6%, transparent);
}
.url-row { display: flex; gap: 8px; align-items: center; }
.singer-row { display: flex; align-items: center; gap: 10px; }
.new-singer { font-size: 12px; color: var(--accent, #ff6b1a); white-space: nowrap; }
.dlg-tip { font-size: 12px; color: var(--text-faint); line-height: 1.6; }
</style>
