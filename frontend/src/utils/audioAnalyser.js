// Web Audio 频谱分析单例：把播放器的 <audio> 接入全局唯一 AudioContext。
// createMediaElementSource 每个元素只能调用一次，因此 ctx/analyser 必须全局复用；
// 接入后声音改走 ctx.destination，所以仅在真正需要频谱时（首次播放）才建立连接，
// 且每次播放前 resume，避免 autoplay 策略导致静音。
// 音量路由：analyser 之后串增益节点出声，应用音量/静音只作用于增益节点——
// 分析器永远吃元素满幅输出，应用内静音/音量0 时频谱动效照常跳动；
// 图尚未建立时由调用方把音量落在元素上（见 player.applyVolume），建图时补挂。
let ctx = null
let analyser = null
let gainNode = null
let outputVolume = null // 最近一次 setOutputVolume 的目标值；建图晚于音量设置时用它是正确起点

export function getAnalyser(audioEl) {
  if (!audioEl) return null
  try {
    if (!ctx) {
      const AC = window.AudioContext || window.webkitAudioContext
      if (!AC) return null
      ctx = new AC()
      const src = ctx.createMediaElementSource(audioEl)
      analyser = ctx.createAnalyser()
      analyser.fftSize = 256
      analyser.smoothingTimeConstant = 0.78
      gainNode = ctx.createGain()
      gainNode.gain.value = outputVolume === null ? 1 : outputVolume
      src.connect(analyser)
      analyser.connect(gainNode)
      gainNode.connect(ctx.destination)
      // 音量接管权移交增益节点：元素必须保持满幅，否则元素与增益双重衰减
      audioEl.volume = 1
    }
    if (ctx.state === 'suspended') ctx.resume()
    return analyser
  } catch (e) {
    return null
  }
}

/** 设置输出音量（0-1，应含响度补偿）。返回 true=增益节点已接管；false=图未建，调用方落在元素上 */
export function setOutputVolume(v) {
  outputVolume = v
  if (gainNode) {
    gainNode.gain.value = Math.min(1, Math.max(0, v))
    return true
  }
  return false
}
