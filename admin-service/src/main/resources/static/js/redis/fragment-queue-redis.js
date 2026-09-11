(function () {
  const QUEUE_REDIS_URL = `${base()}/admin/api/${API.VERSION}/queue/redis`;
  const headers = { 'Authorization': getAuthHeader() };
  let queueMode = 'WAITING';
  let queueSort = { field: 'rank', direction: 'asc' };
  let previousSnapshot = new Map();
  let lastEntries = [];
  let autoRefreshEnabled = false;
  let autoRefreshTimer = null;
  let pendingRemoveToken = null;

  function inputValue(id) {
    return document.getElementById(id)?.value.trim() || '';
  }

  function limitValue() {
    return document.getElementById('queue-limit')?.value || '100';
  }

  function refreshIntervalMillis() {
    const seconds = parseInt(document.getElementById('queue-refresh-interval')?.value || '5', 10);
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

  function escapeJs(value) {
    return String(value ?? '')
      .replace(/\\/g, '\\\\')
      .replace(/'/g, "\\'")
      .replace(/\r/g, '\\r')
      .replace(/\n/g, '\\n');
  }

  function formatTime(millis) {
    if (!millis) return '-';
    return new Date(millis).toLocaleString();
  }

  function formatTtl(ttl) {
    if (ttl === null || ttl === undefined) return '-';
    if (ttl === -2) return 'missing';
    if (ttl === -1) return 'no ttl';
    if (ttl < 60) return `${ttl}s`;
    if (ttl < 3600) return `${Math.floor(ttl / 60)}m ${ttl % 60}s`;
    return `${Math.floor(ttl / 3600)}h ${Math.floor((ttl % 3600) / 60)}m`;
  }

  function sortIndicator(field) {
    if (queueSort.field !== field) return '';
    return `<span class="queue-sort-indicator">${queueSort.direction === 'asc' ? '▲' : '▼'}</span>`;
  }

  function sortableHeader(label, field) {
    return `<button class="queue-sort-btn" type="button" onclick="sortQueueRedis('${field}')">${label}${sortIndicator(field)}</button>`;
  }

  function renderTableHeader() {
    const headRow = document.getElementById('queue-table-head-row');
    if (!headRow) return;

    if (queueMode === 'ACTIVE') {
      headRow.innerHTML = `
        <th>${sortableHeader('Token', 'token')}</th>
        <th>${sortableHeader('Expires At', 'timestampMillis')}</th>
        <th>${sortableHeader('TTL', 'ttlSeconds')}</th>
        <th>${sortableHeader('Token Value', 'value')}</th>
        <th>${sortableHeader('Key', 'key')}</th>
        <th>Action</th>
      `;
      return;
    }

    if (queueMode === 'TOKEN') {
      headRow.innerHTML = `
        <th>${sortableHeader('Token', 'token')}</th>
        <th>${sortableHeader('Token Key', 'key')}</th>
        <th>${sortableHeader('Value', 'value')}</th>
        <th>${sortableHeader('TTL', 'ttlSeconds')}</th>
      `;
      return;
    }

    headRow.innerHTML = `
      <th>${sortableHeader('Rank', 'rank')}</th>
      <th>${sortableHeader('User ID', 'member')}</th>
      <th>${sortableHeader('Entered At', 'timestampMillis')}</th>
      <th>${sortableHeader('Score', 'score')}</th>
      <th>${sortableHeader('Key', 'key')}</th>
      <th>Action</th>
    `;
  }

  function valueForSort(entry, field) {
    const value = entry?.[field];
    if (value === null || value === undefined) return '';
    if (typeof value === 'number') return value;
    return String(value).toLowerCase();
  }

  function sortEntries(entries) {
    const directionMultiplier = queueSort.direction === 'asc' ? 1 : -1;
    return [...entries].sort((a, b) => {
      const aValue = valueForSort(a, queueSort.field);
      const bValue = valueForSort(b, queueSort.field);
      if (typeof aValue === 'number' && typeof bValue === 'number') {
        return (aValue - bValue) * directionMultiplier;
      }
      return String(aValue).localeCompare(String(bValue), undefined, { numeric: true }) * directionMultiplier;
    });
  }

  function entrySignature(entry) {
    return JSON.stringify({
      mode: queueMode,
      key: entry.key,
      member: entry.member,
      value: entry.value,
      ttlSeconds: entry.ttlSeconds,
      score: entry.score
    });
  }

  function rowKey(entry) {
    return `${queueMode}:${entry.key || ''}:${entry.member || entry.token || ''}`;
  }

  function renderEntries(entries, options = {}) {
    const tbody = document.getElementById('queue-table-body');
    if (!tbody) return 0;
    const detectChanges = options.detectChanges !== false;
    const sortedEntries = sortEntries(entries);

    if (!sortedEntries.length) {
      tbody.innerHTML = '';
      if (detectChanges) previousSnapshot = new Map();
      return 0;
    }

    let changedCount = 0;
    const nextSnapshot = new Map();
    tbody.innerHTML = '';

    sortedEntries.forEach(entry => {
      const signature = entrySignature(entry);
      const key = rowKey(entry);
      const previous = previousSnapshot.get(key);
      const changed = detectChanges && previous !== undefined && previous !== signature;
      if (changed) changedCount += 1;
      nextSnapshot.set(key, signature);

      const tr = document.createElement('tr');
      if (changed) tr.classList.add('queue-row-changed');
      const token = entry.token || entry.member || '';
      const removeButton = queueMode === 'WAITING' || queueMode === 'ACTIVE'
        ? `<button class="queue-remove-btn" type="button" onclick="openQueueRedisTokenRemoveModal('${escapeJs(token)}')">제거</button>`
        : '';

      if (queueMode === 'ACTIVE') {
        tr.innerHTML = `
          <td class="queue-key" title="${escapeHtml(entry.token || '')}">${escapeHtml(entry.token || '-')}</td>
          <td>${escapeHtml(formatTime(entry.timestampMillis))}</td>
          <td>${escapeHtml(formatTtl(entry.ttlSeconds))}</td>
          <td class="queue-value" title="${escapeHtml(entry.value || '')}">${escapeHtml(entry.value || '-')}</td>
          <td class="queue-key" title="${escapeHtml(entry.key)}">${escapeHtml(entry.key)}</td>
          <td>${removeButton}</td>
        `;
      } else if (queueMode === 'TOKEN') {
        tr.innerHTML = `
          <td class="queue-key" title="${escapeHtml(entry.token || '')}">${escapeHtml(entry.token || '-')}</td>
          <td class="queue-key" title="${escapeHtml(entry.key)}">${escapeHtml(entry.key)}</td>
          <td class="queue-value" title="${escapeHtml(entry.value || '')}">${escapeHtml(entry.value || '-')}</td>
          <td>${escapeHtml(formatTtl(entry.ttlSeconds))}</td>
        `;
      } else {
        tr.innerHTML = `
          <td>${escapeHtml(entry.rank || '-')}</td>
          <td class="queue-value" title="${escapeHtml(entry.member || '')}">${escapeHtml(entry.member || '-')}</td>
          <td>${escapeHtml(formatTime(entry.timestampMillis))}</td>
          <td>${escapeHtml(entry.score ?? '-')}</td>
          <td class="queue-key" title="${escapeHtml(entry.key)}">${escapeHtml(entry.key)}</td>
          <td>${removeButton}</td>
        `;
      }
      tbody.appendChild(tr);
    });

    if (detectChanges) previousSnapshot = nextSnapshot;
    return changedCount;
  }

  function updateSummary(data, changedCount) {
    const target = queueMode === 'TOKEN'
      ? `Token ${inputValue('queue-token') || '-'}`
      : `Event ${inputValue('queue-event-id') || '-'} / ${queueMode}`;
    document.getElementById('queue-summary-target').textContent = target;
    document.getElementById('queue-summary-count').textContent = String(data.count ?? 0);
    document.getElementById('queue-summary-changed').textContent = String(changedCount);
    document.getElementById('queue-summary-time').textContent = new Date().toLocaleTimeString();
  }

  function defaultSortForMode(mode) {
    if (mode === 'ACTIVE') return { field: 'timestampMillis', direction: 'asc' };
    if (mode === 'TOKEN') return { field: 'token', direction: 'asc' };
    return { field: 'rank', direction: 'asc' };
  }

  function syncModeUi() {
    document.getElementById('queue-mode-waiting')?.classList.toggle('active', queueMode === 'WAITING');
    document.getElementById('queue-mode-active')?.classList.toggle('active', queueMode === 'ACTIVE');
    document.getElementById('queue-mode-token')?.classList.toggle('active', queueMode === 'TOKEN');
    document.getElementById('queue-event-field')?.classList.toggle('is-hidden', queueMode === 'TOKEN');
    document.getElementById('queue-token-field')?.classList.toggle('is-hidden', queueMode !== 'TOKEN');
    document.getElementById('queue-limit-field')?.classList.toggle('is-hidden', queueMode === 'TOKEN');
    document.querySelector('.queue-table')?.classList.toggle('active-mode', queueMode === 'ACTIVE');
    renderTableHeader();
  }

  function resetSummary() {
    document.getElementById('queue-summary-target').textContent = '-';
    document.getElementById('queue-summary-count').textContent = '0';
    document.getElementById('queue-summary-changed').textContent = '0';
    document.getElementById('queue-summary-time').textContent = '-';
  }

  function resetTable() {
    const tbody = document.getElementById('queue-table-body');
    if (tbody) {
      tbody.innerHTML = '';
    }
  }

  function stopAutoRefresh() {
    autoRefreshEnabled = false;
    if (autoRefreshTimer) {
      clearInterval(autoRefreshTimer);
      autoRefreshTimer = null;
    }
    syncAutoRefreshToggle();
  }

  window.setQueueRedisMode = function (mode) {
    queueMode = ['WAITING', 'ACTIVE', 'TOKEN'].includes(mode) ? mode : 'WAITING';
    queueSort = defaultSortForMode(queueMode);
    lastEntries = [];
    previousSnapshot = new Map();
    stopAutoRefresh();
    resetSummary();
    resetTable();
    syncModeUi();
  };

  window.sortQueueRedis = function (field) {
    if (queueSort.field === field) {
      queueSort.direction = queueSort.direction === 'asc' ? 'desc' : 'asc';
    } else {
      queueSort = { field, direction: 'asc' };
    }
    renderTableHeader();
    renderEntries(lastEntries, { detectChanges: false });
  };

  window.clearQueueSnapshot = function () {
    previousSnapshot = new Map();
    document.getElementById('queue-summary-changed').textContent = '0';
    showToast('\uBCC0\uACBD \uAC10\uC9C0 \uAE30\uC900\uC744 \uCD08\uAE30\uD654\uD588\uC2B5\uB2C8\uB2E4.');
  };

  window.openQueueRedisTokenRemoveModal = function (token) {
    const eventId = inputValue('queue-event-id');
    if (!eventId) {
      showToast('Event ID\uB97C \uC785\uB825\uD574\uC8FC\uC138\uC694.', true);
      return;
    }

    if (!token || !['WAITING', 'ACTIVE'].includes(queueMode)) {
      showToast('\uC81C\uAC70\uD560 \uD1A0\uD070\uC774 \uC5C6\uC2B5\uB2C8\uB2E4.', true);
      return;
    }

    pendingRemoveToken = { eventId, token, mode: queueMode };
    document.getElementById('queue-token-remove-summary').innerHTML = `
      <div>Mode: <strong>${escapeHtml(queueMode)}</strong></div>
      <div>Event ID: <strong>${escapeHtml(eventId)}</strong></div>
      <div>Token: <strong>${escapeHtml(token)}</strong></div>
    `;
    document.getElementById('queue-token-remove-modal').style.display = 'flex';
  };

  window.cancelQueueRedisTokenRemove = function () {
    pendingRemoveToken = null;
    document.getElementById('queue-token-remove-modal').style.display = 'none';
  };

  window.submitQueueRedisTokenRemove = async function () {
    if (!pendingRemoveToken) {
      return;
    }

    const { eventId, token, mode } = pendingRemoveToken;
    document.getElementById('queue-token-remove-modal').style.display = 'none';
    pendingRemoveToken = null;

    try {
      const params = new URLSearchParams();
      params.set('mode', mode);
      const res = await Fetch(
        `${QUEUE_REDIS_URL}/event/${encodeURIComponent(eventId)}/token/${encodeURIComponent(token)}?${params.toString()}`,
        { method: 'DELETE', headers }
      );

      if (!res.ok) {
        showToast('\uD1A0\uD070 \uC81C\uAC70\uC5D0 \uC2E4\uD328\uD588\uC2B5\uB2C8\uB2E4.', true);
        return;
      }

      showToast('\uD1A0\uD070\uC744 \uC81C\uAC70\uD588\uC2B5\uB2C8\uB2E4.');
      previousSnapshot = new Map();
      await window.loadQueueRedis();
    } catch (e) {
      console.error(e);
      showToast('\uD1A0\uD070 \uC81C\uAC70\uC5D0 \uC2E4\uD328\uD588\uC2B5\uB2C8\uB2E4.', true);
    }
  };

  window.loadQueueRedis = async function () {
    const url = buildRequestUrl();
    if (!url) return;

    try {
      const res = await Fetch(url, { method: 'GET', headers });
      if (!res.ok) {
        showToast('Queue Redis \uC870\uD68C\uC5D0 \uC2E4\uD328\uD588\uC2B5\uB2C8\uB2E4.', true);
        return;
      }
      const data = await res.json();
      lastEntries = data.entries || [];
      const changedCount = renderEntries(lastEntries);
      updateSummary(data, changedCount);
    } catch (e) {
      console.error(e);
      showToast('Queue Redis \uC870\uD68C\uC5D0 \uC2E4\uD328\uD588\uC2B5\uB2C8\uB2E4.', true);
    }
  };

  function buildRequestUrl() {
    if (queueMode === 'TOKEN') {
      const token = inputValue('queue-token');
      if (!token) {
        showToast('Token\uC744 \uC785\uB825\uD574\uC8FC\uC138\uC694.', true);
        return null;
      }
      return `${QUEUE_REDIS_URL}/token/${encodeURIComponent(token)}`;
    }

    const eventId = inputValue('queue-event-id');
    if (!eventId) {
      showToast('Event ID\uB97C \uC785\uB825\uD574\uC8FC\uC138\uC694.', true);
      return null;
    }

    const params = new URLSearchParams();
    params.set('mode', queueMode);
    params.set('limit', limitValue());
    return `${QUEUE_REDIS_URL}/event/${encodeURIComponent(eventId)}?${params.toString()}`;
  }

  function syncAutoRefreshToggle() {
    document.getElementById('queue-auto-start')?.classList.toggle('active', autoRefreshEnabled);
    document.getElementById('queue-auto-stop')?.classList.toggle('active', !autoRefreshEnabled);
  }

  function restartAutoRefresh() {
    if (autoRefreshTimer) {
      clearInterval(autoRefreshTimer);
      autoRefreshTimer = null;
    }
    if (autoRefreshEnabled) {
      autoRefreshTimer = setInterval(() => window.loadQueueRedis(), refreshIntervalMillis());
    }
    syncAutoRefreshToggle();
  }

  window.setQueueAutoRefresh = function (enabled) {
    autoRefreshEnabled = Boolean(enabled);
    restartAutoRefresh();
  };

  window.updateQueueAutoRefreshInterval = function () {
    restartAutoRefresh();
  };

  window.addEventListener('admin:fragment-loaded', function cleanupQueueTimer(event) {
    if (event.detail?.menuName === 'queueRedis') return;
    autoRefreshEnabled = false;
    if (autoRefreshTimer) {
      clearInterval(autoRefreshTimer);
      autoRefreshTimer = null;
    }
    window.removeEventListener('admin:fragment-loaded', cleanupQueueTimer);
  });

  syncModeUi();
  syncAutoRefreshToggle();
})();
