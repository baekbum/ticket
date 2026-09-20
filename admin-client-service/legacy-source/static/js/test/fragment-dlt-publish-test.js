(function () {
  const DLT_TEST_URL = `${base()}/admin/api/${API.VERSION}/manage/test/dlt`;

  function inputValue(id) {
    return document.getElementById(id)?.value?.trim() || '';
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  function defaultPayload() {
    return JSON.stringify({
      test: true,
      source: 'admin-dlt-test',
      reason: 'local DLT publish test',
      createdAt: new Date().toISOString()
    }, null, 2);
  }

  function setResult(html) {
    const result = document.getElementById('dlt-test-result');
    if (result) result.innerHTML = html;
  }

  async function loadDltTopics() {
    const select = document.getElementById('dlt-test-topic');
    if (!select) return;

    try {
      const res = await Fetch(`${DLT_TEST_URL}/topics`, { method: 'GET' });
      if (!res.ok) {
        await showResponseError(res, 'DLT topic 목록 조회에 실패했습니다.');
        return;
      }

      const topics = await res.json();
      select.innerHTML = topics.map(topic => `<option value="${escapeHtml(topic)}">${escapeHtml(topic)}</option>`).join('');
    } catch (e) {
      console.error('DLT topic 목록 조회 실패', e);
      showToast('DLT topic 목록 조회에 실패했습니다.', true);
    }
  }

  window.resetDltTestPayload = function () {
    const payload = document.getElementById('dlt-test-payload');
    if (payload) payload.value = defaultPayload();
  };

  window.openDltTestConfirm = function () {
    const topic = inputValue('dlt-test-topic');
    const payload = inputValue('dlt-test-payload');
    if (!topic) {
      showToast('DLT topic을 선택해주세요.', true);
      return;
    }
    if (!payload) {
      showToast('Payload를 입력해주세요.', true);
      return;
    }
    document.getElementById('dlt-test-confirm')?.classList.add('is-open');
  };

  window.closeDltTestConfirm = function () {
    document.getElementById('dlt-test-confirm')?.classList.remove('is-open');
  };

  window.publishDltTestMessage = async function () {
    closeDltTestConfirm();

    const request = {
      dltTopic: inputValue('dlt-test-topic'),
      key: inputValue('dlt-test-key'),
      payload: inputValue('dlt-test-payload')
    };

    try {
      const res = await Fetch(`${DLT_TEST_URL}/publish`, {
        method: 'POST',
        body: request
      });
      if (!res.ok) {
        await showResponseError(res, 'DLT 테스트 메시지 발행에 실패했습니다.');
        return;
      }

      const data = await res.json();
      setResult(`
        <div><strong>발행 완료</strong></div>
        <div>Topic: ${escapeHtml(data.dltTopic)}</div>
        <div>Partition: ${escapeHtml(data.partition)}</div>
        <div>Offset: ${escapeHtml(data.offset)}</div>
        <div>Key: ${escapeHtml(data.key || '-')}</div>
        <div>Published At: ${escapeHtml(data.publishedAt)}</div>
      `);
      showToast('DLT 테스트 메시지를 발행했습니다.');
    } catch (e) {
      console.error('DLT 테스트 메시지 발행 실패', e);
      showToast('DLT 테스트 메시지 발행에 실패했습니다.', true);
    }
  };

  resetDltTestPayload();
  loadDltTopics();
})();
