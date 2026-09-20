(function () {
  const DLQ_URL = `${base()}/admin/api/${API.VERSION}/manage/kafka-dlq`;

  let currentTopics = [];
  let currentMessages = [];
  let currentDetail = null;
  let dlqConfirmResolver = null;

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  function inputValue(id) {
    return document.getElementById(id)?.value?.trim() || '';
  }

  function setValue(id, value) {
    const el = document.getElementById(id);
    if (el) el.value = value ?? '';
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

  function statusBadge(status) {
    const normalizedStatus = String(status || 'UNKNOWN').toUpperCase();
    return `<span class="dlq-status-badge status-${escapeHtml(normalizedStatus.toLowerCase())}">${escapeHtml(normalizedStatus)}</span>`;
  }

  function isHandledStatus(status) {
    return ['REPLAYED', 'MODIFIED_REPLAYED', 'DISCARDED'].includes(String(status || '').toUpperCase());
  }

  function defaultOperator() {
    if (typeof loggedInUserRawData !== 'undefined' && loggedInUserRawData) {
      return loggedInUserRawData.userId || loggedInUserRawData.name || '';
    }
    return '';
  }

  function setListLoading(isLoading) {
    const loading = document.getElementById('dlq-list-loading');
    const searchButton = document.getElementById('dlq-search-btn');

    loading?.classList.toggle('active', isLoading);
    if (searchButton) {
      searchButton.disabled = isLoading;
      searchButton.innerHTML = isLoading
        ? '<i class="ti ti-loader-2"></i>조회 중'
        : '<i class="ti ti-search"></i>조회';
    }
  }

  function setDetailLoading(isLoading) {
    const loading = document.getElementById('dlq-detail-loading');
    const detailGrid = document.getElementById('dlq-detail-grid');
    const detailSection = document.querySelector('.dlq-detail-section');
    const handlePanel = document.getElementById('dlq-handle-panel');
    const payloadEditPanel = document.getElementById('dlq-payload-edit-panel');
    const modalActions = document.querySelector('#dlq-message-detail-modal .modal-actions');

    loading?.classList.toggle('active', isLoading);
    if (detailGrid) detailGrid.style.display = isLoading ? 'none' : 'grid';
    if (detailSection) detailSection.style.display = isLoading ? 'none' : 'grid';
    if (payloadEditPanel) payloadEditPanel.style.display = isLoading ? 'none' : 'block';
    if (handlePanel) handlePanel.style.display = isLoading ? 'none' : 'grid';
    if (modalActions) modalActions.style.display = isLoading ? 'none' : 'flex';
  }

  function setHandleLoading(isLoading) {
    const replayButton = document.getElementById('dlq-replay-btn');
    const modifiedReplayButton = document.getElementById('dlq-modified-replay-btn');
    const discardButton = document.getElementById('dlq-discard-btn');
    const disabled = isLoading;

    if (replayButton) {
      replayButton.disabled = disabled;
      replayButton.textContent = isLoading ? '처리 중' : '원본 topic 재발행';
    }
    if (modifiedReplayButton) {
      modifiedReplayButton.disabled = disabled;
      modifiedReplayButton.textContent = isLoading ? '처리 중' : '수정 payload 재발행';
    }
    if (discardButton) {
      discardButton.disabled = disabled;
      discardButton.textContent = isLoading ? '처리 중' : '폐기';
    }
  }

  function refreshHandleControls() {
    const notice = document.getElementById('dlq-handle-notice');
    const replayButton = document.getElementById('dlq-replay-btn');
    const modifiedReplayButton = document.getElementById('dlq-modified-replay-btn');
    const discardButton = document.getElementById('dlq-discard-btn');
    const resetPayloadButton = document.getElementById('dlq-reset-payload-btn');
    const operatorInput = document.getElementById('dlq-handle-operator');
    const reasonInput = document.getElementById('dlq-handle-reason');
    const modifiedPayloadInput = document.getElementById('dlq-modified-payload');
    const alreadyHandled = isHandledStatus(currentDetail?.processingStatus);

    if (notice) {
      notice.textContent = alreadyHandled
        ? `이미 ${formatDateTime(currentDetail?.handledAt)}에 처리된 DLT 메시지입니다.`
        : '';
    }
    if (replayButton) replayButton.style.display = alreadyHandled ? 'none' : 'inline-flex';
    if (modifiedReplayButton) modifiedReplayButton.style.display = alreadyHandled ? 'none' : 'inline-flex';
    if (discardButton) discardButton.style.display = alreadyHandled ? 'none' : 'inline-flex';
    if (resetPayloadButton) resetPayloadButton.style.display = alreadyHandled ? 'none' : 'inline-flex';
    if (operatorInput) operatorInput.disabled = alreadyHandled;
    if (reasonInput) reasonInput.disabled = alreadyHandled;
    if (modifiedPayloadInput) modifiedPayloadInput.disabled = alreadyHandled;
  }

  function openDlqConfirm(action, body) {
    const isReplay = action === 'replay';
    const isModifiedReplay = action === 'replay/modified';
    document.getElementById('dlq-confirm-title').textContent = isModifiedReplay
      ? '수정 payload 재발행 확인'
      : isReplay ? '원본 topic 재발행 확인' : 'DLT 메시지 폐기 확인';
    document.getElementById('dlq-confirm-message').textContent = isModifiedReplay
      ? '운영자가 수정한 payload를 원본 topic으로 다시 발행합니다.'
      : isReplay
        ? '선택한 DLT 메시지를 원본 topic으로 다시 발행합니다.'
        : '선택한 DLT 메시지를 운영상 폐기 처리합니다. Kafka 메시지는 retention 전까지 남아 있습니다.';
    document.getElementById('dlq-confirm-summary').innerHTML = `
      <div>DLT Topic: <strong>${escapeHtml(body.dltTopic)}</strong></div>
      <div>Partition / Offset: <strong>${escapeHtml(body.partition)} / ${escapeHtml(body.offset)}</strong></div>
      <div>처리자: <strong>${escapeHtml(body.operator)}</strong></div>
      <div>사유: <strong>${escapeHtml(body.reason)}</strong></div>
    `;
    const submitButton = document.getElementById('dlq-confirm-submit-btn');
    submitButton.className = isModifiedReplay ? 'btn btn-warning' : isReplay ? 'btn' : 'btn btn-danger';
    submitButton.textContent = isModifiedReplay ? '수정 재발행' : isReplay ? '재발행' : '폐기';
    document.getElementById('dlq-confirm-modal').style.display = 'flex';

    return new Promise(resolve => {
      dlqConfirmResolver = resolve;
    });
  }

  function renderTopics() {
    const grid = document.getElementById('dlq-topic-grid');
    const select = document.getElementById('dlq-topic-select');

    grid.innerHTML = '';
    select.innerHTML = '<option value="">DLT topic 선택</option>';

    if (currentTopics.length === 0) {
      grid.innerHTML = '<div class="dlq-topic-card"><strong>DLT topic 없음</strong><span>허용된 DLT mapping이 없습니다.</span></div>';
      return;
    }

    currentTopics.forEach(topic => {
      const option = document.createElement('option');
      option.value = topic.dltTopic;
      option.textContent = `${topic.dltTopic} → ${topic.targetTopic}`;
      select.appendChild(option);

      const card = document.createElement('button');
      card.className = 'dlq-topic-card';
      card.type = 'button';
      card.dataset.topic = topic.dltTopic;
      card.onclick = () => selectDlqTopic(topic.dltTopic);
      card.innerHTML = `
        <strong>${escapeHtml(topic.dltTopic)}</strong>
        <span>원본 topic: ${escapeHtml(topic.targetTopic)}</span>
      `;
      grid.appendChild(card);
    });
  }

  function syncTopicCards() {
    const selectedTopic = inputValue('dlq-topic-select');
    document.querySelectorAll('.dlq-topic-card').forEach(card => {
      card.classList.toggle('active', card.dataset.topic === selectedTopic);
    });
  }

  window.selectDlqTopic = function (dltTopic) {
    setValue('dlq-topic-select', dltTopic);
    syncTopicCards();
    loadDlqMessages();
  };

  window.loadDlqTopics = async function () {
    try {
      const res = await Fetch(`${DLQ_URL}/topics`, { method: 'GET' });
      if (!res.ok) {
        await showResponseError(res, 'DLT topic 목록 조회에 실패했습니다.');
        return;
      }

      currentTopics = await res.json();
      renderTopics();
    } catch (e) {
      console.error('DLT topics load failed', e);
      showToast('DLT topic 목록 통신 오류가 발생했습니다.', true);
    }
  };

  window.loadDlqMessages = async function () {
    const dltTopic = inputValue('dlq-topic-select');
    const tbody = document.getElementById('dlq-message-table-body');
    syncTopicCards();

    if (!dltTopic) {
      tbody.innerHTML = '<tr><td colspan="9" class="dlq-empty-cell">DLT topic을 선택하세요.</td></tr>';
      return;
    }

    const params = new URLSearchParams({
      dltTopic,
      size: inputValue('dlq-size-input') || '20'
    });
    const partition = inputValue('dlq-partition-input');
    const fromOffset = inputValue('dlq-from-offset-input');
    if (partition) params.set('partition', partition);
    if (fromOffset) params.set('fromOffset', fromOffset);

    try {
      setListLoading(true);
      const res = await Fetch(`${DLQ_URL}/messages?${params.toString()}`, { method: 'GET' });
      if (!res.ok) {
        await showResponseError(res, 'DLT 메시지 목록 조회에 실패했습니다.');
        return;
      }

      currentMessages = await res.json();
      renderMessages();
    } catch (e) {
      console.error('DLT messages load failed', e);
      showToast('DLT 메시지 목록 통신 오류가 발생했습니다.', true);
    } finally {
      setListLoading(false);
    }
  };

  function renderMessages() {
    const tbody = document.getElementById('dlq-message-table-body');
    tbody.innerHTML = '';

    if (currentMessages.length === 0) {
      tbody.innerHTML = '<tr><td colspan="9" class="dlq-empty-cell">조회된 DLT 메시지가 없습니다.</td></tr>';
      return;
    }

    currentMessages.forEach(message => {
      const tr = document.createElement('tr');
      tr.onclick = () => openDlqMessageDetailModal(message.dltTopic, message.partition, message.offset);
      tr.innerHTML = `
        <td title="${escapeHtml(message.dltTopic)}">${escapeHtml(message.dltTopic)}</td>
        <td>${escapeHtml(message.partition)}</td>
        <td><strong>${escapeHtml(message.offset)}</strong></td>
        <td title="${escapeHtml(message.messageKey)}">${escapeHtml(message.messageKey || '-')}</td>
        <td>${escapeHtml(formatDateTime(message.occurredAt))}</td>
        <td>${escapeHtml((message.headers || []).length)}개</td>
        <td>${statusBadge(message.processingStatus)}</td>
        <td title="${escapeHtml(message.payloadPreview)}">${escapeHtml(message.payloadPreview || '-')}</td>
        <td class="actions">
          <button class="btn btn-sm btn-outline" onclick="event.stopPropagation(); openDlqMessageDetailModal('${escapeHtml(message.dltTopic)}', ${Number(message.partition)}, ${Number(message.offset)})">상세</button>
        </td>
      `;
      tbody.appendChild(tr);
    });
  }

  window.openDlqMessageDetailModal = async function (dltTopic, partition, offset) {
    const params = new URLSearchParams({ dltTopic, partition, offset });
    document.getElementById('dlq-detail-location').textContent =
      `${dltTopic} / partition ${partition} / offset ${offset}`;
    currentDetail = null;
    document.getElementById('dlq-detail-payload').textContent = '';
    document.getElementById('dlq-detail-headers').textContent = '';
    setValue('dlq-modified-payload', '');
    setValue('dlq-handle-operator', defaultOperator());
    setValue('dlq-handle-reason', '');
    document.getElementById('dlq-handle-notice').textContent = '';
    document.getElementById('dlq-message-detail-modal').style.display = 'flex';
    setDetailLoading(true);

    try {
      const res = await Fetch(`${DLQ_URL}/messages/detail?${params.toString()}`, { method: 'GET' });
      if (!res.ok) {
        await showResponseError(res, 'DLT 메시지 상세 조회에 실패했습니다.');
        return;
      }

      const detail = await res.json();
      renderDetail(detail);
    } catch (e) {
      console.error('DLT message detail load failed', e);
      showToast('DLT 메시지 상세 통신 오류가 발생했습니다.', true);
    } finally {
      setDetailLoading(false);
    }
  };

  function renderDetail(detail) {
    currentDetail = detail;
    const alreadyHandled = isHandledStatus(detail.processingStatus);
    document.getElementById('dlq-detail-location').textContent =
      `${detail.dltTopic} / partition ${detail.partition} / offset ${detail.offset}`;

    document.getElementById('dlq-detail-grid').innerHTML = [
      ['DLT Topic', detail.dltTopic],
      ['원본 Topic', detail.targetTopic],
      ['Partition', detail.partition],
      ['Offset', detail.offset],
      ['Key', detail.messageKey],
      ['발생 시각', formatDateTime(detail.occurredAt)],
      ['처리 상태', detail.processingStatus],
      ['처리 방식', detail.handleAction],
      ['처리자', detail.handledOperator],
      ['처리 사유', detail.handleReason],
      ['처리 시각', formatDateTime(detail.handledAt)],
      ['처리 오류', detail.handleErrorMessage],
      ['Payload 수정 여부', detail.payloadModified ? 'Y' : 'N'],
      ['Payload Base64', detail.payloadBase64]
    ].map(([label, value]) => `
      <div class="dlq-detail-item">
        <span>${escapeHtml(label)}</span>
        <strong title="${escapeHtml(value)}">${escapeHtml(value ?? '-')}</strong>
      </div>
    `).join('');

    document.getElementById('dlq-detail-payload').textContent = formatJsonText(detail.payload);
    document.getElementById('dlq-detail-headers').textContent = JSON.stringify(detail.headers || [], null, 2);
    setValue('dlq-modified-payload', detail.payloadModified ? formatJsonText(detail.modifiedPayload) : formatJsonText(detail.payload));
    setValue('dlq-handle-operator', alreadyHandled ? detail.handledOperator : defaultOperator());
    setValue('dlq-handle-reason', alreadyHandled ? detail.handleReason : '');
    refreshHandleControls();
  }

  window.handleDlqMessage = async function (action) {
    if (!currentDetail) {
      showToast('DLT 메시지 상세 정보를 먼저 조회하세요.', true);
      return;
    }
    if (isHandledStatus(currentDetail.processingStatus)) {
      showToast('이미 처리된 DLT 메시지입니다.', true);
      return;
    }

    const operator = inputValue('dlq-handle-operator');
    const reason = inputValue('dlq-handle-reason');
    if (!operator) {
      showToast('처리자를 입력하세요.', true);
      return;
    }
    if (!reason) {
      showToast('처리 사유를 입력하세요.', true);
      return;
    }

    const isReplay = action === 'replay';
    const isModifiedReplay = action === 'replay/modified';
    const modifiedPayload = inputValue('dlq-modified-payload');
    if (isModifiedReplay) {
      if (!modifiedPayload) {
        showToast('수정 payload를 입력하세요.', true);
        return;
      }
      try {
        JSON.parse(modifiedPayload);
      } catch (e) {
        showToast('수정 payload는 유효한 JSON이어야 합니다.', true);
        return;
      }
    }

    const confirmed = await openDlqConfirm(action, {
      dltTopic: currentDetail.dltTopic,
      partition: currentDetail.partition,
      offset: currentDetail.offset,
      operator,
      reason
    });
    if (!confirmed) {
      return;
    }

    const body = {
      dltTopic: currentDetail.dltTopic,
      partition: currentDetail.partition,
      offset: currentDetail.offset,
      operator,
      reason
    };
    if (isModifiedReplay) {
      body.modifiedPayload = modifiedPayload;
    }

    try {
      setHandleLoading(true);
      const res = await Fetch(`${DLQ_URL}/${action}`, {
        method: 'POST',
        body
      });
      if (!res.ok) {
        await showResponseError(res, isReplay || isModifiedReplay ? 'DLT 메시지 재발행에 실패했습니다.' : 'DLT 메시지 폐기에 실패했습니다.');
        return;
      }

      const result = await res.json();
      currentDetail.processingStatus = result.result;
      refreshHandleControls();
      showToast(isReplay || isModifiedReplay ? 'DLT 메시지를 재발행했습니다.' : 'DLT 메시지를 폐기 처리했습니다.');
      await loadDlqMessages();
    } catch (e) {
      console.error('DLT message handle failed', e);
      showToast('DLT 메시지 처리 통신 오류가 발생했습니다.', true);
    } finally {
      setHandleLoading(false);
    }
  };

  window.resetModifiedPayload = function () {
    if (!currentDetail) {
      showToast('DLT 메시지 상세 정보를 먼저 조회하세요.', true);
      return;
    }
    setValue('dlq-modified-payload', formatJsonText(currentDetail.payload));
  };

  window.closeDlqMessageDetailModal = function () {
    document.getElementById('dlq-message-detail-modal').style.display = 'none';
    currentDetail = null;
  };

  window.cancelDlqConfirm = function () {
    document.getElementById('dlq-confirm-modal').style.display = 'none';
    if (dlqConfirmResolver) {
      dlqConfirmResolver(false);
      dlqConfirmResolver = null;
    }
  };

  window.submitDlqConfirm = function () {
    document.getElementById('dlq-confirm-modal').style.display = 'none';
    if (dlqConfirmResolver) {
      dlqConfirmResolver(true);
      dlqConfirmResolver = null;
    }
  };

  window.resetDlqSearch = function () {
    setValue('dlq-topic-select', '');
    setValue('dlq-partition-input', '');
    setValue('dlq-from-offset-input', '');
    setValue('dlq-size-input', '20');
    currentMessages = [];
    syncTopicCards();
    document.getElementById('dlq-message-table-body').innerHTML =
      '<tr><td colspan="9" class="dlq-empty-cell">DLT topic을 선택하고 조회하세요.</td></tr>';
  };

  loadDlqTopics();
})();
