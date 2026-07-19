(function () {
  const RESERVATION_URL = `${base()}/admin/api/${API.VERSION}/reservation`;
  const TICKET_URL = `${base()}/admin/api/${API.VERSION}/ticket`;
  const SEAT_URL = `${base()}/admin/api/${API.VERSION}/seat`;
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

  function money(value) {
    return `${Number(value || 0).toLocaleString()}원`;
  }

  function valueOrDash(value) {
    return value == null || value === '' ? '-' : value;
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
    document.getElementById('reservation-detail-loading').style.display = 'flex';
    document.getElementById('reservation-detail-body').style.display = 'none';
    document.getElementById('reservation-detail-modal').style.display = 'flex';

    try {
      const res = await Fetch(`${RESERVATION_URL}/select/detail/${reservationId}`, { method: 'GET' });
      if (!res.ok) {
        showToast('예매 상세 조회에 실패했습니다.', true);
        closeReservationDetailModal();
        return;
      }

      renderReservationDetail(await res.json());
    } catch (e) {
      console.error('Reservation detail load failed', e);
      showToast('예매 상세 통신 오류가 발생했습니다.', true);
      closeReservationDetailModal();
    }
  };

  window.closeReservationDetailModal = function () {
    document.getElementById('reservation-detail-modal').style.display = 'none';
  };

  function renderReservationDetail(detail) {
    const reservation = detail?.reservation || {};
    const tickets = Array.isArray(detail?.tickets) ? detail.tickets : [];
    const discounts = Array.isArray(detail?.discounts) ? detail.discounts : [];
    const delivery = detail?.delivery;
    const payment = detail?.payment;

    document.getElementById('detail-reservation-id').textContent = reservation.reservationId ?? '-';
    document.getElementById('detail-order-id').textContent = reservation.orderId || '-';
    document.getElementById('detail-user-id').textContent = reservation.userId || '-';
    document.getElementById('detail-status').innerHTML = statusBadge(reservation.status);
    document.getElementById('detail-event-id').textContent = reservation.eventId || '-';
    document.getElementById('detail-event-title').textContent = reservation.eventTitle || '-';
    document.getElementById('detail-venue').textContent = reservation.venue || '-';
    document.getElementById('detail-event-date-time').textContent = reservation.eventDateTime || '-';
    document.getElementById('detail-reserved-date').textContent = reservation.reservedDate || '-';
    document.getElementById('detail-ticket-count').textContent = `${Number(reservation.ticketCount || tickets.length || 0).toLocaleString()}매`;

    renderDetailTickets(tickets);
    renderDetailDiscounts(discounts);
    renderDetailDelivery(delivery);
    renderDetailPayment(payment, detail);

    document.getElementById('reservation-detail-loading').style.display = 'none';
    document.getElementById('reservation-detail-body').style.display = 'block';
  }

  function renderDetailTickets(tickets) {
    document.getElementById('detail-ticket-summary').textContent =
      `${tickets.length.toLocaleString()}매 / ${money(tickets.reduce((sum, ticket) => sum + Number(ticket.price || 0), 0))}`;

    const tbody = document.getElementById('detail-ticket-body');
    if (tickets.length === 0) {
      tbody.innerHTML = `<tr><td colspan="7" class="detail-empty-cell">연결된 티켓이 없습니다.</td></tr>`;
      return;
    }

    tbody.innerHTML = tickets.map(ticket => `
      <tr>
        <td>#${escapeHtml(ticket.ticketId)}</td>
        <td title="${escapeHtml(ticket.seatName)}">${escapeHtml(ticket.seatName || formatSeatRowCol(ticket))}</td>
        <td>${escapeHtml(ticket.zone || '-')}</td>
        <td>${escapeHtml(ticket.grade || '-')}</td>
        <td class="number-cell">${money(ticket.price)}</td>
        <td>${ticketStatusBadge(ticket.status)}</td>
        <td>
          <button class="btn btn-sm btn-outline" onclick="openTicketSeatLocation(${ticket.seatId})">
            <i class="ti ti-map-pin"></i>위치
          </button>
        </td>
      </tr>
    `).join('');
  }

  function renderDetailDiscounts(discounts) {
    const totalDiscount = discounts.reduce((sum, discount) => sum + Number(discount.discountAmount || 0), 0);
    document.getElementById('detail-discount-summary').textContent = `${discounts.length.toLocaleString()}건 / ${money(totalDiscount)}`;

    const tbody = document.getElementById('detail-discount-body');
    if (discounts.length === 0) {
      tbody.innerHTML = `<tr><td colspan="5" class="detail-empty-cell">적용된 할인 내역이 없습니다.</td></tr>`;
      return;
    }

    tbody.innerHTML = discounts.map(discount => `
      <tr>
        <td title="${escapeHtml(discount.discountName)}">${escapeHtml(discount.discountName || '-')}</td>
        <td>${escapeHtml(formatDiscountType(discount))}</td>
        <td>${escapeHtml(formatDiscountValue(discount))}</td>
        <td class="number-cell">${money(discount.discountAmount)}</td>
        <td>${escapeHtml(discount.createdAt || '-')}</td>
      </tr>
    `).join('');
  }

  function renderDetailDelivery(delivery) {
    const grid = document.getElementById('detail-delivery-grid');
    if (!delivery) {
      grid.innerHTML = `<div class="detail-empty-state detail-grid-empty"><i class="ti ti-truck-off"></i><span>배송 정보가 없습니다.</span></div>`;
      return;
    }

    grid.innerHTML = [
      ['수령인', delivery.recipientName],
      ['연락처', delivery.recipientPhone],
      ['상태', delivery.status],
      ['우편번호', delivery.zipCode],
      ['주소', joinAddress(delivery)],
      ['요청사항', delivery.deliveryMessage],
      ['택배사', delivery.carrier],
      ['운송장 번호', delivery.trackingNumber],
      ['발송일', delivery.shippedAt],
      ['배송 완료일', delivery.deliveredAt]
    ].map(([label, value]) => detailItem(label, value)).join('');
  }

  function renderDetailPayment(payment, detail) {
    document.getElementById('detail-payment-summary').textContent =
      `티켓 ${money(detail?.totalTicketAmount)} / 할인 ${money(detail?.totalDiscountAmount)} / 결제 ${money(detail?.paymentAmount)}`;

    const grid = document.getElementById('detail-payment-grid');
    if (!payment) {
      grid.innerHTML = `<div class="detail-empty-state detail-grid-empty"><i class="ti ti-credit-card-off"></i><span>결제 정보가 없습니다.</span></div>`;
      return;
    }

    grid.innerHTML = [
      ['결제 ID', payment.paymentId],
      ['결제 번호', payment.paymentNo],
      ['수단', payment.method],
      ['상태', payment.status],
      ['결제 금액', money(payment.amount)],
      ['입금자명', payment.depositorName],
      ['은행', payment.bankName],
      ['계좌번호', payment.accountNumber],
      ['요청일', payment.requestedAt],
      ['완료일', payment.paidAt],
      ['만료일', payment.expiresAt]
    ].map(([label, value]) => detailItem(label, value)).join('');
  }

  function detailItem(label, value) {
    return `
      <div class="detail-item">
        <span>${escapeHtml(label)}</span>
        <strong title="${escapeHtml(valueOrDash(value))}">${escapeHtml(valueOrDash(value))}</strong>
      </div>
    `;
  }

  function formatDiscountType(discount) {
    if (discount.discountType === 'COUPON') return `쿠폰 ${discount.couponDiscountType || ''}`.trim();
    return discount.discountType || '-';
  }

  function formatDiscountValue(discount) {
    if (discount.couponDiscountType === 'PERCENT') return `${Number(discount.discountValue || 0).toLocaleString()}%`;
    if (discount.discountValue != null) return money(discount.discountValue);
    return '-';
  }

  function joinAddress(delivery) {
    return [delivery.address, delivery.detailAddress].filter(Boolean).join(' ');
  }

  window.openReservationTicketModal = async function (reservationId) {
    const reservation = currentReservationList.find(item => Number(item.reservationId) === Number(reservationId));
    document.getElementById('ticket-modal-reservation-id').textContent = reservationId ?? '-';
    document.getElementById('ticket-modal-order-id').textContent = reservation?.orderId || '-';
    document.getElementById('ticket-card-list').innerHTML = `
      <div class="ticket-empty-state">
        <i class="ti ti-loader-2"></i>
        <span>티켓 정보를 불러오는 중입니다.</span>
      </div>
    `;
    document.getElementById('reservation-ticket-modal').style.display = 'flex';

    try {
      const res = await Fetch(`${TICKET_URL}/reservation/${reservationId}`, { method: 'GET' });
      if (!res.ok) {
        showTicketEmptyState('티켓 목록 조회에 실패했습니다.');
        return;
      }

      const tickets = await res.json();
      renderTicketCards(Array.isArray(tickets) ? tickets : []);
    } catch (e) {
      console.error('Ticket list load failed', e);
      showTicketEmptyState('티켓 목록 통신 오류가 발생했습니다.');
    }
  };

  window.closeReservationTicketModal = function () {
    document.getElementById('reservation-ticket-modal').style.display = 'none';
  };

  function showTicketEmptyState(message) {
    document.getElementById('ticket-card-list').innerHTML = `
      <div class="ticket-empty-state">
        <i class="ti ti-ticket-off"></i>
        <span>${escapeHtml(message)}</span>
      </div>
    `;
  }

  function renderTicketCards(tickets) {
    if (tickets.length === 0) {
      showTicketEmptyState('연결된 티켓이 없습니다.');
      return;
    }

    document.getElementById('ticket-card-list').innerHTML = tickets.map(ticket => `
      <article class="ticket-card">
        <div class="ticket-card-head">
          <div>
            <span class="ticket-card-kicker">Ticket ID</span>
            <strong>#${escapeHtml(ticket.ticketId)}</strong>
          </div>
          ${ticketStatusBadge(ticket.status)}
        </div>
        <div class="ticket-card-seat">${escapeHtml(ticket.seatName || '-')}</div>
        <div class="ticket-card-grid">
          <div><span>좌석 ID</span><strong>${escapeHtml(ticket.seatId || '-')}</strong></div>
          <div><span>구역</span><strong>${escapeHtml(ticket.zone || '-')}</strong></div>
          <div><span>행/번호</span><strong>${formatSeatRowCol(ticket)}</strong></div>
          <div><span>등급</span><strong>${escapeHtml(ticket.grade || '-')}</strong></div>
          <div><span>가격</span><strong>${Number(ticket.price || 0).toLocaleString()}원</strong></div>
        </div>
        <div class="ticket-card-actions">
          <button class="btn btn-sm btn-outline" onclick="openTicketSeatLocation(${ticket.seatId})">
            <i class="ti ti-map-pin"></i>좌석 위치
          </button>
        </div>
      </article>
    `).join('');
  }

  function formatSeatRowCol(ticket) {
    const row = ticket.seatRow ?? '-';
    const col = ticket.seatCol ?? '-';
    return `${escapeHtml(row)}열 ${escapeHtml(col)}번`;
  }

  function ticketStatusBadge(status) {
    const labels = {
      PENDING_PAYMENT: '결제 대기',
      PAID: '결제 완료',
      CANCELLED: '취소',
      EXPIRED: '만료'
    };
    const classMap = {
      PENDING_PAYMENT: 'badge-pending',
      PAID: 'badge-paid',
      CANCELLED: 'badge-cancelled',
      EXPIRED: 'badge-expired'
    };
    return `<span class="badge ${classMap[status] || 'badge-expired'}">${escapeHtml(labels[status] || status || '-')}</span>`;
  }

  window.openTicketSeatLocation = async function (seatId) {
    if (!seatId) {
      showToast('좌석 ID가 없어 위치를 확인할 수 없습니다.', true);
      return;
    }

    document.getElementById('ticket-seat-location-title').textContent = '좌석 위치 확인';
    document.getElementById('ticket-seat-location-subtitle').textContent = `좌석 ID ${seatId}의 구역 내 위치입니다.`;
    document.getElementById('ticket-seat-location-count').textContent = '-';
    document.getElementById('ticket-seat-location-svg').innerHTML = '';
    document.getElementById('ticket-seat-location-modal').style.display = 'flex';

    try {
      const targetSeatRes = await Fetch(`${SEAT_URL}/select/id/${seatId}`, { method: 'GET' });
      if (!targetSeatRes.ok) {
        renderTicketSeatLocationEmpty('좌석 상세 정보를 조회하지 못했습니다.');
        return;
      }

      const targetSeat = await targetSeatRes.json();
      if (!targetSeat.areaId) {
        renderTicketSeatLocationEmpty('좌석의 구역 정보가 없어 위치를 표시할 수 없습니다.');
        return;
      }

      const seatsRes = await Fetch(`${SEAT_URL}/select`, {
        method: 'POST',
        headers,
        body: JSON.stringify({ areaId: targetSeat.areaId, page: 0, size: 5000, sort: ['seatRow-asc', 'seatCol-asc'] })
      });

      if (!seatsRes.ok) {
        renderTicketSeatLocationEmpty('구역 좌석 정보를 조회하지 못했습니다.');
        return;
      }

      const paged = await seatsRes.json();
      const seats = paged.content || [];
      renderTicketSeatLocation(targetSeat, seats);
    } catch (e) {
      console.error('Ticket seat location load failed', e);
      renderTicketSeatLocationEmpty('좌석 위치 조회 중 오류가 발생했습니다.');
    }
  };

  window.closeTicketSeatLocationModal = function () {
    document.getElementById('ticket-seat-location-modal').style.display = 'none';
  };

  function svgEl(tag, attrs = {}) {
    const el = document.createElementNS('http://www.w3.org/2000/svg', tag);
    Object.entries(attrs).forEach(([key, value]) => el.setAttribute(key, value));
    return el;
  }

  function renderTicketSeatLocationEmpty(message) {
    const svg = document.getElementById('ticket-seat-location-svg');
    svg.innerHTML = '';
    const text = svgEl('text', { x: 350, y: 260, class: 'ticket-seat-location-empty', 'text-anchor': 'middle' });
    text.textContent = message;
    svg.appendChild(text);
    document.getElementById('ticket-seat-location-count').textContent = '0석';
  }

  function renderTicketSeatLocation(targetSeat, seats) {
    const svg = document.getElementById('ticket-seat-location-svg');
    svg.innerHTML = '';
    const areaLabel = formatAreaLabel(targetSeat.areaName || targetSeat.zone);
    const rowColLabel = `${targetSeat.seatRow || '-'}열 ${targetSeat.seatCol || '-'}번`;

    document.getElementById('ticket-seat-location-title').textContent = areaLabel || '좌석 위치 확인';
    document.getElementById('ticket-seat-location-subtitle').textContent =
      `${targetSeat.title || '공연'} · ${areaLabel ? `${areaLabel} ` : ''}${rowColLabel}`;
    document.getElementById('ticket-seat-location-count').textContent = `${seats.length}석`;

    if (seats.length === 0) {
      renderTicketSeatLocationEmpty('해당 구역에 등록된 좌석 정보가 없습니다.');
      return;
    }

    seats.forEach(seat => {
      const x = seat.positionX ?? ((seat.seatCol || 1) - 1) * 18 + 80;
      const y = seat.positionY ?? ((seat.seatRow || 1) - 1) * 18 + 80;
      const width = seat.seatWidth ?? 14;
      const height = seat.seatHeight ?? 14;
      const rotation = seat.rotation ?? 0;
      const cx = x + width / 2;
      const cy = y + height / 2;
      const isTarget = Number(seat.seatId) === Number(targetSeat.seatId);
      const rect = svgEl('rect', {
        x, y, width, height, rx: 2,
        class: `ticket-seat-location-seat ${seatStatusClass(seat.status)}${isTarget ? ' is-target' : ''}`,
        transform: `rotate(${rotation} ${cx} ${cy})`
      });
      const title = svgEl('title');
      title.textContent = `${seat.seatName || `${seat.seatRow || '-'}열 ${seat.seatCol || '-'}번`} / ${seat.status || '-'} / ${seat.price != null ? Number(seat.price).toLocaleString() + '원' : '-'}`;
      rect.appendChild(title);
      svg.appendChild(rect);
    });
  }

  function seatStatusClass(status) {
    if (status === 'RESERVED') return 'is-reserved';
    if (status === 'LOCKED') return 'is-locked';
    return 'is-available';
  }

  function formatAreaLabel(areaName) {
    const value = String(areaName || '').trim();
    if (!value) return '';
    return value.endsWith('구역') ? value : `${value}구역`;
  }

  window.Pagination.register({
    load: window.loadReservationList,
    getTotalPages: () => serverTotalPages
  });

  loadReservationList(0);
})();
