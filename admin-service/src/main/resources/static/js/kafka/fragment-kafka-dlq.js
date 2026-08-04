(function () {
  const DLQ_URL = `${base()}/admin/api/${API.VERSION}/manage/kafka-dlq`;

  let currentTopics = [];
  let currentMessages = [];

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
    return `<span class="dlq-status-badge">${escapeHtml(status || 'UNKNOWN')}</span>`;
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

    try {
      const res = await Fetch(`${DLQ_URL}/messages/detail?${params.toString()}`, { method: 'GET' });
      if (!res.ok) {
        await showResponseError(res, 'DLT 메시지 상세 조회에 실패했습니다.');
        return;
      }

      const detail = await res.json();
      renderDetail(detail);
      document.getElementById('dlq-message-detail-modal').style.display = 'flex';
    } catch (e) {
      console.error('DLT message detail load failed', e);
      showToast('DLT 메시지 상세 통신 오류가 발생했습니다.', true);
    }
  };

  function renderDetail(detail) {
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
      ['Payload Base64', detail.payloadBase64]
    ].map(([label, value]) => `
      <div class="dlq-detail-item">
        <span>${escapeHtml(label)}</span>
        <strong title="${escapeHtml(value)}">${escapeHtml(value ?? '-')}</strong>
      </div>
    `).join('');

    document.getElementById('dlq-detail-payload').textContent = formatJsonText(detail.payload);
    document.getElementById('dlq-detail-headers').textContent = JSON.stringify(detail.headers || [], null, 2);
  }

  window.closeDlqMessageDetailModal = function () {
    document.getElementById('dlq-message-detail-modal').style.display = 'none';
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
