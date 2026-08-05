(function () {
  const FAILURE_URL = `${base()}/admin/api/${API.VERSION}/seat/cache/sync-failures`;
  const headers = { 'Content-Type': 'application/json' };

  let currentFailures = [];
  let currentDetail = null;
  let currentSearchFilters = {
    operation: null,
    keyPrefix: null,
    status: null
  };
  let serverTotalPages = 1;
  let confirmResolver = null;

  function inputValue(id) {
    return document.getElementById(id)?.value?.trim() || '';
  }

  function setValue(id, value) {
    const el = document.getElementById(id);
    if (el) el.value = value ?? '';
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  function formatDateTime(value) {
    if (!value) return '-';
    return String(value).replace('T', ' ').slice(0, 19);
  }

  function statusBadge(status) {
    const normalized = String(status || 'UNKNOWN').toLowerCase();
    return `<span class="status-badge status-${escapeHtml(normalized)}">${escapeHtml(status || 'UNKNOWN')}</span>`;
  }

  function openSeatCacheFailureConfirm(action, failure) {
    const isRetry = action === 'retry';
    document.getElementById('seat-cache-failure-confirm-title').textContent = isRetry
      ? 'Redis 좌석 캐시 재처리 확인'
      : 'Redis 보정 이력 폐기 확인';
    document.getElementById('seat-cache-failure-confirm-message').textContent = isRetry
      ? 'DB 기준 상태로 Redis 좌석 캐시를 다시 동기화합니다.'
      : '선택한 Redis 보정 이력을 운영상 폐기 처리합니다. Redis 값은 변경하지 않습니다.';
    document.getElementById('seat-cache-failure-confirm-summary').innerHTML = `
      <div>이력 ID: <strong>${escapeHtml(failure.id)}</strong></div>
      <div>Redis Key: <strong>${escapeHtml(failure.redisKeys || '-')}</strong></div>
      <div>Target Value: <strong>${escapeHtml(failure.targetValue || '-')}</strong></div>
      <div>현재 상태: <strong>${escapeHtml(failure.status || '-')}</strong></div>
    `;

    const submitButton = document.getElementById('seat-cache-failure-confirm-submit-btn');
    submitButton.className = isRetry ? 'btn' : 'btn btn-danger';
    submitButton.textContent = isRetry ? '재처리 실행' : '폐기 처리';
    document.getElementById('seat-cache-failure-confirm-modal').style.display = 'flex';

    return new Promise(resolve => {
      confirmResolver = resolve;
    });
  }

  function buildCond(pageZeroIndexed) {
    const pageSize = parseInt(document.getElementById('pagination-size').value, 10);
    return {
      page: pageZeroIndexed,
      size: pageSize,
      ...currentSearchFilters
    };
  }

  function syncQuickFilters() {
    document.querySelectorAll('.quick-filter').forEach(filter => {
      filter.classList.toggle('active', (filter.dataset.status || '') === (currentSearchFilters.status || ''));
    });
  }

  window.loadSeatCacheFailureList = async function (pageZeroIndexed = 0) {
    try {
      const res = await Fetch(`${FAILURE_URL}/select`, {
        method: 'POST',
        headers,
        body: JSON.stringify(buildCond(pageZeroIndexed))
      });
      if (!res.ok) {
        await showResponseError(res, 'Redis 보정 이력 조회에 실패했습니다.');
        return;
      }

      const paged = await res.json();
      currentFailures = paged.content || [];
      serverTotalPages = Math.max(paged.page?.totalPages || paged.totalPages || 1, 1);
      const totalCount = paged.page?.totalElements ?? paged.totalElements ?? currentFailures.length;
      const pageSize = parseInt(document.getElementById('pagination-size').value, 10);

      document.getElementById('pagination-total').textContent = serverTotalPages;
      document.getElementById('pagination-current').value = pageZeroIndexed + 1;
      document.getElementById('pagination-total-count').textContent = totalCount;

      const tbody = document.getElementById('seat-cache-failure-table-body');
      tbody.innerHTML = '';

      if (currentFailures.length === 0) {
        tbody.innerHTML = '<tr><td colspan="11" class="empty-cell">조회된 Redis 보정 이력이 없습니다.</td></tr>';
        return;
      }

      currentFailures.forEach((failure, index) => {
        const rowNumber = pageZeroIndexed * pageSize + index + 1;
        const tr = document.createElement('tr');
        tr.onclick = () => openSeatCacheFailureDetailModal(failure.id);
        tr.innerHTML = `
          <td style="text-align:center;color:var(--text-muted);">${rowNumber}</td>
          <td><strong>${escapeHtml(failure.id)}</strong></td>
          <td title="${escapeHtml(failure.operation)}">${escapeHtml(failure.operation)}</td>
          <td title="${escapeHtml(failure.keyPrefix)}">${escapeHtml(failure.keyPrefix)}</td>
          <td title="${escapeHtml(failure.redisKeys)}">${escapeHtml(failure.redisKeys || '-')}</td>
          <td title="${escapeHtml(failure.targetValue)}">${escapeHtml(failure.targetValue || '-')}</td>
          <td>${statusBadge(failure.status)}</td>
          <td>${escapeHtml(failure.retryCount)}</td>
          <td>${escapeHtml(formatDateTime(failure.createdAt))}</td>
          <td>${escapeHtml(formatDateTime(failure.lastFailedAt))}</td>
          <td class="actions">
            <button class="btn btn-sm btn-outline" onclick="event.stopPropagation(); openSeatCacheFailureDetailModal(${Number(failure.id)})">상세</button>
          </td>
        `;
        tbody.appendChild(tr);
      });
    } catch (e) {
      console.error('Seat cache failure list load failed', e);
      showToast('Redis 보정 이력 통신 오류가 발생했습니다.', true);
    }
  };

  window.triggerSeatCacheFailureSearch = function () {
    currentSearchFilters = {
      operation: inputValue('seat-cache-failure-operation') || null,
      keyPrefix: inputValue('seat-cache-failure-key-prefix') || null,
      status: inputValue('seat-cache-failure-status') || null
    };
    syncQuickFilters();
    loadSeatCacheFailureList(0);
  };

  window.resetSeatCacheFailureSearch = function () {
    currentSearchFilters = {
      operation: null,
      keyPrefix: null,
      status: null
    };
    setValue('seat-cache-failure-operation', '');
    setValue('seat-cache-failure-key-prefix', '');
    setValue('seat-cache-failure-status', '');
    syncQuickFilters();
    loadSeatCacheFailureList(0);
  };

  window.quickSeatCacheFailureStatus = function (button) {
    const status = button?.dataset?.status || '';
    currentSearchFilters.status = status || null;
    setValue('seat-cache-failure-status', status);
    syncQuickFilters();
    loadSeatCacheFailureList(0);
  };

  window.openSeatCacheFailureDetailModal = async function (id) {
    let failure = currentFailures.find(item => Number(item.id) === Number(id));

    if (!failure) {
      try {
        const res = await Fetch(`${FAILURE_URL}/${id}`, { method: 'GET' });
        if (!res.ok) {
          await showResponseError(res, 'Redis 보정 이력 상세 조회에 실패했습니다.');
          return;
        }
        failure = await res.json();
      } catch (e) {
        console.error('Seat cache failure detail load failed', e);
        showToast('Redis 보정 이력 상세 통신 오류가 발생했습니다.', true);
        return;
      }
    }

    currentDetail = failure;
    const pending = failure.status === 'PENDING';
    document.getElementById('seat-cache-failure-detail-id').textContent = failure.id || '-';
    document.getElementById('seat-cache-failure-detail-grid').innerHTML = [
      ['Operation', failure.operation],
      ['Key Prefix', failure.keyPrefix],
      ['Target Value', failure.targetValue],
      ['상태', failure.status],
      ['재시도 횟수', failure.retryCount],
      ['생성 시각', formatDateTime(failure.createdAt)],
      ['최근 실패', formatDateTime(failure.lastFailedAt)],
      ['처리 시각', formatDateTime(failure.resolvedAt)]
    ].map(([label, value]) => `
      <div class="detail-item">
        <span>${escapeHtml(label)}</span>
        <strong title="${escapeHtml(value)}">${escapeHtml(value ?? '-')}</strong>
      </div>
    `).join('');

    document.getElementById('seat-cache-failure-detail-keys').textContent = failure.redisKeys || '-';
    document.getElementById('seat-cache-failure-detail-message').textContent = failure.failureMessage || '-';
    document.getElementById('seat-cache-failure-detail-resolved').textContent = failure.resolvedMessage || '-';
    document.getElementById('seat-cache-failure-retry-btn').style.display = pending ? 'inline-flex' : 'none';
    document.getElementById('seat-cache-failure-discard-btn').style.display = pending ? 'inline-flex' : 'none';
    document.getElementById('seat-cache-failure-detail-modal').style.display = 'flex';
  };

  window.handleSeatCacheFailure = async function (action) {
    if (!currentDetail) {
      showToast('Redis 보정 이력을 먼저 선택하세요.', true);
      return;
    }

    const isRetry = action === 'retry';
    const confirmed = await openSeatCacheFailureConfirm(action, currentDetail);
    if (!confirmed) {
      return;
    }

    try {
      const res = await Fetch(`${FAILURE_URL}/${currentDetail.id}/${action}`, { method: 'POST' });
      if (!res.ok) {
        await showResponseError(res, isRetry ? 'Redis 보정 재처리에 실패했습니다.' : 'Redis 보정 이력 폐기에 실패했습니다.');
        return;
      }

      const result = await res.json();
      showToast(result.message || (isRetry ? 'Redis 보정 재처리를 완료했습니다.' : 'Redis 보정 이력을 폐기했습니다.'));
      document.getElementById('seat-cache-failure-detail-modal').style.display = 'none';
      currentDetail = null;
      await loadSeatCacheFailureList(parseInt(document.getElementById('pagination-current').value, 10) - 1);
    } catch (e) {
      console.error('Seat cache failure handle failed', e);
      showToast('Redis 보정 처리 통신 오류가 발생했습니다.', true);
    }
  };

  window.closeSeatCacheFailureDetailModal = function () {
    document.getElementById('seat-cache-failure-detail-modal').style.display = 'none';
    currentDetail = null;
  };

  window.cancelSeatCacheFailureConfirm = function () {
    document.getElementById('seat-cache-failure-confirm-modal').style.display = 'none';
    if (confirmResolver) {
      confirmResolver(false);
      confirmResolver = null;
    }
  };

  window.submitSeatCacheFailureConfirm = function () {
    document.getElementById('seat-cache-failure-confirm-modal').style.display = 'none';
    if (confirmResolver) {
      confirmResolver(true);
      confirmResolver = null;
    }
  };

  window.Pagination.register({
    load: window.loadSeatCacheFailureList,
    getTotalPages: () => serverTotalPages
  });

  currentSearchFilters.status = 'PENDING';
  setValue('seat-cache-failure-status', 'PENDING');
  syncQuickFilters();
  loadSeatCacheFailureList(0);
})();
