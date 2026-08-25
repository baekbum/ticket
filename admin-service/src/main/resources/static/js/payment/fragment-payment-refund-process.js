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
    if (currentSearchFilters.status) cond.status = currentSearchFilters.status;
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

    const isGatewayFailed = currentDetailProcess.status === 'GATEWAY_FAILED';
    const isLocalCompletable = currentDetailProcess.status === 'GATEWAY_SUCCEEDED' || currentDetailProcess.status === 'LOCAL_FAILED';
    const gatewayRetryGuide = isGatewayFailed
      ? detailItem('처리 안내', 'gateway 실패 건은 사용자가 환불 계좌 정보를 포함해 취소 요청을 다시 보내야 합니다.')
      : '';

    if (gatewayRetryGuide) {
      document.getElementById('refund-process-status-grid').insertAdjacentHTML('beforeend', gatewayRetryGuide);
    }
    document.getElementById('refund-process-local-complete-btn').style.display = isLocalCompletable ? 'inline-flex' : 'none';

    document.getElementById('refund-process-detail-modal').style.display = 'flex';
  };

  window.closeRefundProcessDetailModal = function () {
    document.getElementById('refund-process-detail-modal').style.display = 'none';
    currentDetailProcess = null;
  };

  window.completeRefundProcessLocal = async function () {
    if (!currentDetailProcess) return;
    if (!confirm('gateway 재호출 없이 로컬 상태만 완료 처리합니다. 계속 진행할까요?')) return;

    await processManualAction(
      `${REFUND_PROCESS_URL}/local-complete/id/${currentDetailProcess.paymentRefundProcessId}`,
      null,
      '로컬 반영 완료 처리를 완료했습니다.'
    );
  };

  async function processManualAction(url, payload, successMessage) {
    try {
      const options = { method: 'PUT', headers };
      if (payload) {
        options.body = JSON.stringify(payload);
      }
      const res = await Fetch(url, options);
      if (!res.ok) {
        showToast('환불 처리 작업에 실패했습니다.', true);
        return;
      }
      closeRefundProcessDetailModal();
      showToast(successMessage);
      loadPaymentRefundProcessList(Math.max(parseInt(document.getElementById('pagination-current').value, 10) - 1, 0));
    } catch (e) {
      console.error('Refund process manual action failed', e);
      showToast('환불 처리 작업 통신 오류가 발생했습니다.', true);
    }
  }

  window.Pagination.register({
    load: window.loadPaymentRefundProcessList,
    getTotalPages: () => serverTotalPages
  });

  loadPaymentRefundProcessList(0);
})();
