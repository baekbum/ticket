(function () {
  const FAILURE_METRICS_URL = `${base()}/admin/api/${API.VERSION}/manage/monitoring/failure-metrics`;
  const LEVEL_LABELS = {
    NORMAL: '정상',
    WARNING: '경고',
    CRITICAL: '위험',
    UNKNOWN: '확인불가'
  };
  let autoRefreshTimer = null;
  let isLoadingFailureMetrics = false;

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  function formatCollectedAt(value) {
    if (!value) return '수집 시각을 확인할 수 없습니다.';

    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return `수집 시각: ${value}`;
    }

    return `수집 시각: ${date.toLocaleString('ko-KR')}`;
  }

  function formatMetricValue(metric) {
    if (metric.value === null || metric.value === undefined || Number.isNaN(Number(metric.value))) {
      return '-';
    }

    const value = Number(metric.value);
    if (metric.unit === '%') {
      return value.toFixed(2);
    }
    if (metric.unit === 'seconds') {
      return value.toFixed(3);
    }
    if (Number.isInteger(value)) {
      return String(value);
    }
    return value.toFixed(2);
  }

  function levelClass(level) {
    return `failure-level-${String(level || 'UNKNOWN').toLowerCase()}`;
  }

  function hasDetails(metric) {
    return Array.isArray(metric.details) && metric.details.length > 0;
  }

  function renderOverview(metrics) {
    if (!document.getElementById('failure-normal-count')) return;

    const counts = metrics.reduce((acc, metric) => {
      const level = metric.level || 'UNKNOWN';
      acc[level] = (acc[level] || 0) + 1;
      return acc;
    }, {});

    document.getElementById('failure-normal-count').textContent = counts.NORMAL || 0;
    document.getElementById('failure-warning-count').textContent = counts.WARNING || 0;
    document.getElementById('failure-critical-count').textContent = counts.CRITICAL || 0;
    document.getElementById('failure-unknown-count').textContent = counts.UNKNOWN || 0;
  }

  function renderMetricCard(metric) {
    const level = metric.level || 'UNKNOWN';
    const levelLabel = LEVEL_LABELS[level] || level;
    const clickableClass = hasDetails(metric) ? ' is-clickable' : '';
    const detailHint = hasDetails(metric)
      ? '<div class="failure-detail-hint"><i class="ti ti-list-search"></i>클릭하면 상세 목록을 확인합니다.</div>'
      : '';
    const detailList = hasDetails(metric) ? renderMetricDetails(metric) : '';

    return `
      <article class="failure-metric-card${clickableClass}" data-metric-key="${escapeHtml(metric.key)}">
        <div class="failure-metric-top">
          <div>
            <h4>${escapeHtml(metric.name)}</h4>
            <p>${escapeHtml(metric.description)}</p>
          </div>
          <span class="failure-level ${levelClass(level)}">${escapeHtml(levelLabel)}</span>
        </div>
        <div class="failure-metric-value">
          <strong>${escapeHtml(formatMetricValue(metric))}</strong>
          <span>${escapeHtml(metric.unit)}</span>
        </div>
        <div class="failure-threshold">
          경고 ${escapeHtml(metric.warningThreshold)} / 위험 ${escapeHtml(metric.criticalThreshold)}
        </div>
        ${detailHint}
        ${detailList}
      </article>
    `;
  }

  function renderMetricDetails(metric) {
    return `
      <div class="failure-detail-list" hidden>
        ${metric.details.map(detail => `
          <div class="failure-detail-item">
            <strong>${escapeHtml(detail.name || 'unknown')}</strong>
            <span>job: ${escapeHtml(detail.job || '-')}</span>
            <span>instance: ${escapeHtml(detail.instance || '-')}</span>
          </div>
        `).join('')}
      </div>
    `;
  }

  function renderFailureMetrics(summary) {
    const metrics = summary.metrics || [];
    const grid = document.getElementById('failure-metric-grid');
    const collectedAt = document.getElementById('failure-summary-collected-at');
    if (!grid || !collectedAt) return;

    collectedAt.textContent = `${formatCollectedAt(summary.collectedAt)} · 범위: ${summary.range || '-'}`;
    renderOverview(metrics);

    if (metrics.length === 0) {
      grid.innerHTML = '<div class="failure-empty">조회된 장애 지표가 없습니다.</div>';
      return;
    }

    grid.innerHTML = metrics.map(renderMetricCard).join('');
    bindMetricDetailToggle(grid);
  }

  function bindMetricDetailToggle(grid) {
    grid.querySelectorAll('.failure-metric-card.is-clickable').forEach(card => {
      card.addEventListener('click', () => {
        const detailList = card.querySelector('.failure-detail-list');
        if (!detailList) return;

        const isOpen = card.classList.toggle('is-open');
        detailList.hidden = !isOpen;
      });
    });
  }

  window.openMonitoringDashboard = function (button) {
    const dashboardUrl = button?.dataset?.url;
    if (!dashboardUrl) return;

    window.open(dashboardUrl, '_blank', 'noopener,noreferrer');
  };

  window.openFailureSummaryWindow = function () {
    if (typeof openDashboardEmbedWindow !== 'function') return;
    openDashboardEmbedWindow('failureMonitoring', '장애 요약');
  };

  window.loadFailureMetrics = async function () {
    if (isLoadingFailureMetrics) return;

    const range = document.getElementById('failure-metric-range')?.value || '5m';
    const grid = document.getElementById('failure-metric-grid');
    if (!grid) return;

    isLoadingFailureMetrics = true;
    grid.innerHTML = '<div class="failure-loading">장애 지표를 조회하고 있습니다.</div>';

    try {
      const res = await Fetch(`${FAILURE_METRICS_URL}?range=${encodeURIComponent(range)}`, { method: 'GET' });
      if (!res.ok) {
        await showResponseError(res, '장애 지표 조회에 실패했습니다.');
        grid.innerHTML = '<div class="failure-empty">장애 지표 조회에 실패했습니다.</div>';
        return;
      }

      renderFailureMetrics(await res.json());
    } catch (e) {
      console.error('장애 지표 조회 실패', e);
      showToast('장애 지표 조회에 실패했습니다.', true);
      grid.innerHTML = '<div class="failure-empty">장애 지표 조회에 실패했습니다.</div>';
    } finally {
      isLoadingFailureMetrics = false;
    }
  };

  function stopFailureMetricAutoRefresh() {
    if (!autoRefreshTimer) return;
    clearInterval(autoRefreshTimer);
    autoRefreshTimer = null;
    setAutoRefreshActive(false);
  }

  function syncFailureMetricAutoRefresh() {
    stopFailureMetricAutoRefresh();

    const intervalSeconds = parseInt(document.getElementById('failure-auto-refresh-interval')?.value || '', 10);
    if (!Number.isFinite(intervalSeconds) || intervalSeconds <= 0) {
      return;
    }

    setAutoRefreshActive(true);
    autoRefreshTimer = setInterval(() => {
      loadFailureMetrics();
    }, intervalSeconds * 1000);
  }

  function setAutoRefreshActive(active) {
    document.querySelector('.failure-refresh-btn')?.classList.toggle('is-auto-refreshing', active);
  }

  document.getElementById('failure-metric-range')?.addEventListener('change', () => {
    loadFailureMetrics();
  });

  document.getElementById('failure-auto-refresh-interval')?.addEventListener('change', () => {
    syncFailureMetricAutoRefresh();
  });

  window.addEventListener('beforeunload', stopFailureMetricAutoRefresh);

  if (document.getElementById('failure-monitoring-view')) {
    loadFailureMetrics();
    syncFailureMetricAutoRefresh();
  }
})();
