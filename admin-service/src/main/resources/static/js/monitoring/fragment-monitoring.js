(function () {
  const FAILURE_METRICS_URL = `${base()}/admin/api/${API.VERSION}/manage/monitoring/failure-metrics`;
  const LEVEL_LABELS = {
    NORMAL: '정상',
    WARNING: '경고',
    CRITICAL: '위험',
    UNKNOWN: '확인불가'
  };

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
    return `
      <article class="failure-metric-card">
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
      </article>
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
    const range = document.getElementById('failure-metric-range')?.value || '5m';
    const grid = document.getElementById('failure-metric-grid');
    if (!grid) return;

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
    }
  };

  document.getElementById('failure-metric-range')?.addEventListener('change', () => {
    loadFailureMetrics();
  });

  if (document.getElementById('failure-monitoring-view')) {
    loadFailureMetrics();
  }
})();
