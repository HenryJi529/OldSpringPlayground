import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import legacy from '@vitejs/plugin-legacy'
import browserslist from 'browserslist'


export default defineConfig({
  plugins: [
    vue(),
    legacy({
      renderLegacyChunks: false,
      modernTargets: browserslist(),
      modernPolyfills: true
    }),
  ],
  server: {
    port: 8087,
    proxy: {
      // 后端 Spring Boot 在 8088，rewrite 去掉 /api 前缀
      '/api': {
        target: 'http://127.0.0.1:8088',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
  preview: {
    open: false
  }
})
