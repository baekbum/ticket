import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 3000,
    proxy: {
      '/auth': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/user': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/ticket': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      '/queue': {
        target: 'http://localhost:8083',
        changeOrigin: true,
      },
    },
  },
});
