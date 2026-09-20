import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 3000,
    proxy: Object.fromEntries([
      '/auth', '/user', '/ticket', '/queue', '/support', '/payment-gateway',
    ].map(path => [path, {
      target: 'http://localhost:80',
      changeOrigin: true,
    }])),
  },
});
