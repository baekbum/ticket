(function () {
  const SEAT_REDIS_URL = `${base()}/admin/api/${API.VERSION}/seat/cache/inspect`;
  const headers = { 'Authorization': getAuthHeader() };
  let previousSnapshot = new Map();
  let autoRefreshTimer = null;
  let autoRefreshEnabled = false;

  function limitValue() {
    return document.getElementById('redis-limit')?.value || '100';
  }

  function inputValue(id) {
    return document.getElementById(id)?.value.trim() || '';
  }

  function refreshIntervalMillis() {
    const seconds = parseInt(document.getElementById('redis-refresh-interval')?.value || '5', 10);
    return Math.max(seconds, 1) * 1000;
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function formatTtl(ttl) {
    if (ttl === null || ttl === undefined) return '-';
    if (ttl === -2) return 'missing';
    if (ttl === -1) return 'no ttl';
    if (ttl < 60) return `${ttl}s`;
    if (ttl < 3600) return `${Math.floor(ttl / 60)}m ${ttl % 60}s`;
    if (ttl < 86400) return `${Math.floor(ttl / 3600)}h ${Math.floor((ttl % 3600) / 60)}m`;
    return `${Math.floor(ttl / 86400)}d ${Math.floor((ttl % 86400) / 3600)}h`;
  }

  function statusBadge(status) {
    const normalized = String(status || 'MISSING').toUpperCase();
    const css = normalized === 'AVAILABLE'
      ? 'available'
      : normalized === 'LOCKED'
        ? 'locked'
        : normalized === 'RESERVED'
          ? 'reserved'
          : 'missing';
    return `<span class="redis-badge ${css}">${escapeHtml(normalized)}</span>`;
  }

  function entrySignature(entry) {
    return JSON.stringify({
      value: entry.value,
      status: entry.status,
      lockValue: entry.lockValue
    });
  }

  function renderEntries(entries) {
    const tbody = document.getElementById('redis-seat-body');
    if (!tbody) return 0;

    if (!entries.length) {
      tbody.innerHTML = '<tr><td colspan="6" class="redis-empty">No Redis entries found.</td></tr>';
      previousSnapshot = new Map();
      return 0;
    }

    let changedCount = 0;
    const nextSnapshot = new Map();
    tbody.innerHTML = '';

    entries.forEach(entry => {
      const signature = entrySignature(entry);
      const previous = previousSnapshot.get(entry.key);
      const changed = previous !== undefined && previous !== signature;
      if (changed) changedCount += 1;
      nextSnapshot.set(entry.key, signature);

      const tr = document.createElement('tr');
      if (changed) tr.classList.add('redis-row-changed');
      tr.innerHTML = `
        <td class="redis-key" title="${escapeHtml(entry.key)}">${escapeHtml(entry.key)}</td>
        <td class="redis-value" title="${escapeHtml(entry.value || '')}">${escapeHtml(entry.value || '-')}</td>
        <td>${statusBadge(entry.status)}</td>
        <td>${escapeHtml(formatTtl(entry.ttlSeconds))}</td>
        <td class="redis-value" title="${escapeHtml(entry.lockValue || '')}">${escapeHtml(entry.lockValue || '-')}</td>
        <td>${escapeHtml(formatTtl(entry.lockTtlSeconds))}</td>
      `;
      tbody.appendChild(tr);
    });

    previousSnapshot = nextSnapshot;
    return changedCount;
  }

  function updateSummary(data, changedCount) {
    document.getElementById('redis-summary-scope').textContent = buildSummaryTarget();
    document.getElementById('redis-summary-count').textContent = String(data.count ?? 0);
    document.getElementById('redis-summary-changed').textContent = String(changedCount);
    document.getElementById('redis-summary-time').textContent = new Date().toLocaleTimeString();
  }

  function buildSummaryTarget() {
    const parts = [`Event ${inputValue('redis-event-id') || '-'}`];
    const zone = inputValue('redis-zone');
    const row = inputValue('redis-row');
    const col = inputValue('redis-col');
    if (zone) parts.push(`Zone ${zone}`);
    if (row) parts.push(`Row ${row}`);
    if (col) parts.push(`Col ${col}`);
    return parts.join(' / ');
  }

  window.clearRedisSnapshot = function () {
    previousSnapshot = new Map();
    document.getElementById('redis-summary-changed').textContent = '0';
    showToast('\uBCC0\uACBD \uAC10\uC9C0 \uAE30\uC900\uC744 \uCD08\uAE30\uD654\uD588\uC2B5\uB2C8\uB2E4.');
  };

  window.loadSeatRedis = async function () {
    const eventId = inputValue('redis-event-id');
    if (!eventId) {
      showToast('\uC870\uD68C \uB300\uC0C1 ID\uB97C \uC785\uB825\uD574\uC8FC\uC138\uC694.', true);
      return;
    }

    const params = new URLSearchParams();
    params.set('limit', limitValue());

    const zone = inputValue('redis-zone');
    const row = inputValue('redis-row');
    const col = inputValue('redis-col');

    if (zone) params.set('zone', zone);
    if (row) params.set('row', row);
    if (col) params.set('col', col);

    try {
      const res = await Fetch(`${SEAT_REDIS_URL}/event/${encodeURIComponent(eventId)}?${params.toString()}`, { method: 'GET', headers });
      if (!res.ok) {
        showToast('Seat Redis \uC870\uD68C\uC5D0 \uC2E4\uD328\uD588\uC2B5\uB2C8\uB2E4.', true);
        return;
      }
      const data = await res.json();
      const changedCount = renderEntries(data.entries || []);
      updateSummary(data, changedCount);
    } catch (e) {
      console.error(e);
      showToast('Seat Redis \uC870\uD68C\uC5D0 \uC2E4\uD328\uD588\uC2B5\uB2C8\uB2E4.', true);
    }
  };

  function syncAutoRefreshToggle() {
    document.getElementById('redis-auto-start')?.classList.toggle('active', autoRefreshEnabled);
    document.getElementById('redis-auto-stop')?.classList.toggle('active', !autoRefreshEnabled);
  }

  function restartAutoRefresh() {
    if (autoRefreshTimer) {
      clearInterval(autoRefreshTimer);
      autoRefreshTimer = null;
    }
    if (autoRefreshEnabled) {
      autoRefreshTimer = setInterval(() => window.loadSeatRedis(), refreshIntervalMillis());
    }
    syncAutoRefreshToggle();
  }

  window.setRedisAutoRefresh = function (enabled) {
    autoRefreshEnabled = Boolean(enabled);
    restartAutoRefresh();
  };

  window.updateRedisAutoRefreshInterval = function () {
    restartAutoRefresh();
  };

  window.addEventListener('admin:fragment-loaded', function cleanupRedisTimer(event) {
    if (event.detail?.menuName === 'seatRedis') return;
    autoRefreshEnabled = false;
    if (autoRefreshTimer) {
      clearInterval(autoRefreshTimer);
      autoRefreshTimer = null;
    }
    window.removeEventListener('admin:fragment-loaded', cleanupRedisTimer);
  });

  syncAutoRefreshToggle();
})();
