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
      const refundProcesses = paged.content || [];
      serverTotalPages = Math.max(paged.page?.totalPages || paged.totalPages || 1, 1);
      const totalCount = paged.totalElements ?? paged.page?.totalElements ?? refundProcesses.length;
      const pageSize = parseInt(document.getElementById('pagination-size').value, 10);

      document.getElementById('pagination-total').textContent = serverTotalPages;
      document.getElementById('pagination-current').value = pageZeroIndexed + 1;
      document.getElementById('pagination-total-count').textContent = totalCount;

      const tbody = document.getElementById('payment-refund-process-table-body');
      tbody.innerHTML = '';

      if (refundProcesses.length === 0) {
        tbody.innerHTML = `<tr><td colspan="14" style="text-align:center;color:var(--text-muted);padding:2rem;">조회된 환불 처리 현황이 없습니다.</td></tr>`;
        return;
      }

      refundProcesses.forEach((process, index) => {
        const rowNumber = pageZeroIndexed * pageSize + index + 1;
        const refundAccount = [process.refundBankCompany, process.refundAccountNumberMasked].filter(Boolean).join(' ');
        const tr = document.createElement('tr');
        tr.innerHTML = `
          <td style="text-align:center;color:var(--text-muted);">${rowNumber}</td>
          <td><strong>${escapeHtml(process.paymentRefundProcessId)}</strong></td>
          <td>${escapeHtml(process.reservationId || '-')}</td>
          <td>${escapeHtml(process.paymentNo || '-')}</td>
          <td>${escapeHtml(methodLabel(process.method))}</td>
          <td class="number-cell">${money(process.refundAmount)}</td>
          <td>${process.fullCancellation ? '전체 취소' : '부분 취소'}</td>
          <td>${statusBadge(process.status)}</td>
          <td>${escapeHtml(process.retryCount ?? 0)}</td>
          <td title="${escapeHtml(refundAccount)}">${escapeHtml(refundAccount || '-')}</td>
          <td>${escapeHtml(process.refundAccountHolder || '-')}</td>
          <td title="${escapeHtml(process.failureReason || '')}">${escapeHtml(process.failureReason || '-')}</td>
          <td>${escapeHtml(process.lastTriedAt || '-')}</td>
          <td>${escapeHtml(process.completedAt || '-')}</td>
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

  window.Pagination.register({
    load: window.loadPaymentRefundProcessList,
    getTotalPages: () => serverTotalPages
  });

  loadPaymentRefundProcessList(0);
})();
