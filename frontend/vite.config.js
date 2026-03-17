import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import path from 'node:path';
import AutoImport from 'unplugin-auto-import/vite';
import Components from 'unplugin-vue-components/vite';
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const apiTarget = env.VITE_API_TARGET || 'http://localhost:8080';
  const stripApiPrefix = env.VITE_PROXY_STRIP_PREFIX === 'true';

  return {
    plugins: [
      vue(),
      AutoImport({
        imports: ['vue', 'vue-router', 'pinia'],
        resolvers: [ElementPlusResolver()],
        dts: false
      }),
      Components({
        resolvers: [ElementPlusResolver({ importStyle: 'css' })],
        dts: false
      })
    ],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src')
      }
    },
    server: {
      host: '0.0.0.0',
      port: 5173,
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true,
          secure: false,
          // 联调开关：后端若接口不带 /api 前缀，则开启去前缀
          rewrite: stripApiPrefix ? (value) => value.replace(/^\/api/, '') : (value) => value
        }
      }
    },
    build: {
      target: 'es2018',
      cssCodeSplit: true,
      sourcemap: false,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (!id.includes('node_modules')) {
              return undefined;
            }
            if (id.includes('xlsx')) {
              return 'xlsx';
            }
            if (id.includes('element-plus')) {
              return 'element-plus';
            }
            if (id.includes('vue')) {
              return 'vue';
            }
            if (id.includes('pinia') || id.includes('vue-router') || id.includes('axios')) {
              return 'framework';
            }
            return 'vendor';
          }
        }
      }
    }
  };
});
