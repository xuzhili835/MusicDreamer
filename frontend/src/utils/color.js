// 封面主色提取 → 卡片底色衍生。
// 思路：封面缩到 24×24 画布取像素，跳过近白/近黑/脏灰后按色相分桶，
// 以「出现次数 × 饱和度」加权挑出主色相桶，桶内平均得到 HSL；
// 再把 HSL 校进适合当浅底的范围（低饱和高亮度），并派生一个较深的点缀色。
// 结果按 url 缓存；图片加载/读像素失败返回 null（调用方回退白卡）。
const cache = new Map()

function rgbToHsl(r, g, b) {
  r /= 255; g /= 255; b /= 255
  const max = Math.max(r, g, b), min = Math.min(r, g, b)
  const l = (max + min) / 2
  if (max === min) return [0, 0, l]
  const d = max - min
  const s = l > 0.5 ? d / (2 - max - min) : d / (max + min)
  let h
  if (max === r) h = ((g - b) / d + (g < b ? 6 : 0))
  else if (max === g) h = (b - r) / d + 2
  else h = (r - g) / d + 4
  return [h * 60, s, l]
}

function clamp(v, lo, hi) { return Math.min(hi, Math.max(lo, v)) }

/** h∈[0,360) s,l∈[0,1] */
function hsl(h, s, l) { return `hsl(${((h % 360) + 360) % 360}, ${Math.round(s * 100)}%, ${Math.round(l * 100)}%)` }

/**
 * 提取主色并衍生卡片配色。
 * @returns {Promise<{bg:string, accent:string}|null>} bg=浅色渐变底色，accent=同色系点缀色
 */
export function coverTint(url) {
  if (!url) return Promise.resolve(null)
  if (cache.has(url)) return Promise.resolve(cache.get(url))
  return new Promise((resolve) => {
    const img = new Image()
    img.crossOrigin = 'anonymous'
    const fail = () => { cache.set(url, null); resolve(null) }
    img.onerror = fail
    img.onload = () => {
      try {
        const S = 24
        const c = document.createElement('canvas')
        c.width = S; c.height = S
        const g = c.getContext('2d', { willReadFrequently: true })
        g.drawImage(img, 0, 0, S, S)
        const px = g.getImageData(0, 0, S, S).data
        // 色相 12 桶：count 加权 (0.3+饱和度)，避免被大片脏色带偏
        const bins = Array.from({ length: 12 }, () => ({ n: 0, w: 0, h: 0, s: 0, l: 0 }))
        for (let i = 0; i < px.length; i += 4) {
          const [h, s, l] = rgbToHsl(px[i], px[i + 1], px[i + 2])
          if (l > 0.93 || l < 0.1 || s < 0.12) continue // 近白/近黑/灰不参与
          const b = bins[Math.min(11, Math.floor(h / 30))]
          b.n++
          b.w += 0.3 + s
          b.h += h; b.s += s; b.l += l
        }
        const best = bins.reduce((a, b) => (b.w > a.w ? b : a), { w: 0, n: 0 })
        if (!best.n) { fail(); return }
        const h = best.h / best.n, s = best.s / best.n
        // 校准：底色淡雅（饱和度压低、亮度抬高），点缀色同色相加深
        const tintA = hsl(h, clamp(s * 0.6, 0.2, 0.5), 0.93)
        const tintB = hsl(h, clamp(s * 0.95, 0.3, 0.62), 0.83)
        const accent = hsl(h, clamp(s * 1.1 + 0.15, 0.5, 0.72), 0.5)
        const out = { bg: `linear-gradient(158deg, ${tintA} 0%, ${tintB} 100%)`, accent }
        cache.set(url, out)
        resolve(out)
      } catch (e) {
        fail()
      }
    }
    img.src = url
  })
}

// 无封面时按占位渐变（app.css .c1~.c6，key = id%6+1）派生的成套浅色，
// 保证整套卡片无论有无封面都有色彩，而不是退回纯白
const PH_TINTS = {
  1: { h: 348, s: 0.7 }, // #ff512f→#dd2476 暖玫红
  2: { h: 172, s: 0.65 }, // #00c9ff→#92fe9d 青绿
  3: { h: 285, s: 0.72 }, // #7b2ff7→#f107a3 紫洋红
  4: { h: 38, s: 0.8 }, // #f7971e→#ffd200 金橙
  5: { h: 200, s: 0.66 }, // #12c2e9→#f64f59 青粉混
  6: { h: 200, s: 0.3 } // #0f2027→#2c5364 深蓝灰
}
export function placeholderTint(idx) {
  const p = PH_TINTS[((Number(idx) || 0) % 6) + 1] || PH_TINTS[1]
  return {
    bg: `linear-gradient(158deg, ${hsl(p.h, p.s * 0.45, 0.93)} 0%, ${hsl(p.h, p.s * 0.75, 0.83)} 100%)`,
    accent: hsl(p.h, clamp(p.s + 0.05, 0.5, 0.7), 0.5)
  }
}
