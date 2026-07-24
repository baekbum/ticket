(function () {
  const QUEUE_TEST_URL = `${base()}/admin/api/${API.VERSION}/queue/test`;
  const users = new Map();
  const logs = [];

  function inputValue(id) {
    return document.getElementById(id)?.value.trim() || '';
  }

  function numberValue(id, fallback) {
    const value = parseInt(inputValue(id), 10);
    return Number.isFinite(value) ? value : fallback;
  }

  function eventIdValue() {
    const eventId = inputValue('queue-test-event-id');
    if (!eventId) {
      showToast('Event ID를 입력해주세요.', true);
      return null;
    }
    return eventId;
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function escapeJs(value) {
    return String(value ?? '').replace(/\\/g, '\\\\').replace(/'/g, "\\'");
  }

  function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, Math.max(ms, 0)));
  }

  function formatTtl(seconds) {
    if (seconds === null || seconds === undefined) return '-';
    if (seconds < 60) return `${seconds}s`;
    return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
  }

  function addLog(message, detail = '') {
    logs.unshift({
      time: new Date().toLocaleTimeString(),
      message,
      detail
    });
    logs.splice(30);
    renderLogs();
  }

  function renderLogs() {
    const wrap = document.getElementById('queue-test-log');
    if (!wrap) return;
    if (!logs.length) {
      wrap.innerHTML = '<div style="color:var(--text-muted); font-size:12px;">아직 실행 결과가 없습니다.</div>';
      return;
    }
    wrap.innerHTML = logs.map(log => `
      <div class="queue-test-log-item">
        <strong>${escapeHtml(log.time)}</strong> ${escapeHtml(log.message)}
        ${log.detail ? `<div>${escapeHtml(log.detail)}</div>` : ''}
      </div>
    `).join('');
  }

  function applyResponse(userId, data) {
    const previous = users.get(userId) || {};
    users.set(userId, {
      ...previous,
      userId,
      status: data.status,
      rank: data.rank,
      waitingCount: data.waitingCount,
      token: data.token,
      expiresInSeconds: data.expiresInSeconds,
      lastCheckedAt: new Date().toLocaleTimeString()
    });
    renderUsers();
  }

  function statusBadge(status) {
    const normalized = String(status || 'WAITING').toUpperCase();
    const css = normalized === 'READY'
      ? 'ready'
      : normalized === 'COMPLETED'
        ? 'completed'
        : 'waiting';
    return `<span class="queue-test-badge ${css}">${escapeHtml(normalized)}</span>`;
  }

  function shortToken(token) {
    if (!token) return '-';
    return token.length > 18 ? `${token.slice(0, 8)}...${token.slice(-6)}` : token;
  }

  function rowButton(label, disabled, onclick) {
    return `<button class="queue-test-btn secondary" style="min-width:72px;height:30px;font-size:12px;" type="button" ${disabled ? 'disabled' : ''} onclick="${onclick}">${label}</button>`;
  }

  function renderUsers() {
    const tbody = document.getElementById('queue-test-body');
    if (!tbody) return;
    const list = [...users.values()];

    if (!list.length) {
      tbody.innerHTML = '<tr><td colspan="9" class="queue-test-empty">테스트 유저를 진입시키면 상태가 표시됩니다.</td></tr>';
      updateSummary();
      return;
    }

    tbody.innerHTML = list.map(user => {
      const userId = escapeJs(user.userId);
      const canValidate = Boolean(user.token) && user.status === 'READY';
      const canComplete = Boolean(user.token) && user.status === 'READY';
      return `
        <tr>
          <td title="${escapeHtml(user.userId)}">${escapeHtml(user.userId)}</td>
          <td>${statusBadge(user.status)}</td>
          <td>${escapeHtml(user.rank ?? '-')}</td>
          <td>${escapeHtml(user.waitingCount ?? '-')}</td>
          <td class="queue-test-token" title="${escapeHtml(user.token || '')}">${escapeHtml(shortToken(user.token))}</td>
          <td>${escapeHtml(formatTtl(user.expiresInSeconds))}</td>
          <td>${escapeHtml(user.lastCheckedAt || '-')}</td>
          <td>${rowButton('검증', !canValidate, `validateQueueTestToken('${userId}')`)}</td>
          <td>${rowButton('완료', !canComplete, `completeQueueTestUser('${userId}')`)}</td>
        </tr>
      `;
    }).join('');

    updateSummary();
  }

  function updateSummary() {
    const list = [...users.values()];
    const waiting = list.filter(user => user.status === 'WAITING').length;
    const ready = list.filter(user => user.status === 'READY').length;
    const last = list[list.length - 1];
    const lastToken = [...list].reverse().find(user => user.token)?.token;

    document.getElementById('queue-test-waiting-count').textContent = String(waiting);
    document.getElementById('queue-test-ready-count').textContent = String(ready);
    document.getElementById('queue-test-user-count').textContent = String(list.length);
    document.getElementById('queue-test-last-user').textContent = last?.userId || '-';
    document.getElementById('queue-test-last-token').textContent = shortToken(lastToken);
    document.getElementById('queue-test-last-token').title = lastToken || '';
  }

  async function enterUser(eventId, userId) {
    const res = await Fetch(`${QUEUE_TEST_URL}/events/${encodeURIComponent(eventId)}/enter?userId=${encodeURIComponent(userId)}`, {
      method: 'POST'
    });
    if (!res.ok) {
      throw new Error(`enter failed: ${res.status}`);
    }
    const data = await res.json();
    applyResponse(userId, data);
    addLog(`${userId} 진입`, `${data.status}${data.rank != null ? ` / rank ${data.rank}` : ''}${data.token ? ' / token 발급' : ''}`);
    return data;
  }

  async function refreshUserStatus(eventId, userId) {
    const current = users.get(userId);
    if (current?.status === 'COMPLETED') {
      return current;
    }

    const res = await Fetch(`${QUEUE_TEST_URL}/events/${encodeURIComponent(eventId)}/status?userId=${encodeURIComponent(userId)}`, {
      method: 'GET'
    });
    if (!res.ok) {
      throw new Error(`status failed: ${res.status}`);
    }
    const data = await res.json();
    applyResponse(userId, data);
    return data;
  }

  async function refreshUserStatuses(eventId, userIds) {
    const targetUserIds = userIds.filter(userId => users.get(userId)?.status !== 'COMPLETED');
    if (!targetUserIds.length) {
      return [];
    }

    const res = await Fetch(`${QUEUE_TEST_URL}/events/${encodeURIComponent(eventId)}/statuses`, {
      method: 'POST',
      body: { userIds: targetUserIds }
    });
    if (!res.ok) {
      throw new Error(`statuses failed: ${res.status}`);
    }

    const data = await res.json();
    data.forEach((status, index) => applyResponse(targetUserIds[index], status));
    return data;
  }

  async function refreshWaitingUsers(eventId) {
    const waitingUserIds = [...users.values()]
      .filter(user => user.status === 'WAITING')
      .map(user => user.userId);

    await refreshUserStatuses(eventId, waitingUserIds);
  }

  window.enterSingleQueueTestUser = async function () {
    const eventId = eventIdValue();
    if (!eventId) return;
    const userId = inputValue('queue-test-user-id');
    if (!userId) {
      showToast('User ID를 입력해주세요.', true);
      return;
    }

    try {
      await enterUser(eventId, userId);
      showToast('대기열 진입 요청이 완료되었습니다.');
    } catch (e) {
      console.error(e);
      showToast('대기열 진입 요청에 실패했습니다.', true);
    }
  };

  window.enterBulkQueueTestUsers = async function () {
    const eventId = eventIdValue();
    if (!eventId) return;
    const prefix = inputValue('queue-test-prefix') || 'test-user';
    const count = Math.min(Math.max(numberValue('queue-test-count', 10), 1), 500);
    const interval = Math.min(Math.max(numberValue('queue-test-interval', 100), 0), 5000);

    try {
      for (let i = 1; i <= count; i++) {
        await enterUser(eventId, `${prefix}-${i}`);
        if (interval > 0 && i < count) {
          await sleep(interval);
        }
      }
      showToast(`${count}명 대기열 진입 요청이 완료되었습니다.`);
    } catch (e) {
      console.error(e);
      showToast('N명 대기열 진입 중 오류가 발생했습니다.', true);
    }
  };

  window.refreshQueueTestStatuses = async function () {
    const eventId = eventIdValue();
    if (!eventId) return;
    const userIds = [...users.keys()];
    if (!userIds.length) {
      showToast('조회할 테스트 유저가 없습니다.', true);
      return;
    }

    try {
      await refreshUserStatuses(eventId, userIds);
      addLog('상태 새로고침 완료', `${userIds.length}명 조회`);
      showToast('대기열 상태를 새로고침했습니다.');
    } catch (e) {
      console.error(e);
      showToast('대기열 상태 조회에 실패했습니다.', true);
    }
  };

  window.validateQueueTestToken = async function (userId) {
    const eventId = eventIdValue();
    if (!eventId) return;
    const user = users.get(userId);
    if (!user?.token) {
      showToast('검증할 토큰이 없습니다.', true);
      return;
    }

    try {
      const res = await Fetch(`${QUEUE_TEST_URL}/validate`, {
        method: 'POST',
        body: {
          eventId: Number(eventId),
          userId,
          token: user.token
        }
      });
      if (!res.ok) {
        showToast('토큰 검증에 실패했습니다.', true);
        return;
      }
      const data = await res.json();
      addLog(`${userId} 토큰 검증`, data.allowed ? 'OK' : data.reason);
      showToast(data.allowed ? '토큰이 유효합니다.' : '토큰이 유효하지 않습니다.', !data.allowed);
    } catch (e) {
      console.error(e);
      showToast('토큰 검증에 실패했습니다.', true);
    }
  };

  window.completeQueueTestUser = async function (userId) {
    const eventId = eventIdValue();
    if (!eventId) return;
    const user = users.get(userId);
    if (!user?.token || user.status !== 'READY') {
      showToast('완료 처리할 READY 토큰이 없습니다.', true);
      return;
    }

    try {
      const res = await Fetch(`${QUEUE_TEST_URL}/events/${encodeURIComponent(eventId)}/complete?userId=${encodeURIComponent(userId)}&token=${encodeURIComponent(user.token)}`, {
        method: 'POST'
      });
      if (!res.ok) {
        showToast('완료 처리에 실패했습니다.', true);
        return;
      }
      const message = await res.text();
      users.set(userId, {
        ...user,
        status: 'COMPLETED',
        rank: '-',
        token: null,
        expiresInSeconds: null,
        lastCheckedAt: new Date().toLocaleTimeString()
      });
      renderUsers();
      addLog(`${userId} 완료`, message);
      await refreshWaitingUsers(eventId);
      showToast('완료 처리 후 대기 유저 상태를 새로고침했습니다.');
    } catch (e) {
      console.error(e);
      showToast('완료 처리에 실패했습니다.', true);
    }
  };

  window.clearQueueTestRows = function () {
    users.clear();
    renderUsers();
    addLog('화면 상태 초기화');
  };

  window.clearQueueTestEvent = async function () {
    const eventId = eventIdValue();
    if (!eventId) return;
    if (!window.confirm('이 이벤트의 대기열 Redis 데이터를 초기화할까요?')) return;

    try {
      const res = await Fetch(`${QUEUE_TEST_URL}/events/${encodeURIComponent(eventId)}`, {
        method: 'DELETE'
      });
      if (!res.ok) {
        showToast('대기열 초기화에 실패했습니다.', true);
        return;
      }
      const message = await res.text();
      users.clear();
      renderUsers();
      addLog('대기열 Redis 초기화', message);
      showToast(message || '대기열이 초기화되었습니다.');
    } catch (e) {
      console.error(e);
      showToast('대기열 초기화에 실패했습니다.', true);
    }
  };

  renderLogs();
  renderUsers();
})();
