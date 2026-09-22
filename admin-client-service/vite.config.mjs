import { execFile } from 'node:child_process';
import path from 'node:path';
import { promisify } from 'node:util';
import { defineConfig, normalizePath } from 'vite';
import react from '@vitejs/plugin-react';

const execFileAsync = promisify(execFile);
const legacySource = normalizePath(path.resolve('legacy-source'));
const syncScript = path.resolve('scripts/sync-legacy.mjs');

function syncLegacyDuringDev() {
  let syncQueue = Promise.resolve();
  async function syncAndReload(server) {
    const sync = syncQueue.then(() => execFileAsync(process.execPath, [syncScript]));
    syncQueue = sync.then(() => undefined, () => undefined);
    try {
      await sync;
      server.ws.send({ type: 'full-reload' });
    } catch (error) {
      server.config.logger.error(`Legacy 화면 동기화 실패: ${String(error)}`);
    }
  }
  const isLegacySource = (file) => normalizePath(file).startsWith(`${legacySource}/`);
  return {
    name: 'sync-legacy-during-dev',
    apply: 'serve',
    configureServer(server) {
      server.watcher.on('unlink', (file) => {
        if (isLegacySource(file)) void syncAndReload(server);
      });
    },
    async handleHotUpdate({ file, server }) {
      if (!isLegacySource(file)) return;
      await syncAndReload(server);
      return [];
    },
  };
}

const servicePaths = [
  '/auth', '/user', '/ticket', '/queue', '/support', '/audit', '/payment-gateway', '/admin-api',
];

export default defineConfig({
  base: '/admin/',
  plugins: [react(), syncLegacyDuringDev()],
  server: {
    host: '0.0.0.0',
    port: 8999,
    strictPort: true,
    watch: { ignored: ['**/public/legacy/**'] },
    proxy: Object.fromEntries(servicePaths.map(path => [path, {
      target: 'http://localhost:80',
      changeOrigin: true,
    }])),
  },
});
