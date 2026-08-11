(function () {
  const DLT_SLACK_TEST_URL = `${base()}/admin/api/${API.VERSION}/manage/test/dlt/slack`;

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
      source: 'admin-dlt-slack-test',
      reason: 'local DLT Slack notification test',
      createdAt: new Date().toISOString()
    }, null, 2);
  }

  function setStatus(html) {
    const status = document.getElementById('dlt-slack-status');
    if (status) status.innerHTML = html;
  }

  function setResult(html) {
    const result = document.getElementById('dlt-slack-result');
    if (result) result.innerHTML = html;
  }

  async function loadDltSlackConfig() {
    try {
      const res = await Fetch(`${DLT_SLACK_TEST_URL}/config`, { method: 'GET' });
      if (!res.ok) {
        await showResponseError(res, 'DLT Slack 설정 조회에 실패했습니다.');
        return;
      }

      const data = await res.json();
      setStatus(`
        <div><strong>Slack 설정</strong></div>
        <div>Enabled: ${escapeHtml(data.enabled)}</div>
        <div>Webhook Configured: ${escapeHtml(data.webhookConfigured)}</div>
        <div>Admin DLQ: ${escapeHtml(data.adminDlqUrl || '-')}</div>
      `);
    } catch (e) {
      console.error('DLT Slack 설정 조회 실패', e);
      showToast('DLT Slack 설정 조회에 실패했습니다.', true);
    }
  }

  window.resetDltSlackTestPayload = function () {
    const payload = document.getElementById('dlt-slack-payload');
    if (payload) payload.value = defaultPayload();
  };

  window.sendDltSlackTest = async function () {
    const request = {
      originTopic: inputValue('dlt-slack-origin-topic'),
      dltTopic: inputValue('dlt-slack-dlt-topic'),
      originPartition: 0,
      dltPartition: 0,
      offset: 0,
      key: inputValue('dlt-slack-key'),
      payload: inputValue('dlt-slack-payload'),
      exceptionMessage: inputValue('dlt-slack-exception')
    };

    if (!request.originTopic || !request.dltTopic || !request.payload) {
      showToast('Origin Topic, DLT Topic, Payload를 입력해주세요.', true);
      return;
    }

    try {
      const res = await Fetch(`${DLT_SLACK_TEST_URL}/send`, {
        method: 'POST',
        body: request
      });
      if (!res.ok) {
        await showResponseError(res, 'DLT Slack 테스트 알림 전송에 실패했습니다.');
        return;
      }

      const data = await res.json();
      if (data.sent) {
        setResult(`
          <div><strong>Slack 알림 전송 요청 완료</strong></div>
          <div>Origin Topic: ${escapeHtml(data.originTopic)}</div>
          <div>DLT Topic: ${escapeHtml(data.dltTopic)}</div>
          <div>Admin DLQ: ${escapeHtml(data.adminDlqUrl || '-')}</div>
          <div>Requested At: ${escapeHtml(data.requestedAt)}</div>
        `);
        showToast('DLT Slack 테스트 알림을 전송했습니다.');
      } else {
        setResult(`
          <div><strong>Slack 알림 전송 생략/실패</strong></div>
          <div>${escapeHtml(data.skippedReason || '알 수 없는 사유')}</div>
        `);
        showToast(data.skippedReason || 'DLT Slack 테스트 알림 전송에 실패했습니다.', true);
      }
    } catch (e) {
      console.error('DLT Slack 테스트 알림 전송 실패', e);
      showToast('DLT Slack 테스트 알림 전송에 실패했습니다.', true);
    }
  };

  resetDltSlackTestPayload();
  loadDltSlackConfig();
})();
