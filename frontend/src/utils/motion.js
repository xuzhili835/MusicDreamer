// GSAP 动效辅助：统一入口 + 尊重「减少动态效果」系统偏好
import { gsap } from 'gsap'

export const prefersReduced =
  typeof window !== 'undefined' &&
  window.matchMedia &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches

// 入场：一组元素自下而上淡入并错位出现；减少动态时直接置为终态
export function revealStagger(targets, opts = {}) {
  if (!targets || (targets.length === 0)) return
  if (prefersReduced) {
    gsap.set(targets, { opacity: 1, y: 0, clearProps: 'transform' })
    return
  }
  gsap.from(targets, {
    opacity: 0,
    y: opts.y ?? 14,
    duration: opts.duration ?? 0.5,
    ease: opts.ease ?? 'power3.out',
    stagger: opts.stagger ?? 0.05,
    delay: opts.delay ?? 0,
    clearProps: 'transform,opacity'
  })
}

export { gsap }
