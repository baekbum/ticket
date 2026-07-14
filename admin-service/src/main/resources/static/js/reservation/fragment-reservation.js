(function () {
  const RESERVATION_URL = `${base()}/admin/api/${API.VERSION}/reservation`;
  const headers = { 'Content-Type': 'application/json' };

  let currentReservationList = [];
  let currentSearchFilters = {
    userId: null,
    eventId: null,
    seatId: null,
    startDate: null,
    endDate: null,
    status: null
  };
  let currentSortFilters = { reservationId: 'desc' };
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

  function statusLabel(status) {
    const labels = {
      PENDING_PAYMENT: '결제 대기',
      PAID: '결제 완료',
      PARTIALLY_CANCELLED: '부분 취소',
      CANCELLED: '전체 취소',
      EXPIRED: '만료'
    };
    return labels[status] || status || '-';
  }

  function statusBadge(status) {
    const classMap = {
      PENDING_PAYMENT: 'badge-pending',
      PAID: 'badge-paid',
      PARTIALLY_CANCELLED: 'badge-partial',
      CANCELLED: 'badge-cancelled',
      EXPIRED: 'badge-expired'
    };
    return `<span class="badge ${classMap[status] || 'badge-expired'}">${escapeHtml(statusLabel(status))}</span>`;
  }

  function buildSortArray() {
    return Object.keys(currentSortFilters).reduce((acc, field) => {
      if (currentSortFilters[field]) acc.push(`${field}-${currentSortFilters[field]}`);
      return acc;
    }, []);
  }

  function buildCond(pageZeroIndexed) {
    const pageSize = parseInt(document.getElementById('pagination-size').value, 10);
    const cond = {
      page: pageZeroIndexed,
      size: pageSize,
      sort: buildSortArray()
    };

    if (currentSearchFilters.userId) cond.userId = currentSearchFilters.userId;
    if (currentSearchFilters.eventId) cond.eventId = currentSearchFilters.eventId;
    if (currentSearchFilters.seatId) cond.seatId = currentSearchFilters.seatId;
    if (currentSearchFilters.startDate) cond.startDate = currentSearchFilters.startDate;
    if (currentSearchFilters.endDate) cond.endDate = currentSearchFilters.endDate;
    if (currentSearchFilters.status) cond.status = currentSearchFilters.status;

    return cond;
  }

  window.loadReservationList = async function (pageZeroIndexed = 0) {
    try {
      const res = await Fetch(`${RESERVATION_URL}/select`, {
        method: 'POST',
        headers,
        body: JSON.stringify(buildCond(pageZeroIndexed))
      });

      if (!res.ok) {
        showToast('예매 목록 조회에 실패했습니다.', true);
        return;
      }

      const paged = await res.json();
      currentReservationList = paged.content || [];
      serverTotalPages = Math.max(paged.page?.totalPages || paged.totalPages || 1, 1);
      const totalCount = paged.totalElements ?? paged.page?.totalElements ?? currentReservationList.length;
      const pageSize = parseInt(document.getElementById('pagination-size').value, 10);

      document.getElementById('pagination-total').textContent = serverTotalPages;
      document.getElementById('pagination-current').value = pageZeroIndexed + 1;
      document.getElementById('pagination-total-count').textContent = totalCount;

      const tbody = document.getElementById('reservation-table-body');
      tbody.innerHTML = '';

      if (currentReservationList.length === 0) {
        tbody.innerHTML = `<tr><td colspan="11" style="text-align:center;color:var(--text-muted);padding:2rem;">조회된 예매 내역이 없습니다.</td></tr>`;
        syncReservationSortHeaderUI();
        return;
      }

      currentReservationList.forEach((reservation, index) => {
        const rowNumber = pageZeroIndexed * pageSize + index + 1;
        const tr = document.createElement('tr');
        tr.innerHTML = `
          <td style="text-align:center;color:var(--text-muted);">${rowNumber}</td>
          <td><strong>${escapeHtml(reservation.reservationId)}</strong></td>
          <td title="${escapeHtml(reservation.orderId)}">${escapeHtml(reservation.orderId || '-')}</td>
          <td>${escapeHtml(reservation.userId || '-')}</td>
          <td>${escapeHtml(reservation.eventId || '-')}</td>
          <td title="${escapeHtml(reservation.eventTitle)}">${escapeHtml(reservation.eventTitle || '-')}</td>
          <td title="${escapeHtml(reservation.venue)}">${escapeHtml(reservation.venue || '-')}</td>
          <td>${escapeHtml(reservation.reservedDate || '-')}</td>
          <td style="text-align:right;">${Number(reservation.ticketCount || 0).toLocaleString()}매</td>
          <td>${statusBadge(reservation.status)}</td>
          <td class="actions" onclick="event.stopPropagation()">
            <button class="btn btn-sm btn-outline" onclick="event.stopPropagation(); openReservationDetailModal(${reservation.reservationId})">상세</button>
          </td>
        `;
        tr.onclick = () => openReservationDetailModal(reservation.reservationId);
        tbody.appendChild(tr);
      });

      syncReservationSortHeaderUI();
    } catch (e) {
      console.error('Reservation list load failed', e);
      showToast('예매 목록 통신 오류가 발생했습니다.', true);
    }
  };

  window.triggerReservationSearch = function () {
    currentSearchFilters = {
      ...currentSearchFilters,
      userId: inputValue('reservation-search-user-id') || null
    };
    setValue('cond-reservation-user-id', currentSearchFilters.userId);
    loadReservationList(0);
  };

  window.resetReservationSearch = function () {
    currentSearchFilters = {
      userId: null,
      eventId: null,
      seatId: null,
      startDate: null,
      endDate: null,
      status: null
    };
    setValue('reservation-search-user-id', '');
    setValue('cond-reservation-user-id', '');
    setValue('cond-reservation-status', '');
    setValue('cond-reservation-event-id', '');
    setValue('cond-reservation-seat-id', '');
    setValue('cond-reservation-start-date', '');
    setValue('cond-reservation-end-date', '');
    loadReservationList(0);
  };

  window.openReservationSearchModal = function () {
    setValue('cond-reservation-user-id', currentSearchFilters.userId);
    setValue('cond-reservation-status', currentSearchFilters.status);
    setValue('cond-reservation-event-id', currentSearchFilters.eventId);
    setValue('cond-reservation-seat-id', currentSearchFilters.seatId);
    setValue('cond-reservation-start-date', currentSearchFilters.startDate);
    setValue('cond-reservation-end-date', currentSearchFilters.endDate);
    document.getElementById('reservation-search-modal').style.display = 'flex';
  };

  window.closeReservationSearchModal = function () {
    document.getElementById('reservation-search-modal').style.display = 'none';
  };

  window.submitReservationDetailedSearch = function () {
    const startDate = inputValue('cond-reservation-start-date') || null;
    const endDate = inputValue('cond-reservation-end-date') || null;
    if ((startDate && !endDate) || (!startDate && endDate)) {
      showToast('검색 시작일과 종료일을 함께 입력해주세요.', true);
      return;
    }
    if (startDate && endDate && startDate > endDate) {
      showToast('검색 시작일은 종료일보다 늦을 수 없습니다.', true);
      return;
    }

    currentSearchFilters = {
      userId: inputValue('cond-reservation-user-id') || null,
      eventId: numberValue('cond-reservation-event-id'),
      seatId: numberValue('cond-reservation-seat-id'),
      startDate,
      endDate,
      status: inputValue('cond-reservation-status') || null
    };

    setValue('reservation-search-user-id', currentSearchFilters.userId);
    closeReservationSearchModal();
    loadReservationList(0);
  };

  window.handleReservationSortClick = function (headerEl, event) {
    const field = headerEl.getAttribute('data-sort-field');
    const current = currentSortFilters[field];
    const next = !current ? 'asc' : current === 'asc' ? 'desc' : null;

    if (!event.shiftKey) currentSortFilters = {};
    if (next) currentSortFilters[field] = next;
    else delete currentSortFilters[field];

    loadReservationList(0);
  };

  function syncReservationSortHeaderUI() {
    document.querySelectorAll('.reservation-table .sortable').forEach(th => {
      const dir = currentSortFilters[th.getAttribute('data-sort-field')];
      const icon = th.querySelector('.sort-icon');
      th.classList.remove('asc', 'desc');
      if (dir === 'asc') {
        th.classList.add('asc');
        icon.textContent = '▲';
      } else if (dir === 'desc') {
        th.classList.add('desc');
        icon.textContent = '▼';
      } else {
        icon.textContent = '↕';
      }
    });
  }

  window.openReservationDetailModal = async function (reservationId) {
    let reservation = currentReservationList.find(item => Number(item.reservationId) === Number(reservationId));

    if (!reservation) {
      try {
        const res = await Fetch(`${RESERVATION_URL}/select/id/${reservationId}`, { method: 'GET' });
        if (!res.ok) {
          showToast('예매 상세 조회에 실패했습니다.', true);
          return;
        }
        reservation = await res.json();
      } catch (e) {
        showToast('예매 상세 통신 오류가 발생했습니다.', true);
        return;
      }
    }

    document.getElementById('detail-reservation-id').textContent = reservation.reservationId ?? '-';
    document.getElementById('detail-order-id').textContent = reservation.orderId || '-';
    document.getElementById('detail-user-id').textContent = reservation.userId || '-';
    document.getElementById('detail-status').innerHTML = statusBadge(reservation.status);
    document.getElementById('detail-event-id').textContent = reservation.eventId || '-';
    document.getElementById('detail-event-title').textContent = reservation.eventTitle || '-';
    document.getElementById('detail-venue').textContent = reservation.venue || '-';
    document.getElementById('detail-event-date-time').textContent = reservation.eventDateTime || '-';
    document.getElementById('detail-reserved-date').textContent = reservation.reservedDate || '-';
    document.getElementById('detail-ticket-count').textContent = `${Number(reservation.ticketCount || 0).toLocaleString()}매`;
    document.getElementById('reservation-detail-modal').style.display = 'flex';
  };

  window.closeReservationDetailModal = function () {
    document.getElementById('reservation-detail-modal').style.display = 'none';
  };

  window.Pagination.register({
    load: window.loadReservationList,
    getTotalPages: () => serverTotalPages
  });

  loadReservationList(0);
})();
