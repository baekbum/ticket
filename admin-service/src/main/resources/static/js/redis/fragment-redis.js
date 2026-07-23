(function () {
  const SEAT_REDIS_URL = `${base()}/admin/api/${API.VERSION}/seat/cache/inspect`;
  const headers = { 'Authorization': getAuthHeader() };
  let previousSnapshot = new Map();
  let autoRefreshTimer = null;
  let autoRefreshEnabled = false;
  let redisMode = 'SEAT';
  let redisSort = { field: 'key', direction: 'asc' };
  let lastEntries = [];

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

  function sortIndicator(field) {
    if (redisSort.field !== field) return '';
    return `<span class="redis-sort-indicator">${redisSort.direction === 'asc' ? '▲' : '▼'}</span>`;
  }

  function sortableHeader(label, field) {
    return `<button class="redis-sort-btn" type="button" onclick="sortSeatRedis('${field}')">${label}${sortIndicator(field)}</button>`;
  }

  function entrySignature(entry) {
    return JSON.stringify({
      mode: redisMode,
      value: entry.value,
      status: entry.status,
      lockValue: entry.lockValue
    });
  }

  function renderTableHeader() {
    const headRow = document.getElementById('redis-table-head-row');
    if (!headRow) return;

    if (redisMode === 'LOCK') {
      headRow.innerHTML = `
        <th>${sortableHeader('Lock Key', 'key')}</th>
        <th>${sortableHeader('Lock Value', 'value')}</th>
        <th>${sortableHeader('TTL', 'ttlSeconds')}</th>
        <th>${sortableHeader('Seat Key', 'lockKey')}</th>
        <th>${sortableHeader('Seat Value', 'lockValue')}</th>
        <th>${sortableHeader('Seat TTL', 'lockTtlSeconds')}</th>
      `;
      return;
    }

    headRow.innerHTML = `
      <th>${sortableHeader('Key', 'key')}</th>
      <th>${sortableHeader('Value', 'value')}</th>
      <th>${sortableHeader('Status', 'status')}</th>
      <th>${sortableHeader('TTL', 'ttlSeconds')}</th>
      <th>${sortableHeader('Lock Value', 'lockValue')}</th>
      <th>${sortableHeader('Lock TTL', 'lockTtlSeconds')}</th>
    `;
  }

  function valueForSort(entry, field) {
    const value = entry?.[field];
    if (value === null || value === undefined) return '';
    if (typeof value === 'number') return value;
    return String(value).toLowerCase();
  }

  function sortEntries(entries) {
    const directionMultiplier = redisSort.direction === 'asc' ? 1 : -1;
    return [...entries].sort((a, b) => {
      const aValue = valueForSort(a, redisSort.field);
      const bValue = valueForSort(b, redisSort.field);

      if (typeof aValue === 'number' && typeof bValue === 'number') {
        return (aValue - bValue) * directionMultiplier;
      }

      return String(aValue).localeCompare(String(bValue), undefined, { numeric: true }) * directionMultiplier;
    });
  }

  function renderEntries(entries, options = {}) {
    const tbody = document.getElementById('redis-seat-body');
    if (!tbody) return 0;
    const detectChanges = options.detectChanges !== false;

    const sortedEntries = sortEntries(entries);

    if (!sortedEntries.length) {
      tbody.innerHTML = '<tr><td colspan="6" class="redis-empty">No Redis entries found.</td></tr>';
      if (detectChanges) {
        previousSnapshot = new Map();
      }
      return 0;
    }

    let changedCount = 0;
    const nextSnapshot = new Map();
    tbody.innerHTML = '';

    sortedEntries.forEach(entry => {
      const signature = entrySignature(entry);
      const previous = previousSnapshot.get(entry.key);
      const changed = detectChanges && previous !== undefined && previous !== signature;
      if (changed) changedCount += 1;
      nextSnapshot.set(entry.key, signature);

      const tr = document.createElement('tr');
      if (changed) tr.classList.add('redis-row-changed');

      if (redisMode === 'LOCK') {
        tr.innerHTML = `
          <td class="redis-key" title="${escapeHtml(entry.key)}">${escapeHtml(entry.key)}</td>
          <td class="redis-value" title="${escapeHtml(entry.value || '')}">${escapeHtml(entry.value || '-')}</td>
          <td>${escapeHtml(formatTtl(entry.ttlSeconds))}</td>
          <td class="redis-key" title="${escapeHtml(entry.lockKey || '')}">${escapeHtml(entry.lockKey || '-')}</td>
          <td class="redis-value" title="${escapeHtml(entry.lockValue || '')}">${escapeHtml(entry.lockValue || '-')}</td>
          <td>${escapeHtml(formatTtl(entry.lockTtlSeconds))}</td>
        `;
      } else {
        tr.innerHTML = `
          <td class="redis-key" title="${escapeHtml(entry.key)}">${escapeHtml(entry.key)}</td>
          <td class="redis-value" title="${escapeHtml(entry.value || '')}">${escapeHtml(entry.value || '-')}</td>
          <td>${statusBadge(entry.status)}</td>
          <td>${escapeHtml(formatTtl(entry.ttlSeconds))}</td>
          <td class="redis-value" title="${escapeHtml(entry.lockValue || '')}">${escapeHtml(entry.lockValue || '-')}</td>
          <td>${escapeHtml(formatTtl(entry.lockTtlSeconds))}</td>
        `;
      }
      tbody.appendChild(tr);
    });

    if (detectChanges) {
      previousSnapshot = nextSnapshot;
    }
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

  function syncRedisModeToggle() {
    document.getElementById('redis-mode-seat')?.classList.toggle('active', redisMode === 'SEAT');
    document.getElementById('redis-mode-lock')?.classList.toggle('active', redisMode === 'LOCK');
    document.querySelector('.redis-table')?.classList.toggle('lock-mode', redisMode === 'LOCK');
    renderTableHeader();
  }

  function resetSummary() {
    document.getElementById('redis-summary-scope').textContent = '-';
    document.getElementById('redis-summary-count').textContent = '0';
    document.getElementById('redis-summary-changed').textContent = '0';
    document.getElementById('redis-summary-time').textContent = '-';
  }

  window.setSeatRedisMode = function (mode) {
    redisMode = mode === 'LOCK' ? 'LOCK' : 'SEAT';
    redisSort = { field: 'key', direction: 'asc' };
    lastEntries = [];
    previousSnapshot = new Map();
    resetSummary();
    syncRedisModeToggle();
  };

  window.sortSeatRedis = function (field) {
    if (redisSort.field === field) {
      redisSort.direction = redisSort.direction === 'asc' ? 'desc' : 'asc';
    } else {
      redisSort = { field, direction: 'asc' };
    }

    renderTableHeader();
    renderEntries(lastEntries, { detectChanges: false });
  };

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
    params.set('mode', redisMode);

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
      lastEntries = data.entries || [];
      const changedCount = renderEntries(lastEntries);
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
  syncRedisModeToggle();
})();
