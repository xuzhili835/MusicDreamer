import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发代理指向本地网关 :8080（docker-compose 与本机 java -jar 均为此端口）
// '/data' 为后端静态资源（音频/封面）路径，见 init.sql 示例 file_url=/data/music/x.mp3
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: 'ws://localhost:8080', ws: true, changeOrigin: true },
      '/data': { target: 'http://localhost:8080', changeOrigin: true }
    }
  },
  build: {
    chunkSizeWarningLimit: 1500
  }
})
