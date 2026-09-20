import { cp, mkdir, readFile, readdir, unlink, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const projectRoot = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const resourceRoot = path.join(projectRoot, 'legacy-source');
const publicRoot = path.join(projectRoot, 'public', 'legacy');
const fragmentRoot = path.join(publicRoot, 'fragments');

await mkdir(fragmentRoot, { recursive: true });
await cp(path.join(resourceRoot, 'static', 'css'), path.join(publicRoot, 'css'), { recursive: true, force: true });
await cp(path.join(resourceRoot, 'static', 'js'), path.join(publicRoot, 'js'), { recursive: true, force: true });
await cp(path.join(projectRoot, 'scripts', 'bridge.js'), path.join(publicRoot, 'bridge.js'), { force: true });
const bridgeVersion = createHash('sha256').update(await readFile(path.join(projectRoot, 'scripts', 'bridge.js'))).digest('hex').slice(0, 8);

const pagination = (await readFile(path.join(resourceRoot, 'templates', 'common', 'pagination.html'), 'utf8'))
  .replace(/\s+th:fragment="[^"]*"/, '');

const aliases = {
  'reservation-delivery': 'reservationDelivery',
  'payment-refund-process': 'paymentRefundProcess',
  'user-coupon': 'userCoupon',
  'audit-log': 'auditLog',
  'redis': 'seatRedis',
  'redis-hub': 'redisHub',
  'queue-redis': 'queueRedis',
  'seat-cache-sync-failures': 'seatCacheSyncFailures',
  'kafka-dlq': 'kafkaDlq',
  'kafka-dlq-history': 'kafkaDlqHistory',
  'test-hub': 'testHub',
  'failure-monitoring': 'failureMonitoring',
  'seat-reservation-test': 'seatReservationTest',
  'queue-enter-test': 'queueEnterTest',
  'dlt-publish-test': 'dltPublishTest',
  'dlt-slack-test': 'dltSlackTest',
};

for (const filename of await readdir(fragmentRoot)) {
  if (filename.endsWith('.html')) await unlink(path.join(fragmentRoot, filename));
}

for (const filename of await readdir(path.join(resourceRoot, 'templates', 'fragment'))) {
  if (!filename.endsWith('.html')) continue;
  const kebabName = filename.slice('fragment-'.length, -'.html'.length);
  const menuName = aliases[kebabName] || kebabName;
  let fragment = await readFile(path.join(resourceRoot, 'templates', 'fragment', filename), 'utf8');
  fragment = fragment.replace(/<head>[\s\S]*?<\/head>/i, '');
  fragment = fragment.replace(/<div\s+th:replace="~\{common\/pagination :: pagination\}"\s*><\/div>/g, pagination);
  fragment = fragment.replace(/\s+th:(href|src)="@\{\/(css|js)\/([^}]+)\}"/g, (_match, attribute, kind, asset) => ` ${attribute}="/admin/legacy/${kind}/${asset}"`);
  fragment = fragment.replace(/\s+th:if="\$\{localProfile\}"/g, ' data-local-only="true"');
  if (menuName === 'monitoring') {
    const cards = [
      ['Spring Boot / JVM', 'chart-line', 'http://localhost:3001/d/ticket-spring-services/ticket-spring-services-overview'],
      ['PostgreSQL', 'database', 'http://localhost:3001/d/ticket-postgresql-overview/ticket-postgresql-overview'],
      ['Redis', 'database', 'http://localhost:3001/d/ticket-redis-overview/ticket-redis-overview'],
      ['Docker', 'brand-docker', 'http://localhost:3001/d/ticket-docker-cadvisor-overview/ticket-docker-cadvisor-overview'],
      ['Nginx', 'server', 'http://localhost:3001/dashboards?query=Nginx'],
    ];
    fragment = fragment.replace(/<button\s+class="monitoring-card"[\s\S]*?<\/button>/, cards.map(([title, icon, url]) => `<button class="monitoring-card" type="button" data-url="${url}" onclick="openMonitoringDashboard(this)"><span class="monitoring-icon"><i class="ti ti-${icon}"></i></span><span class="monitoring-card-content"><strong>${title}</strong><em>Grafana Dashboard</em><span>대시보드 새 창 열기</span></span><span class="monitoring-card-action"><i class="ti ti-external-link"></i></span></button>`).join('\n'));
  }
  const html = `<!doctype html><html lang="ko"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/@tabler/icons-webfont@latest/tabler-icons.min.css"><link rel="stylesheet" href="/admin/legacy/css/dashboard/dashboard.css"><link rel="stylesheet" href="/admin/legacy/css/common/toast.css"><link rel="stylesheet" href="/admin/legacy/css/common/modal.css"><link rel="stylesheet" href="/admin/legacy/css/common/table-common.css"><style>html,body{display:block;width:100%;height:100%;margin:0;overflow:auto}#embed-content{width:100%;height:100%;overflow:auto}</style><script src="/admin/legacy/bridge.js?v=${bridgeVersion}"></script><script src="/admin/legacy/js/common/toast.js"></script><script src="/admin/legacy/js/common/modal.js"></script></head><body><main id="embed-content" class="main-content">${fragment}</main><div class="toast" id="toast"></div><script>window.addEventListener('DOMContentLoaded', () => { document.querySelectorAll('[data-local-only]').forEach(el => { if (!['localhost', '127.0.0.1'].includes(location.hostname)) el.remove(); }); const query = new URLSearchParams(location.search); let context = Object.fromEntries(query); if (query.has('context')) { try { context = JSON.parse(query.get('context')); } catch { context = {}; } } window.dispatchEvent(new CustomEvent('admin:fragment-loaded', { detail: { menuName: ${JSON.stringify(menuName)}, context } })); });</script></body></html>`;
  await writeFile(path.join(fragmentRoot, `${menuName}.html`), html);
}
