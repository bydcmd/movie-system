import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { NaiveUiResolver } from 'unplugin-vue-components/resolvers'
import path from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      imports: [
        'vue',
        {
          'naive-ui': [
            'useDialog',
            'useMessage',
            'useNotification',
            'useLoadingBar'
          ]
        }
      ],
      resolvers: [NaiveUiResolver()]
    }),
    Components({
      resolvers: [NaiveUiResolver()]
    })
  ],
  resolve: {
    extensions: ['.ts', '.tsx', '.mjs', '.js', '.jsx', '.json', '.vue'],
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  server: {
    port: 5173, // 你可以固定一个前端开发端口
    open: true, // 启动时自动在浏览器中打开
    proxy: {
      // 匹配所有以 /api 开头的请求路径
      '/api': {
        target: 'http://localhost:8080', // 你的真实后端接口地址
        changeOrigin: true, // 允许跨域
        // 如果后端接口本身不带 /api 前缀，需要将其重写（去掉 /api）
        // rewrite: (path) => path.replace(/^\/api/, '') 
      }
    }
  }
})
