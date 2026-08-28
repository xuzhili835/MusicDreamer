<template>
  <div class="gen-cover" :style="coverStyle" aria-hidden="true">
    <span class="glyph" v-if="glyph && !initial">{{ glyph }}</span>
    <span class="initial" v-else>{{ initial }}</span>
    <span class="grain"></span>
  </div>
</template>

<script setup>
// 生成式封面：无上传封面时的统一视觉。名字哈希 → 稳定色相，双渐变 + 首字/音符符号。
// 同一歌单/专辑每次渲染一致，不同名字天然区分度。
import { computed } from 'vue'

const props = defineProps({
  name: { type: String, default: '' },
  id: { type: [Number, String], default: 0 },
  glyph: { type: String, default: '' } // 歌单 ♪ / 专辑 ♫，有名字时优先显示首字
})

function hashStr(s) {
  let h = 2166136261
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return Math.abs(h)
}

const initial = computed(() => {
  const n = (props.name || '').trim()
  return n ? Array.from(n)[0].toUpperCase() : ''
})

const coverStyle = computed(() => {
  const h = hashStr((props.name || '') + '#' + props.id) % 360
  const h2 = (h + 42) % 360
  return {
    background: `linear-gradient(135deg, hsl(${h}, 62%, 52%) 0%, hsl(${h2}, 68%, 38%) 100%)`
  }
})
</script>

<style scoped>
.gen-cover {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  user-select: none;
}
.initial,
.glyph {
  color: rgba(255, 255, 255, 0.94);
  font-family: var(--font-display);
  font-weight: 800;
  text-shadow: 0 2px 14px rgba(0, 0, 0, 0.28);
  line-height: 1;
}
.initial { font-size: 34px; }
.glyph { font-size: 30px; }
.grain {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 82% 18%, rgba(255, 255, 255, 0.26) 0%, transparent 42%),
    radial-gradient(circle at 12% 88%, rgba(0, 0, 0, 0.18) 0%, transparent 46%);
  pointer-events: none;
}
</style>
