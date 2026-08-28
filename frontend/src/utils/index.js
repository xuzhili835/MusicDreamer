// 通用格式化 / 数据归一化工具（后端分页返回 MyBatis-Plus Page：records+total；部分接口直接返回数组）

export function fmtDuration(sec) {
  const s = Math.max(0, Math.floor(Number(sec) || 0))
  const m = Math.floor(s / 60)
  return `${m}:${String(s % 60).padStart(2, '0')}`
}

export function fmtTime(t) {
  if (!t) return ''
  let d
  if (typeof t === 'number') {
    d = new Date(t < 1e12 ? t * 1000 : t)
  } else {
    d = new Date(String(t).replace('T', ' ').replace(/-/g, '/'))
  }
  if (isNaN(d.getTime())) return String(t)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

// 任意返回值 -> 数组
export function listOf(data) {
  if (data == null) return []
  if (Array.isArray(data)) return data
  for (const k of ['records', 'list', 'rows', 'items', 'songs', 'songList']) {
    if (Array.isArray(data[k])) return data[k]
  }
  return []
}

// 分页返回值 -> { list, total }
export function pageOf(data) {
  if (data == null) return { list: [], total: 0 }
  const list = listOf(data)
  let total = list.length
  if (!Array.isArray(data) && data.total != null) total = Number(data.total) || 0
  return { list, total }
}

// 歌手名（不同接口字段可能为 singerName / artistName / nickname）
export function singerName(song) {
  if (!song) return '未知歌手'
  return song.singer || song.singerName || song.artistName || song.singerNickname || song.nickname || '未知歌手'
}

// 歌单内的歌曲列表字段
export function playlistSongs(p) {
  return listOf(p && (p.songs || p.songList || p.songVOList || p.songVOs || p))
    .map((song) => ({ ...song, id: song.id != null ? song.id : song.songId }))
}

// /playlist/my 返回 -> { created, collected }
export function splitMyPlaylists(data) {
  if (data == null) return { created: [], collected: [] }
  const created = listOf(data.created || data.mine || data.myPlaylists || data.createdList)
  let collected = listOf(data.collected || data.favorites || data.favorited || data.favorite || data.favoriteList || data.collectedList)
  if (!created.length && !collected.length && Array.isArray(data)) {
    // 单数组结构：按 favorite 标记或 ownership 拆分
    return { created: data, collected: [] }
  }
  return { created, collected }
}

// 相对音频 URL 处理（dev 环境经 vite proxy 转发到网关 80）
export function resolveFileUrl(url) {
  if (!url) return ''
  if (/^(https?:)?\/\//i.test(url)) {
    return url.startsWith('//') ? 'http:' + url : url
  }
  return url
}

// 歌曲状态映射（init.sql: 0已下架 1审核中 2已发布）
export const SONG_STATUS = {
  0: { label: '已下架', type: 'danger' },
  1: { label: '审核中', type: 'warning' },
  2: { label: '已发布', type: 'success' }
}

// 媒体任务状态（media_task 表: PENDING/RUNNING/SUCCESS/FAILED/CANCELLED）
export const TASK_TERMINAL = ['SUCCESS', 'FAILED', 'CANCELLED', 'DONE', 'ERROR', 'CANCEL']

export function isTaskTerminal(status) {
  return TASK_TERMINAL.includes(String(status || '').toUpperCase())
}

export const STYLES = ['流行', '摇滚', '民谣', '电子', '说唱', '古典', '爵士', 'R&B', '轻音乐', '原声带', '其他']
// bug68：语言维度前端整体下线（后端 song.language 字段保留，后续要恢复再加回来）
