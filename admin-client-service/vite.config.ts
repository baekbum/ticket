import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const servicePaths = [
  '/auth', '/user', '/ticket', '/queue', '/support', '/audit', '/payment-gateway', '/admin-api',
];

export default defineConfig({
  base: '/admin/',
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 8999,
    strictPort: true,
    proxy: Object.fromEntries(servicePaths.map(path => [path, {
      target: 'http://localhost:80',
      changeOrigin: true,
    }])),
  },
});
