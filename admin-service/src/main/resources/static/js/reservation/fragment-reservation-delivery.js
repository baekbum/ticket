(function () {
  const DELIVERY_URL = `${base()}/admin/api/${API.VERSION}/reservation/delivery`;
  const headers = { 'Content-Type': 'application/json' };

  let currentDeliveryList = [];
  let currentSearchFilters = { userId: null, reservationId: null, status: null };
  let serverTotalPages = 1;
  let currentTrackingDeliveryId = null;

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

  function statusLabel(status) {
    const labels = {
      READY: '준비 대기',
      PREPARING: '준비중',
      SHIPPED: '발송',
      DELIVERED: '완료',
      RETURNED: '반송',
      CANCELLED: '취소'
    };
    return labels[status] || status || '-';
  }

  function statusBadge(status) {
    const classMap = {
      READY: 'badge-pending',
      PREPARING: 'badge-partial',
      SHIPPED: 'badge-paid',
      DELIVERED: 'badge-paid',
      RETURNED: 'badge-expired',
      CANCELLED: 'badge-cancelled'
    };
    return `<span class="badge ${classMap[status] || 'badge-expired'}">${escapeHtml(statusLabel(status))}</span>`;
  }

  function buildCond(pageZeroIndexed) {
    const pageSize = parseInt(document.getElementById('pagination-size').value, 10);
    const cond = {
      page: pageZeroIndexed,
      size: pageSize,
      sort: ['reservationDeliveryId-desc']
    };
    if (currentSearchFilters.userId) cond.userId = currentSearchFilters.userId;
    if (currentSearchFilters.reservationId) cond.reservationId = currentSearchFilters.reservationId;
    if (currentSearchFilters.status) cond.status = currentSearchFilters.status;
    return cond;
  }

  window.loadReservationDeliveryList = async function (pageZeroIndexed = 0) {
    try {
      const res = await Fetch(`${DELIVERY_URL}/select`, {
        method: 'POST',
        headers,
        body: JSON.stringify(buildCond(pageZeroIndexed))
      });

      if (!res.ok) {
        showToast('배송 목록 조회에 실패했습니다.', true);
        return;
      }

      const paged = await res.json();
      currentDeliveryList = paged.content || [];
      serverTotalPages = Math.max(paged.page?.totalPages || paged.totalPages || 1, 1);
      const totalCount = paged.totalElements ?? paged.page?.totalElements ?? currentDeliveryList.length;
      const pageSize = parseInt(document.getElementById('pagination-size').value, 10);

      document.getElementById('pagination-total').textContent = serverTotalPages;
      document.getElementById('pagination-current').value = pageZeroIndexed + 1;
      document.getElementById('pagination-total-count').textContent = totalCount;

      const tbody = document.getElementById('reservation-delivery-table-body');
      tbody.innerHTML = '';

      if (currentDeliveryList.length === 0) {
        tbody.innerHTML = `<tr><td colspan="11" style="text-align:center;color:var(--text-muted);padding:2rem;">조회된 배송 정보가 없습니다.</td></tr>`;
        return;
      }

      currentDeliveryList.forEach((delivery, index) => {
        const rowNumber = pageZeroIndexed * pageSize + index + 1;
        const fullAddress = [delivery.address, delivery.detailAddress].filter(Boolean).join(' ');
        const tr = document.createElement('tr');
        tr.innerHTML = `
          <td style="text-align:center;color:var(--text-muted);">${rowNumber}</td>
          <td><strong>${escapeHtml(delivery.reservationDeliveryId)}</strong></td>
          <td>${escapeHtml(delivery.reservationId || '-')}</td>
          <td>${escapeHtml(delivery.recipientName || '-')}</td>
          <td>${escapeHtml(delivery.recipientPhone || '-')}</td>
          <td>${escapeHtml(delivery.zipCode || '-')}</td>
          <td title="${escapeHtml(fullAddress)}">${escapeHtml(fullAddress || '-')}</td>
          <td>${statusBadge(delivery.status)}</td>
          <td>${escapeHtml(delivery.carrier || '-')}</td>
          <td>${escapeHtml(delivery.trackingNumber || '-')}</td>
          <td class="actions"><div class="delivery-actions">${renderActions(delivery)}</div></td>
        `;
        tbody.appendChild(tr);
      });
    } catch (e) {
      console.error('Delivery list load failed', e);
      showToast('배송 목록 통신 오류가 발생했습니다.', true);
    }
  };

  function renderActions(delivery) {
    const id = Number(delivery.reservationDeliveryId);
    const status = delivery.status;
    const buttons = [];

    if (status === 'READY') {
      buttons.push(`<button class="btn btn-sm btn-outline" onclick="prepareDelivery(${id})">준비</button>`);
    }
    if (status === 'READY' || status === 'PREPARING') {
      buttons.push(`<button class="btn btn-sm btn-outline" onclick="openDeliveryTrackingModal(${id}, 'tracking')">운송장</button>`);
      buttons.push(`<button class="btn btn-sm" onclick="openDeliveryTrackingModal(${id}, 'ship')">발송</button>`);
    }
    if (status === 'SHIPPED') {
      buttons.push(`<button class="btn btn-sm" onclick="changeDeliveryState(${id}, 'deliver')">완료</button>`);
      buttons.push(`<button class="btn btn-sm btn-outline" onclick="changeDeliveryState(${id}, 'return')">반송</button>`);
    }
    if (status !== 'DELIVERED' && status !== 'RETURNED' && status !== 'CANCELLED') {
      buttons.push(`<button class="btn btn-sm btn-danger" onclick="changeDeliveryState(${id}, 'cancel')">취소</button>`);
    }

    return buttons.join('') || '<span style="color:var(--text-muted);">처리 완료</span>';
  }

  window.triggerDeliverySearch = function () {
    currentSearchFilters = {
      userId: inputValue('delivery-search-user-id') || null,
      reservationId: numberValue('delivery-search-reservation-id'),
      status: inputValue('delivery-search-status') || null
    };
    syncDeliveryTabs();
    loadReservationDeliveryList(0);
  };

  window.resetDeliverySearch = function () {
    currentSearchFilters = { userId: null, reservationId: null, status: null };
    setValue('delivery-search-user-id', '');
    setValue('delivery-search-reservation-id', '');
    setValue('delivery-search-status', '');
    syncDeliveryTabs();
    loadReservationDeliveryList(0);
  };

  window.quickDeliveryStatus = function (button) {
    const status = button?.dataset?.status || '';
    currentSearchFilters.status = status || null;
    setValue('delivery-search-status', status);
    syncDeliveryTabs();
    loadReservationDeliveryList(0);
  };

  function syncDeliveryTabs() {
    document.querySelectorAll('.delivery-tab').forEach(tab => {
      tab.classList.toggle('active', (tab.dataset.status || '') === (currentSearchFilters.status || ''));
    });
  }

  window.prepareDelivery = function (id) {
    changeDeliveryState(id, 'prepare');
  };

  window.changeDeliveryState = async function (id, action) {
    try {
      const res = await Fetch(`${DELIVERY_URL}/${action}/id/${id}`, { method: 'PUT' });
      if (!res.ok) {
        showToast('배송 상태 변경에 실패했습니다.', true);
        return;
      }
      showToast('배송 상태가 변경되었습니다.');
      loadReservationDeliveryList(Math.max(parseInt(document.getElementById('pagination-current').value, 10) - 1, 0));
    } catch (e) {
      console.error('Delivery state change failed', e);
      showToast('배송 상태 변경 통신 오류가 발생했습니다.', true);
    }
  };

  window.openDeliveryTrackingModal = function (id, mode) {
    currentTrackingDeliveryId = id;
    const delivery = currentDeliveryList.find(item => Number(item.reservationDeliveryId) === Number(id));
    setValue('delivery-tracking-mode', mode);
    document.getElementById('delivery-tracking-title').textContent = mode === 'ship' ? '운송장 등록 후 발송' : '운송장 등록';
    document.getElementById('delivery-tracking-id').textContent = id;
    setValue('delivery-carrier', delivery?.carrier || '');
    setValue('delivery-tracking-number', delivery?.trackingNumber || '');
    document.getElementById('delivery-tracking-modal').style.display = 'flex';
  };

  window.closeDeliveryTrackingModal = function () {
    document.getElementById('delivery-tracking-modal').style.display = 'none';
    currentTrackingDeliveryId = null;
  };

  window.submitDeliveryTracking = async function () {
    const mode = inputValue('delivery-tracking-mode') || 'tracking';
    const payload = {
      carrier: inputValue('delivery-carrier'),
      trackingNumber: inputValue('delivery-tracking-number')
    };
    if (!payload.carrier || !payload.trackingNumber) {
      showToast('택배사와 운송장 번호를 입력해주세요.', true);
      return;
    }

    try {
      const res = await Fetch(`${DELIVERY_URL}/${mode}/id/${currentTrackingDeliveryId}`, {
        method: 'PUT',
        headers,
        body: JSON.stringify(payload)
      });
      if (!res.ok) {
        showToast('운송장 처리에 실패했습니다.', true);
        return;
      }
      closeDeliveryTrackingModal();
      showToast(mode === 'ship' ? '발송 처리되었습니다.' : '운송장이 저장되었습니다.');
      loadReservationDeliveryList(Math.max(parseInt(document.getElementById('pagination-current').value, 10) - 1, 0));
    } catch (e) {
      console.error('Delivery tracking submit failed', e);
      showToast('운송장 처리 통신 오류가 발생했습니다.', true);
    }
  };

  window.Pagination.register({
    load: window.loadReservationDeliveryList,
    getTotalPages: () => serverTotalPages
  });

  loadReservationDeliveryList(0);
})();
