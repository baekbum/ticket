(function () {
  const LOGIN_LOG_URL = `${base()}/admin/api/${API.VERSION}/login-log`;
  const headers = { 'Content-Type': 'application/json' };

  let currentLoginLogList = [];
  let currentSearchFilters = emptyFilters();
  let serverTotalPages = 1;

  function emptyFilters() {
    return {
      occurredFrom: null,
      occurredTo: null,
      loginId: null,
      ipAddress: null,
      result: null,
      authMethod: null
    };
  }

  function inputValue(id) {
    return document.getElementById(id)?.value?.trim() || '';
  }

  function setValue(id, value) {
    const element = document.getElementById(id);
    if (element) element.value = value ?? '';
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  function toStartOfDay(value) {
    return value ? `${value}T00:00:00` : null;
  }

  function toEndOfDay(value) {
    return value ? `${value}T23:59:59` : null;
  }

  function formatDateTime(value) {
    return value ? String(value).replace('T', ' ').slice(0, 19) : '-';
  }

  function formatDateInput(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  function resultBadge(result) {
    const success = String(result || '').toUpperCase() === 'SUCCESS';
    return `<span class="badge ${success ? 'badge-success' : 'badge-fail'}">${escapeHtml(result || '-')}</span>`;
  }

  function buildCond(pageZeroIndexed) {
    const pageSize = Number.parseInt(document.getElementById('pagination-size').value, 10);
    const cond = {
      page: pageZeroIndexed,
      size: pageSize,
      sort: ['occurredAt-desc', 'id-desc']
    };

    Object.entries(currentSearchFilters).forEach(([key, value]) => {
      if (value) cond[key] = value;
    });
    return cond;
  }

  window.loadLoginLogList = async function (pageZeroIndexed = 0) {
    try {
      const response = await Fetch(`${LOGIN_LOG_URL}/select`, {
        method: 'POST',
        headers,
        body: JSON.stringify(buildCond(pageZeroIndexed))
      });

      if (!response.ok) {
        showToast('로그인 로그 조회에 실패했습니다.', true);
        return;
      }

      const paged = await response.json();
      currentLoginLogList = paged.content || [];
      serverTotalPages = Math.max(paged.page?.totalPages || 1, 1);
      const totalCount = paged.page?.totalElements ?? currentLoginLogList.length;
      const pageSize = Number.parseInt(document.getElementById('pagination-size').value, 10);

      document.getElementById('pagination-total').textContent = serverTotalPages;
      document.getElementById('pagination-current').value = pageZeroIndexed + 1;
      document.getElementById('pagination-total-count').textContent = totalCount;

      const tbody = document.getElementById('login-log-table-body');
      tbody.innerHTML = '';
      if (currentLoginLogList.length === 0) {
        tbody.innerHTML = '<tr><td colspan="11" class="login-empty-row">조회된 로그인 로그가 없습니다.</td></tr>';
        return;
      }

      currentLoginLogList.forEach((loginLog, index) => {
        const rowNumber = pageZeroIndexed * pageSize + index + 1;
        const row = document.createElement('tr');
        row.onclick = () => openLoginLogDetailModal(loginLog.id);
        row.innerHTML = `
          <td class="login-row-number">${rowNumber}</td>
          <td><strong>${escapeHtml(loginLog.id)}</strong></td>
          <td>${escapeHtml(formatDateTime(loginLog.occurredAt))}</td>
          <td title="${escapeHtml(loginLog.loginId)}">${escapeHtml(loginLog.loginId || '-')}</td>
          <td>${escapeHtml(loginLog.authId ?? '-')}</td>
          <td>${resultBadge(loginLog.result)}</td>
          <td>${escapeHtml(loginLog.authMethod || '-')}</td>
          <td title="${escapeHtml(loginLog.ipAddress)}">${escapeHtml(loginLog.ipAddress || '-')}</td>
          <td title="${escapeHtml(loginLog.failureReason)}">${escapeHtml(loginLog.failureReason || '-')}</td>
          <td title="${escapeHtml(loginLog.requestId)}">${escapeHtml(loginLog.requestId || '-')}</td>
          <td class="actions"><button class="btn btn-sm btn-outline" onclick="event.stopPropagation(); openLoginLogDetailModal(${Number(loginLog.id)})">상세</button></td>
        `;
        tbody.appendChild(row);
      });
    } catch (error) {
      console.error('Login log list load failed', error);
      showToast('로그인 로그 통신 오류가 발생했습니다.', true);
    }
  };

  window.triggerLoginLogSearch = function () {
    currentSearchFilters = {
      occurredFrom: toStartOfDay(inputValue('login-search-from')),
      occurredTo: toEndOfDay(inputValue('login-search-to')),
      loginId: inputValue('login-search-login-id') || null,
      ipAddress: inputValue('login-search-ip') || null,
      result: inputValue('login-search-result') || null,
      authMethod: inputValue('login-search-auth-method') || null
    };
    syncResultFilters();
    loadLoginLogList(0);
  };

  window.resetLoginLogSearch = function () {
    currentSearchFilters = emptyFilters();
    ['login-search-period', 'login-search-from', 'login-search-to', 'login-search-login-id',
      'login-search-ip', 'login-search-result', 'login-search-auth-method'].forEach(id => setValue(id, ''));
    syncResultFilters();
    loadLoginLogList(0);
  };

  window.applyLoginLogPeriodPreset = function () {
    const preset = inputValue('login-search-period');
    if (!preset) return;

    const today = new Date();
    const fromDate = new Date(today);
    if (preset === '7days') fromDate.setDate(today.getDate() - 6);
    else if (preset === '30days') fromDate.setDate(today.getDate() - 29);

    setValue('login-search-from', formatDateInput(fromDate));
    setValue('login-search-to', formatDateInput(today));
    triggerLoginLogSearch();
  };

  window.quickLoginResult = function (button) {
    const result = button?.dataset?.result || '';
    currentSearchFilters.result = result || null;
    setValue('login-search-result', result);
    syncResultFilters();
    loadLoginLogList(0);
  };

  function syncResultFilters() {
    document.querySelectorAll('.audit-filter').forEach(filter => {
      filter.classList.toggle('active', (filter.dataset.result || '') === (currentSearchFilters.result || ''));
    });
  }

  window.openLoginLogDetailModal = async function (id) {
    let loginLog = currentLoginLogList.find(item => Number(item.id) === Number(id));
    if (!loginLog) {
      try {
        const response = await Fetch(`${LOGIN_LOG_URL}/select/id/${id}`, { method: 'GET' });
        if (!response.ok) {
          showToast('로그인 로그 상세 조회에 실패했습니다.', true);
          return;
        }
        loginLog = await response.json();
      } catch (error) {
        console.error('Login log detail load failed', error);
        showToast('로그인 로그 상세 통신 오류가 발생했습니다.', true);
        return;
      }
    }

    document.getElementById('login-detail-id').textContent = loginLog.id || '-';
    const details = [
      ['발생 일시', formatDateTime(loginLog.occurredAt)],
      ['저장 일시', formatDateTime(loginLog.createdAt)],
      ['로그인 ID', loginLog.loginId],
      ['계정 PK', loginLog.authId],
      ['결과', loginLog.result],
      ['인증 방식', loginLog.authMethod],
      ['실패 사유', loginLog.failureReason],
      ['IP 주소', loginLog.ipAddress],
      ['User-Agent', loginLog.userAgent],
      ['Event ID', loginLog.eventId],
      ['Session ID', loginLog.sessionId],
      ['Request ID', loginLog.requestId],
      ['Trace ID', loginLog.traceId]
    ];
    document.getElementById('login-detail-grid').innerHTML = details.map(([label, value]) => `
      <div class="audit-detail-item">
        <span>${escapeHtml(label)}</span>
        <strong title="${escapeHtml(value)}">${escapeHtml(value ?? '-')}</strong>
      </div>
    `).join('');
    document.getElementById('login-log-detail-modal').style.display = 'flex';
  };

  window.closeLoginLogDetailModal = function () {
    document.getElementById('login-log-detail-modal').style.display = 'none';
  };

  window.Pagination.register({
    load: window.loadLoginLogList,
    getTotalPages: () => serverTotalPages
  });

  window.addEventListener('admin:fragment-loaded', event => {
    if (event.detail?.menuName !== 'loginLog') return;
    setValue('login-search-period', '7days');
    applyLoginLogPeriodPreset();
  });
})();
