<template>
  <!-- 后台下载悬浮指示器：有进行中的媒体任务时出现在右下角（播放栏上方），
       切到任何页面都能看到进度，点击跳转上传中心 -->
  <transition name="tfade">
    <div v-if="taskStore.tasks.length" class="task-float">
      <div v-for="t in shown" :key="t.taskId" class="task-item">
        <div class="task-head">
          <span class="dot" :class="dotClass(t)"></span>
          <span class="label">{{ taskStore.typeLabel(t) }}</span>
          <span class="stage" :title="t.stage">{{ t.stage }}</span>
          <button v-if="!isTerminal(t)" class="cancel" title="取消任务"
            @click="taskStore.cancel(t.taskId).catch(() => {})">×</button>
        </div>
        <div class="bar">
          <div class="fill" :class="barClass(t)" :style="{ width: (t.progress || 0) + '%' }"></div>
        </div>
      </div>
      <div class="goto" @click="$router.push('/upload')">
        {{ hasActive ? '后台进行中，去上传中心查看' : '已完成，去上传中心查看' }}
        <span class="arrow">→</span>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { computed } from 'vue'
import { useTaskStore } from '../stores/tasks'
import { isTaskTerminal } from '../utils'

const taskStore = useTaskStore()
// 最多展示 3 条，防止批量任务时撑爆悬浮层
const shown = computed(() => taskStore.tasks.slice(0, 3))
const hasActive = computed(() => taskStore.hasActive)

const isTerminal = (t) => isTaskTerminal(t.status)
const dotClass = (t) =>
  isTerminal(t) ? (t.status === 'SUCCESS' ? 'ok' : 'stop') : 'run'
const barClass = (t) => (isTerminal(t) ? (t.status === 'SUCCESS' ? 'ok' : 'stop') : 'run')
</script>

<style scoped>
.task-float {
  position: fixed;
  right: 18px;
  bottom: 96px; /* 播放栏高度之上 */
  z-index: 1800;
  width: 264px;
  padding: 10px 12px 8px;
  border-radius: 12px;
  background: var(--bg-card, #20232b);
  border: 1px solid var(--border, rgba(255, 255, 255, 0.09));
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.38);
  display: flex;
  flex-direction: column;
  gap: 8px;
  cursor: default;
}
.task-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  min-width: 0;
}
.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex: none;
}
.dot.run {
  background: #6c9bff;
  animation: pulse 1.2s ease-in-out infinite;
}
.dot.ok { background: #4dd28a; }
.dot.stop { background: #e06a6a; }
.label {
  font-weight: 600;
  flex: none;
}
.stage {
  color: var(--text-muted, #9aa0ae);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.cancel {
  flex: none;
  border: none;
  background: none;
  color: var(--text-muted, #9aa0ae);
  font-size: 14px;
  cursor: pointer;
  padding: 0 2px;
  line-height: 1;
}
.cancel:hover { color: #e06a6a; }
.bar {
  height: 3px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.08);
  overflow: hidden;
}
.fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.6s ease;
}
.fill.run { background: linear-gradient(90deg, #6c9bff, #9b7bff); }
.fill.ok { background: #4dd28a; }
.fill.stop { background: #e06a6a; }
.goto {
  font-size: 12px;
  color: #6c9bff;
  cursor: pointer;
  padding-top: 2px;
  border-top: 1px dashed var(--border, rgba(255, 255, 255, 0.09));
  margin-top: 2px;
  padding-top: 6px;
}
.goto:hover { text-decoration: underline; }
.arrow { margin-left: 2px; }
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
}
.tfade-enter-active, .tfade-leave-active { transition: opacity 0.25s, transform 0.25s; }
.tfade-enter-from, .tfade-leave-to { opacity: 0; transform: translateY(8px); }
</style>
