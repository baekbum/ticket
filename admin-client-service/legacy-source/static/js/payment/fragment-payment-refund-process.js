(function () {
  const REFUND_PROCESS_URL = `${base()}/admin/api/${API.VERSION}/payment/refund-process`;
  const headers = { 'Content-Type': 'application/json' };

  let currentSearchFilters = {
    paymentRefundProcessId: null,
    reservationId: null,
    paymentNo: null,
    method: null,
    status: null
  };
  let serverTotalPages = 1;
  let currentRefundProcesses = [];
  let currentDetailProcess = null;

  function inputValue(id) {
    return document.getElementById(id)?.value?.trim() || '';
  }

  function numberValue(id) {
    const value = inputValue(id);
    if (!value) return null;
    const number = Number(value);
    return Number.isFinite(number) && number > 0 ? number : null;
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

  function money(value) {
    const number = Number(value || 0);
    return `${number.toLocaleString()}원`;
  }

  function methodLabel(method) {
    const labels = {
      CREDIT_CARD: '카드',
      BANK_TRANSFER: '무통장'
    };
    return labels[method] || method || '-';
  }

  function statusLabel(status) {
    const labels = {
      REQUESTED: '요청 생성',
      GATEWAY_SUCCEEDED: 'gateway 성공',
      GATEWAY_FAILED: 'gateway 실패',
      LOCAL_SUCCEEDED: '처리 완료',
      LOCAL_FAILED: '로컬 실패'
    };
    return labels[status] || status || '-';
  }

  function statusBadge(status) {
    const classMap = {
      REQUESTED: 'badge-pending',
      GATEWAY_SUCCEEDED: 'badge-partial',
      GATEWAY_FAILED: 'badge-cancelled',
      LOCAL_SUCCEEDED: 'badge-paid',
      LOCAL_FAILED: 'badge-cancelled'
    };
    return `<span class="badge ${classMap[status] || 'badge-expired'}">${escapeHtml(statusLabel(status))}</span>`;
  }

  function buildCond(pageZeroIndexed) {
    const pageSize = parseInt(document.getElementById('pagination-size').value, 10);
    const cond = {
      page: pageZeroIndexed,
      size: pageSize,
      sort: ['paymentRefundProcessId-desc']
    };
    if (currentSearchFilters.paymentRefundProcessId) cond.paymentRefundProcessId = currentSearchFilters.paymentRefundProcessId;
    if (currentSearchFilters.reservationId) cond.reservationId = currentSearchFilters.reservationId;
    if (currentSearchFilters.paymentNo) cond.paymentNo = currentSearchFilters.paymentNo;
    if (currentSearchFilters.method) cond.method = currentSearchFilters.method;
    if (currentSearchFilters.status === 'FAILED') cond.statuses = ['GATEWAY_FAILED', 'LOCAL_FAILED'];
    else if (currentSearchFilters.status) cond.status = currentSearchFilters.status;
    return cond;
  }

  window.loadPaymentRefundProcessList = async function (pageZeroIndexed = 0) {
    try {
      const res = await Fetch(`${REFUND_PROCESS_URL}/select`, {
        method: 'POST',
        headers,
        body: JSON.stringify(buildCond(pageZeroIndexed))
      });

      if (!res.ok) {
        showToast('환불 처리 현황 조회에 실패했습니다.', true);
        return;
      }

      const paged = await res.json();
      currentRefundProcesses = paged.content || [];
      serverTotalPages = Math.max(paged.page?.totalPages || paged.totalPages || 1, 1);
      const totalCount = paged.totalElements ?? paged.page?.totalElements ?? currentRefundProcesses.length;
      const pageSize = parseInt(document.getElementById('pagination-size').value, 10);

      document.getElementById('pagination-total').textContent = serverTotalPages;
      document.getElementById('pagination-current').value = pageZeroIndexed + 1;
      document.getElementById('pagination-total-count').textContent = totalCount;

      const tbody = document.getElementById('payment-refund-process-table-body');
      tbody.innerHTML = '';

      if (currentRefundProcesses.length === 0) {
        tbody.innerHTML = `<tr><td colspan="10" style="text-align:center;color:var(--text-muted);padding:2rem;">조회된 환불 처리 현황이 없습니다.</td></tr>`;
        return;
      }

      currentRefundProcesses.forEach((process, index) => {
        const rowNumber = pageZeroIndexed * pageSize + index + 1;
        const tr = document.createElement('tr');
        tr.className = 'clickable-row';
        tr.onclick = event => {
          if (event.target.closest('button')) return;
          openRefundProcessDetailModal(process.paymentRefundProcessId);
        };
        tr.innerHTML = `
          <td style="text-align:center;color:var(--text-muted);">${rowNumber}</td>
          <td><strong>${escapeHtml(process.paymentRefundProcessId)}</strong></td>
          <td>${escapeHtml(process.reservationId || '-')}</td>
          <td>${escapeHtml(process.paymentNo || '-')}</td>
          <td>${escapeHtml(methodLabel(process.method))}</td>
          <td class="number-cell">${money(process.refundAmount)}</td>
          <td>${statusBadge(process.status)}</td>
          <td>${escapeHtml(process.retryCount ?? 0)}</td>
          <td>${escapeHtml(process.createdAt || '-')}</td>
          <td>
            <button class="btn btn-sm btn-outline" onclick="openRefundProcessDetailModal(${Number(process.paymentRefundProcessId)})">
              상세
            </button>
          </td>
        `;
        tbody.appendChild(tr);
      });
    } catch (e) {
      console.error('Refund process list load failed', e);
      showToast('환불 처리 현황 통신 오류가 발생했습니다.', true);
    }
  };

  window.triggerRefundProcessSearch = function () {
    currentSearchFilters = {
      paymentRefundProcessId: numberValue('refund-process-search-id'),
      reservationId: numberValue('refund-process-search-reservation-id'),
      paymentNo: inputValue('refund-process-search-payment-no') || null,
      method: inputValue('refund-process-search-method') || null,
      status: inputValue('refund-process-search-status') || null
    };
    loadPaymentRefundProcessList(0);
  };

  window.resetRefundProcessSearch = function () {
    currentSearchFilters = {
      paymentRefundProcessId: null,
      reservationId: null,
      paymentNo: null,
      method: null,
      status: null
    };
    setValue('refund-process-search-id', '');
    setValue('refund-process-search-reservation-id', '');
    setValue('refund-process-search-payment-no', '');
    setValue('refund-process-search-method', '');
    setValue('refund-process-search-status', '');
    loadPaymentRefundProcessList(0);
  };

  function detailItem(label, value, html = false) {
    return `
      <div class="detail-item">
        <span class="detail-label">${escapeHtml(label)}</span>
        <strong class="detail-value">${html ? value : escapeHtml(value || '-')}</strong>
      </div>
    `;
  }

  window.openRefundProcessDetailModal = function (paymentRefundProcessId) {
    currentDetailProcess = currentRefundProcesses.find(item => Number(item.paymentRefundProcessId) === Number(paymentRefundProcessId));
    if (!currentDetailProcess) {
      showToast('환불 처리 정보를 찾을 수 없습니다.', true);
      return;
    }

    document.getElementById('refund-process-detail-id').value = currentDetailProcess.paymentRefundProcessId;
    document.getElementById('refund-process-basic-grid').innerHTML = [
      detailItem('프로세스 ID', currentDetailProcess.paymentRefundProcessId),
      detailItem('예매 ID', currentDetailProcess.reservationId),
      detailItem('결제 ID', currentDetailProcess.paymentId),
      detailItem('결제 번호', currentDetailProcess.paymentNo),
      detailItem('결제 수단', methodLabel(currentDetailProcess.method))
    ].join('');
    document.getElementById('refund-process-refund-grid').innerHTML = [
      detailItem('환불 금액', money(currentDetailProcess.refundAmount)),
      detailItem('취소 구분', currentDetailProcess.fullCancellation ? '전체 취소' : '부분 취소'),
      detailItem('선택 티켓 ID', currentDetailProcess.selectedTicketIds),
      detailItem('환불 계좌', [currentDetailProcess.refundBankCompany, currentDetailProcess.refundAccountNumberMasked].filter(Boolean).join(' ')),
      detailItem('예금주', currentDetailProcess.refundAccountHolder)
    ].join('');
    document.getElementById('refund-process-status-grid').innerHTML = [
      detailItem('현재 상태', statusBadge(currentDetailProcess.status), true),
      detailItem('실패 사유', currentDetailProcess.failureReason),
      detailItem('재시도 횟수', currentDetailProcess.retryCount ?? 0),
      detailItem('마지막 시도일', currentDetailProcess.lastTriedAt),
      detailItem('완료일', currentDetailProcess.completedAt),
      detailItem('생성일', currentDetailProcess.createdAt),
      detailItem('수정일', currentDetailProcess.updatedAt)
    ].join('');

    const canReconcile = ['GATEWAY_FAILED', 'LOCAL_FAILED', 'GATEWAY_SUCCEEDED'].includes(currentDetailProcess.status);
    if (canReconcile) {
      document.getElementById('refund-process-status-grid').insertAdjacentHTML('beforeend',
        detailItem('처리 안내', 'PG에서 환불 성공이 확인되면 Ticksy에도 반영합니다.'));
    }
    document.getElementById('refund-process-reconcile-btn').style.display = canReconcile ? 'inline-flex' : 'none';

    document.getElementById('refund-process-detail-modal').style.display = 'flex';
  };

  window.closeRefundProcessDetailModal = function () {
    document.getElementById('refund-process-detail-modal').style.display = 'none';
    currentDetailProcess = null;
  };

  window.reconcileRefundProcess = async function () {
    const button = document.getElementById('refund-process-reconcile-btn');
    if (!currentDetailProcess || button.disabled) return;
    const processId = currentDetailProcess.paymentRefundProcessId;
    button.disabled = true;
    button.querySelector('span').textContent = '확인 중…';
    try {
      const response = await Fetch(`${REFUND_PROCESS_URL}/reconcile/id/${processId}`, { method: 'POST', headers });
      if (!response.ok) throw new Error();
      const { outcome } = await response.json();
      if (outcome === 'APPLIED') {
        if (currentDetailProcess?.paymentRefundProcessId === processId) closeRefundProcessDetailModal();
        showToast('Ticksy에 환불 결과가 반영됐습니다.');
        loadPaymentRefundProcessList(Math.max(parseInt(document.getElementById('pagination-current').value, 10) - 1, 0));
        return;
      }
      const messages = {
        NOT_FOUND: 'PG에서 이 환불 신청을 찾지 못했습니다.',
        REQUESTED: 'PG에서 접수했으며 환불 결과는 아직 확정되지 않았습니다.',
        WITHDRAWN: 'PG에서 철회 완료로 확인됐습니다.',
        NO_GATEWAY_PAYMENT: '이 신청에는 조회할 PG 결제 번호가 없습니다.'
      };
      if (!Object.hasOwn(messages, outcome)) throw new Error();
      showToast(messages[outcome]);
    } catch {
      showToast('PG 결과 확인 또는 Ticksy 반영에 실패했습니다. 다시 시도해 주세요.', true);
    } finally {
      button.disabled = false;
      button.querySelector('span').textContent = 'PG 결과 확인·반영';
    }
  };

  window.openRefundProcessAuditLog = function () {
    if (!currentDetailProcess) return;

    const paymentRefundProcessId = currentDetailProcess.paymentRefundProcessId;
    closeRefundProcessDetailModal();
    window.switchMenuWithContext('auditLog', {
      auditSearch: {
        serviceName: 'ticket-service',
        targetType: 'PAYMENT_REFUND_PROCESS',
        targetId: String(paymentRefundProcessId)
      }
    });
  };

  window.Pagination.register({
    load: window.loadPaymentRefundProcessList,
    getTotalPages: () => serverTotalPages
  });

  window.addEventListener('admin:fragment-loaded', event => {
    if (event.detail?.menuName !== 'paymentRefundProcess') return;
    const reservationId = Number(event.detail.context?.reservationId);
    if (Number.isSafeInteger(reservationId) && reservationId > 0) {
      currentSearchFilters.reservationId = reservationId;
      setValue('refund-process-search-reservation-id', reservationId);
    }
    if (event.detail.context?.status === 'FAILED') {
      currentSearchFilters.status = 'FAILED';
      setValue('refund-process-search-status', 'FAILED');
    }
    loadPaymentRefundProcessList(0);
  });
})();
