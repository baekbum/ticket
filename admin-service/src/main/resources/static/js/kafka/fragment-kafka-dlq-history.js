(function () {
  const DLQ_HISTORY_URL = `${base()}/admin/api/${API.VERSION}/manage/kafka-dlq/histories`;
  const DLQ_HANDLE_URL = `${base()}/admin/api/${API.VERSION}/manage/kafka-dlq`;
  const headers = { 'Content-Type': 'application/json' };

  let currentHistories = [];
  let currentDetail = null;
  let retryConfirmResolver = null;
  let currentSearchFilters = {
    dltTopic: null,
    partitionNo: null,
    messageOffset: null,
    messageKey: null,
    action: null,
    status: null,
    operator: null,
    payloadModified: null
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

  function formatDateTime(value) {
    if (!value) return '-';
    return String(value).replace('T', ' ').slice(0, 19);
  }

  function formatJsonText(value) {
    if (!value) return '-';
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch (e) {
      return String(value);
    }
  }

  function badge(value, type) {
    const normalized = String(value || '-').toLowerCase();
    return `<span class="dlq-history-badge ${type}-${escapeHtml(normalized)}">${escapeHtml(value || '-')}</span>`;
  }

  function isFailedHistory(history) {
    return String(history?.status || '').toUpperCase() === 'FAILED';
  }

  function retryActionLabel(action) {
    const normalizedAction = String(action || '').toUpperCase();
    if (normalizedAction === 'REPLAY') return '원본 topic 재발행 재시도';
    if (normalizedAction === 'MODIFIED_REPLAY') return '수정 payload 재발행 재시도';
    if (normalizedAction === 'DISCARD') return '폐기 재시도';
    return '재시도';
  }

  function retryEndpointOf(action) {
    const normalizedAction = String(action || '').toUpperCase();
    if (normalizedAction === 'REPLAY') return 'replay';
    if (normalizedAction === 'MODIFIED_REPLAY') return 'replay/modified';
    if (normalizedAction === 'DISCARD') return 'discard';
    return null;
  }

  function retryRequestOf(history) {
    const body = {
      dltTopic: history.dltTopic,
      partition: history.partitionNo,
      offset: history.messageOffset,
      operator: history.operator,
      reason: history.reason || 'DLQ 실패 이력 재시도'
    };

    if (String(history.action || '').toUpperCase() === 'MODIFIED_REPLAY') {
      body.modifiedPayload = history.modifiedPayload;
    }

    return body;
  }

  function refreshRetryControls(history) {
    const panel = document.getElementById('dlq-history-retry-panel');
    const desc = document.getElementById('dlq-history-retry-desc');
    const retryButton = document.getElementById('dlq-history-retry-btn');
    const retryable = isFailedHistory(history) && retryEndpointOf(history.action);
    const modifiedReplayWithoutPayload =
      String(history?.action || '').toUpperCase() === 'MODIFIED_REPLAY'
      && !history?.modifiedPayload;

    if (panel) panel.style.display = retryable ? 'block' : 'none';
    if (retryButton) retryButton.style.display = retryable ? 'inline-flex' : 'none';

    if (!retryable) {
      return;
    }

    if (modifiedReplayWithoutPayload) {
      if (desc) desc.textContent = '수정 payload가 없어 수정 payload 재발행을 재시도할 수 없습니다.';
      if (retryButton) retryButton.disabled = true;
      return;
    }

    if (desc) {
      desc.textContent = `${retryActionLabel(history.action)}를 같은 DLT topic, partition, offset 기준으로 다시 실행합니다. 재시도 결과는 새 이력으로 저장됩니다.`;
    }
    if (retryButton) {
      retryButton.disabled = false;
      retryButton.textContent = retryActionLabel(history.action);
      retryButton.className = String(history.action || '').toUpperCase() === 'DISCARD'
        ? 'btn btn-danger'
        : String(history.action || '').toUpperCase() === 'MODIFIED_REPLAY'
          ? 'btn btn-warning'
          : 'btn';
    }
  }

  function openRetryConfirm(history) {
    const body = retryRequestOf(history);
    const actionLabel = retryActionLabel(history.action);

    document.getElementById('dlq-history-confirm-title').textContent = `${actionLabel} 확인`;
    document.getElementById('dlq-history-confirm-message').textContent =
      '기존 실패 이력은 유지하고, 재시도 결과를 새 처리 이력으로 저장합니다.';
    document.getElementById('dlq-history-confirm-summary').innerHTML = `
      <div>이력 ID: <strong>${escapeHtml(history.id)}</strong></div>
      <div>DLT Topic: <strong>${escapeHtml(body.dltTopic)}</strong></div>
      <div>Partition / Offset: <strong>${escapeHtml(body.partition)} / ${escapeHtml(body.offset)}</strong></div>
      <div>처리: <strong>${escapeHtml(history.action)}</strong></div>
      <div>처리자: <strong>${escapeHtml(body.operator || '-')}</strong></div>
      <div>사유: <strong>${escapeHtml(body.reason || '-')}</strong></div>
    `;

    const submitButton = document.getElementById('dlq-history-confirm-submit-btn');
    submitButton.className = String(history.action || '').toUpperCase() === 'DISCARD'
      ? 'btn btn-danger'
      : String(history.action || '').toUpperCase() === 'MODIFIED_REPLAY'
        ? 'btn btn-warning'
        : 'btn';
    submitButton.textContent = '재시도 실행';
    document.getElementById('dlq-history-confirm-modal').style.display = 'flex';

    return new Promise(resolve => {
      retryConfirmResolver = resolve;
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

  function normalizePayloadModified(value) {
    if (value === 'true') return true;
    if (value === 'false') return false;
    return null;
  }

  window.loadDlqHistoryList = async function (pageZeroIndexed = 0) {
    try {
      const res = await Fetch(DLQ_HISTORY_URL, {
        method: 'POST',
        headers,
        body: JSON.stringify(buildCond(pageZeroIndexed))
      });

      if (!res.ok) {
        await showResponseError(res, 'DLQ 처리 이력 조회에 실패했습니다.');
        return;
      }

      const paged = await res.json();
      currentHistories = paged.content || [];
      serverTotalPages = Math.max(paged.totalPages || paged.page?.totalPages || 1, 1);
      const totalCount = paged.totalElements ?? paged.page?.totalElements ?? currentHistories.length;
      const pageSize = parseInt(document.getElementById('pagination-size').value, 10);

      document.getElementById('pagination-total').textContent = serverTotalPages;
      document.getElementById('pagination-current').value = pageZeroIndexed + 1;
      document.getElementById('pagination-total-count').textContent = totalCount;

      const tbody = document.getElementById('dlq-history-table-body');
      tbody.innerHTML = '';

      if (currentHistories.length === 0) {
        tbody.innerHTML = '<tr><td colspan="14" class="dlq-history-empty-cell">조회된 DLQ 처리 이력이 없습니다.</td></tr>';
        return;
      }

      currentHistories.forEach((history, index) => {
        const rowNumber = pageZeroIndexed * pageSize + index + 1;
        const tr = document.createElement('tr');
        tr.onclick = () => openDlqHistoryDetailModal(history.id);
        tr.innerHTML = `
          <td style="text-align:center;color:var(--text-muted);">${rowNumber}</td>
          <td><strong>${escapeHtml(history.id)}</strong></td>
          <td title="${escapeHtml(history.dltTopic)}">${escapeHtml(history.dltTopic)}</td>
          <td>${escapeHtml(history.partitionNo)}</td>
          <td>${escapeHtml(history.messageOffset)}</td>
          <td title="${escapeHtml(history.messageKey)}">${escapeHtml(history.messageKey || '-')}</td>
          <td title="${escapeHtml(history.targetTopic)}">${escapeHtml(history.targetTopic || '-')}</td>
          <td>${badge(history.action, 'action')}</td>
          <td>${badge(history.status, 'status')}</td>
          <td>${history.payloadModified ? '수정됨' : '-'}</td>
          <td title="${escapeHtml(history.operator)}">${escapeHtml(history.operator || '-')}</td>
          <td title="${escapeHtml(history.reason)}">${escapeHtml(history.reason || '-')}</td>
          <td>${escapeHtml(formatDateTime(history.handledAt))}</td>
          <td class="actions">
            <button class="btn btn-sm btn-outline" onclick="event.stopPropagation(); openDlqHistoryDetailModal(${Number(history.id)})">상세</button>
          </td>
        `;
        tbody.appendChild(tr);
      });
    } catch (e) {
      console.error('DLQ history list load failed', e);
      showToast('DLQ 처리 이력 통신 오류가 발생했습니다.', true);
    }
  };

  window.triggerDlqHistorySearch = function () {
    currentSearchFilters = {
      dltTopic: inputValue('dlq-history-topic') || null,
      partitionNo: inputValue('dlq-history-partition') ? Number(inputValue('dlq-history-partition')) : null,
      messageOffset: inputValue('dlq-history-offset') ? Number(inputValue('dlq-history-offset')) : null,
      messageKey: inputValue('dlq-history-message-key') || null,
      action: inputValue('dlq-history-action') || null,
      status: inputValue('dlq-history-status') || null,
      operator: inputValue('dlq-history-operator') || null,
      payloadModified: normalizePayloadModified(inputValue('dlq-history-payload-modified'))
    };
    syncDlqHistoryStatusFilters();
    loadDlqHistoryList(0);
  };

  window.resetDlqHistorySearch = function () {
    currentSearchFilters = {
      dltTopic: null,
      partitionNo: null,
      messageOffset: null,
      messageKey: null,
      action: null,
      status: null,
      operator: null,
      payloadModified: null
    };
    setValue('dlq-history-topic', '');
    setValue('dlq-history-partition', '');
    setValue('dlq-history-offset', '');
    setValue('dlq-history-message-key', '');
    setValue('dlq-history-action', '');
    setValue('dlq-history-status', '');
    setValue('dlq-history-operator', '');
    setValue('dlq-history-payload-modified', '');
    syncDlqHistoryStatusFilters();
    loadDlqHistoryList(0);
  };

  window.quickDlqHistoryStatus = function (button) {
    const status = button?.dataset?.status || '';
    currentSearchFilters.status = status || null;
    setValue('dlq-history-status', status);
    syncDlqHistoryStatusFilters();
    loadDlqHistoryList(0);
  };

  function syncDlqHistoryStatusFilters() {
    document.querySelectorAll('.dlq-history-filter').forEach(filter => {
      filter.classList.toggle('active', (filter.dataset.status || '') === (currentSearchFilters.status || ''));
    });
  }

  window.openDlqHistoryDetailModal = async function (id) {
    let history = currentHistories.find(item => Number(item.id) === Number(id));

    if (!history) {
      try {
        const res = await Fetch(`${DLQ_HISTORY_URL}/${id}`, { method: 'GET' });
        if (!res.ok) {
          await showResponseError(res, 'DLQ 처리 이력 상세 조회에 실패했습니다.');
          return;
        }
        history = await res.json();
      } catch (e) {
        console.error('DLQ history detail load failed', e);
        showToast('DLQ 처리 이력 상세 통신 오류가 발생했습니다.', true);
        return;
      }
    }

    currentDetail = history;
    document.getElementById('dlq-history-detail-id').textContent = history.id || '-';
    document.getElementById('dlq-history-detail-grid').innerHTML = [
      ['DLT Topic', history.dltTopic],
      ['Partition', history.partitionNo],
      ['Offset', history.messageOffset],
      ['Message Key', history.messageKey],
      ['원본 Topic', history.targetTopic],
      ['처리', history.action],
      ['상태', history.status],
      ['Payload 수정', history.payloadModified ? 'Y' : 'N'],
      ['처리자', history.operator],
      ['사유', history.reason],
      ['처리 시각', formatDateTime(history.handledAt)]
    ].map(([label, value]) => `
      <div class="dlq-history-detail-item">
        <span>${escapeHtml(label)}</span>
        <strong title="${escapeHtml(value)}">${escapeHtml(value ?? '-')}</strong>
      </div>
    `).join('');

    document.getElementById('dlq-history-original-payload').textContent = formatJsonText(history.originalPayload);
    document.getElementById('dlq-history-modified-payload').textContent = formatJsonText(history.modifiedPayload);
    document.getElementById('dlq-history-error-message').textContent = history.errorMessage || '-';
    refreshRetryControls(history);
    document.getElementById('dlq-history-detail-modal').style.display = 'flex';
  };

  window.closeDlqHistoryDetailModal = function () {
    document.getElementById('dlq-history-detail-modal').style.display = 'none';
    currentDetail = null;
  };

  window.retryDlqHistory = async function () {
    if (!currentDetail || !isFailedHistory(currentDetail)) {
      showToast('재시도할 실패 이력을 먼저 선택하세요.', true);
      return;
    }

    const endpoint = retryEndpointOf(currentDetail.action);
    if (!endpoint) {
      showToast('지원하지 않는 DLQ 처리 유형입니다.', true);
      return;
    }

    if (String(currentDetail.action || '').toUpperCase() === 'MODIFIED_REPLAY' && !currentDetail.modifiedPayload) {
      showToast('수정 payload가 없어 재시도할 수 없습니다.', true);
      return;
    }

    const confirmed = await openRetryConfirm(currentDetail);
    if (!confirmed) {
      return;
    }

    try {
      const res = await Fetch(`${DLQ_HANDLE_URL}/${endpoint}`, {
        method: 'POST',
        headers,
        body: JSON.stringify(retryRequestOf(currentDetail))
      });

      if (!res.ok) {
        await showResponseError(res, 'DLQ 실패 이력 재시도에 실패했습니다.');
        return;
      }

      const result = await res.json();
      showToast(`${result.result || 'DLQ 재시도'} 처리를 완료했습니다.`);
      document.getElementById('dlq-history-detail-modal').style.display = 'none';
      currentDetail = null;
      await loadDlqHistoryList(parseInt(document.getElementById('pagination-current').value, 10) - 1);
    } catch (e) {
      console.error('DLQ history retry failed', e);
      showToast('DLQ 실패 이력 재시도 통신 오류가 발생했습니다.', true);
    }
  };

  window.cancelDlqHistoryRetryConfirm = function () {
    document.getElementById('dlq-history-confirm-modal').style.display = 'none';
    if (retryConfirmResolver) {
      retryConfirmResolver(false);
      retryConfirmResolver = null;
    }
  };

  window.submitDlqHistoryRetryConfirm = function () {
    document.getElementById('dlq-history-confirm-modal').style.display = 'none';
    if (retryConfirmResolver) {
      retryConfirmResolver(true);
      retryConfirmResolver = null;
    }
  };

  window.Pagination.register({
    load: window.loadDlqHistoryList,
    getTotalPages: () => serverTotalPages
  });

  loadDlqHistoryList(0);
})();
