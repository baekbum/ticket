(function () {
  const AUDIT_LOG_URL = `${base()}/admin/api/${API.VERSION}/audit-log`;
  const headers = { 'Content-Type': 'application/json' };

  let currentAuditLogList = [];
  let currentSearchFilters = {
    occurredFrom: null,
    occurredTo: null,
    serviceName: null,
    actorId: null,
    action: null,
    targetType: null,
    targetId: null,
    result: null
  };
  let serverTotalPages = 1;

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

  function toStartOfDay(value) {
    if (!value) return null;
    return `${value}T00:00:00`;
  }

  function toEndOfDay(value) {
    if (!value) return null;
    return `${value}T23:59:59`;
  }

  function formatDateTime(value) {
    if (!value) return '-';
    return String(value).replace('T', ' ').slice(0, 19);
  }

  function formatDateInput(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  function formatJson(value) {
    if (value == null) return '-';
    try {
      return JSON.stringify(value, null, 2);
    } catch (e) {
      return String(value);
    }
  }

  function resultBadge(result) {
    const normalized = String(result || '').toUpperCase();
    const success = normalized === 'SUCCESS';
    const className = success ? 'badge-success' : 'badge-fail';
    return `<span class="badge ${className}">${escapeHtml(result || '-')}</span>`;
  }

  function serviceDisplayName(serviceName) {
    const displayNames = {
      'auth-service': '권한',
      'user-service': '유저',
      'ticket-service': '티켓'
    };
    return displayNames[serviceName] || serviceName || '-';
  }

  function renderContextFilter() {
    const area = document.getElementById('audit-context-filter');
    if (!area) return;

    if (!currentSearchFilters.targetType && !currentSearchFilters.targetId) {
      area.style.display = 'none';
      area.innerHTML = '';
      return;
    }

    area.style.display = 'flex';
    area.innerHTML = `
      <span>
        대상 필터 적용 중:
        <strong>${escapeHtml(currentSearchFilters.targetType || '-')}</strong>
        /
        <strong>${escapeHtml(currentSearchFilters.targetId || '-')}</strong>
      </span>
      <button class="btn btn-sm btn-outline" onclick="clearAuditTargetFilter()">
        필터 해제
      </button>
    `;
  }

  function buildCond(pageZeroIndexed) {
    const pageSize = parseInt(document.getElementById('pagination-size').value, 10);
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

  window.loadAuditLogList = async function (pageZeroIndexed = 0) {
    try {
      const res = await Fetch(`${AUDIT_LOG_URL}/select`, {
        method: 'POST',
        headers,
        body: JSON.stringify(buildCond(pageZeroIndexed))
      });

      if (!res.ok) {
        showToast('감사 로그 조회에 실패했습니다.', true);
        return;
      }

      const paged = await res.json();
      currentAuditLogList = paged.content || [];
      serverTotalPages = Math.max(paged.page?.totalPages || paged.totalPages || 1, 1);
      const totalCount = paged.totalElements ?? paged.page?.totalElements ?? currentAuditLogList.length;
      const pageSize = parseInt(document.getElementById('pagination-size').value, 10);

      document.getElementById('pagination-total').textContent = serverTotalPages;
      document.getElementById('pagination-current').value = pageZeroIndexed + 1;
      document.getElementById('pagination-total-count').textContent = totalCount;

      const tbody = document.getElementById('audit-log-table-body');
      tbody.innerHTML = '';

      if (currentAuditLogList.length === 0) {
        tbody.innerHTML = `<tr><td colspan="13" style="text-align:center;color:var(--text-muted);padding:2rem;">조회된 감사 로그가 없습니다.</td></tr>`;
        return;
      }

      currentAuditLogList.forEach((auditLog, index) => {
        const rowNumber = pageZeroIndexed * pageSize + index + 1;
        const tr = document.createElement('tr');
        tr.onclick = () => openAuditLogDetailModal(auditLog.id);
        tr.innerHTML = `
          <td style="text-align:center;color:var(--text-muted);">${rowNumber}</td>
          <td><strong>${escapeHtml(auditLog.id)}</strong></td>
          <td>${escapeHtml(formatDateTime(auditLog.occurredAt))}</td>
          <td title="${escapeHtml(auditLog.serviceName)}">${escapeHtml(serviceDisplayName(auditLog.serviceName))}</td>
          <td>${escapeHtml(auditLog.actorType || '-')}</td>
          <td title="${escapeHtml(auditLog.actorId)}">${escapeHtml(auditLog.actorId || '-')}</td>
          <td title="${escapeHtml(auditLog.action)}">${escapeHtml(auditLog.action || '-')}</td>
          <td>${escapeHtml(auditLog.targetType || '-')}</td>
          <td title="${escapeHtml(auditLog.targetId)}">${escapeHtml(auditLog.targetId || '-')}</td>
          <td>${resultBadge(auditLog.result)}</td>
          <td title="${escapeHtml(auditLog.requestId)}">${escapeHtml(auditLog.requestId || '-')}</td>
          <td title="${escapeHtml(auditLog.traceId)}">${escapeHtml(auditLog.traceId || '-')}</td>
          <td class="actions">
            <button class="btn btn-sm btn-outline" onclick="event.stopPropagation(); openAuditLogDetailModal(${Number(auditLog.id)})">상세</button>
          </td>
        `;
        tbody.appendChild(tr);
      });
    } catch (e) {
      console.error('Audit log list load failed', e);
      showToast('감사 로그 통신 오류가 발생했습니다.', true);
    }
  };

  window.triggerAuditLogSearch = function () {
    currentSearchFilters = {
      occurredFrom: toStartOfDay(inputValue('audit-search-from')),
      occurredTo: toEndOfDay(inputValue('audit-search-to')),
      serviceName: inputValue('audit-search-service-name') || null,
      actorId: inputValue('audit-search-actor-id') || null,
      action: inputValue('audit-search-action') || null,
      targetType: currentSearchFilters.targetType,
      targetId: currentSearchFilters.targetId,
      result: inputValue('audit-search-result') || null
    };
    syncAuditResultFilters();
    renderContextFilter();
    loadAuditLogList(0);
  };

  window.resetAuditLogSearch = function () {
    currentSearchFilters = {
      occurredFrom: null,
      occurredTo: null,
      serviceName: null,
      actorId: null,
      action: null,
      targetType: null,
      targetId: null,
      result: null
    };
    setValue('audit-search-period', '');
    setValue('audit-search-from', '');
    setValue('audit-search-to', '');
    setValue('audit-search-service-name', '');
    setValue('audit-search-actor-id', '');
    setValue('audit-search-action', '');
    setValue('audit-search-result', '');
    syncAuditResultFilters();
    renderContextFilter();
    loadAuditLogList(0);
  };

  window.clearAuditTargetFilter = function () {
    currentSearchFilters.targetType = null;
    currentSearchFilters.targetId = null;
    renderContextFilter();
    loadAuditLogList(0);
  };

  function applyAuditSearchContext(context) {
    if (!context) return;

    currentSearchFilters = {
      occurredFrom: context.occurredFrom || null,
      occurredTo: context.occurredTo || null,
      serviceName: context.serviceName || null,
      actorId: context.actorId || null,
      action: context.action || null,
      targetType: context.targetType || null,
      targetId: context.targetId || null,
      result: context.result || null
    };

    setValue('audit-search-period', '');
    setValue('audit-search-from', '');
    setValue('audit-search-to', '');
    setValue('audit-search-service-name', currentSearchFilters.serviceName);
    setValue('audit-search-actor-id', currentSearchFilters.actorId);
    setValue('audit-search-action', currentSearchFilters.action);
    setValue('audit-search-result', currentSearchFilters.result);
    syncAuditResultFilters();
    renderContextFilter();
    loadAuditLogList(0);
  }

  window.applyAuditLogPeriodPreset = function () {
    const preset = inputValue('audit-search-period');
    if (!preset) {
      return;
    }

    const today = new Date();
    const fromDate = new Date(today);

    if (preset === '7days') {
      fromDate.setDate(today.getDate() - 6);
    } else if (preset === '30days') {
      fromDate.setDate(today.getDate() - 29);
    }

    setValue('audit-search-from', formatDateInput(fromDate));
    setValue('audit-search-to', formatDateInput(today));
    triggerAuditLogSearch();
  };

  window.quickAuditResult = function (button) {
    const result = button?.dataset?.result || '';
    currentSearchFilters.result = result || null;
    setValue('audit-search-result', result);
    syncAuditResultFilters();
    loadAuditLogList(0);
  };

  function syncAuditResultFilters() {
    document.querySelectorAll('.audit-filter').forEach(filter => {
      filter.classList.toggle('active', (filter.dataset.result || '') === (currentSearchFilters.result || ''));
    });
  }

  window.openAuditLogDetailModal = async function (id) {
    let auditLog = currentAuditLogList.find(item => Number(item.id) === Number(id));

    if (!auditLog) {
      try {
        const res = await Fetch(`${AUDIT_LOG_URL}/select/id/${id}`, { method: 'GET' });
        if (!res.ok) {
          showToast('감사 로그 상세 조회에 실패했습니다.', true);
          return;
        }
        auditLog = await res.json();
      } catch (e) {
        console.error('Audit log detail load failed', e);
        showToast('감사 로그 상세 통신 오류가 발생했습니다.', true);
        return;
      }
    }

    document.getElementById('audit-detail-id').textContent = auditLog.id || '-';
    document.getElementById('audit-detail-grid').innerHTML = [
      ['발생 일시', formatDateTime(auditLog.occurredAt)],
      ['서비스', serviceDisplayName(auditLog.serviceName)],
      ['작업자 타입', auditLog.actorType],
      ['작업자 ID', auditLog.actorId],
      ['작업자명', auditLog.actorName],
      ['액션', auditLog.action],
      ['대상 타입', auditLog.targetType],
      ['대상 ID', auditLog.targetId],
      ['결과', auditLog.result],
      ['사유', auditLog.reason],
      ['IP', auditLog.ipAddress],
      ['User-Agent', auditLog.userAgent],
      ['Request ID', auditLog.requestId],
      ['Trace ID', auditLog.traceId],
      ['생성 일시', formatDateTime(auditLog.createdAt)]
    ].map(([label, value]) => `
      <div class="audit-detail-item">
        <span>${escapeHtml(label)}</span>
        <strong title="${escapeHtml(value)}">${escapeHtml(value || '-')}</strong>
      </div>
    `).join('');

    document.getElementById('audit-before-data').textContent = formatJson(auditLog.beforeData);
    document.getElementById('audit-after-data').textContent = formatJson(auditLog.afterData);
    document.getElementById('audit-metadata').textContent = formatJson(auditLog.metadata);
    document.getElementById('audit-log-detail-modal').style.display = 'flex';
  };

  window.closeAuditLogDetailModal = function () {
    document.getElementById('audit-log-detail-modal').style.display = 'none';
  };

  window.Pagination.register({
    load: window.loadAuditLogList,
    getTotalPages: () => serverTotalPages
  });

  window.addEventListener('admin:fragment-loaded', event => {
    if (event.detail?.menuName !== 'auditLog') return;
    applyAuditSearchContext(event.detail?.context?.auditSearch);
  });

  setValue('audit-search-period', '7days');
  renderContextFilter();
  applyAuditLogPeriodPreset();
})();
