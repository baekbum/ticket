(function () {
  const USER_COUPON_URL = `${base()}/admin/api/${API.VERSION}/coupon`;
  const headers = { 'Content-Type': 'application/json' };

  let currentUserCouponList = [];
  let currentSearchFilters = {
    userId: null,
    couponName: null,
    couponCode: null,
    discountType: null,
    status: null,
    issuedAt: null,
    expiresAt: null,
    usedAt: null
  };
  let currentSortFilters = { userCouponId: 'desc' };
  let serverTotalPages = 1;

  function inputValue(id) {
    return document.getElementById(id)?.value?.trim() || '';
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

  function toApiDateTime(value) {
    return value ? `${value}:00` : null;
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
    if (currentSearchFilters.couponName) cond.couponName = currentSearchFilters.couponName;
    if (currentSearchFilters.couponCode) cond.couponCode = currentSearchFilters.couponCode;
    if (currentSearchFilters.discountType) cond.discountType = currentSearchFilters.discountType;
    if (currentSearchFilters.status) cond.status = currentSearchFilters.status;
    if (currentSearchFilters.issuedAt) cond.issuedAt = currentSearchFilters.issuedAt;
    if (currentSearchFilters.expiresAt) cond.expiresAt = currentSearchFilters.expiresAt;
    if (currentSearchFilters.usedAt) cond.usedAt = currentSearchFilters.usedAt;

    return cond;
  }

  function discountTypeBadge(type) {
    if (type === 'PERCENT') return '<span class="badge badge-percent">정률</span>';
    if (type === 'FIXED_AMOUNT') return '<span class="badge badge-fixed">정액</span>';
    return `<span class="badge badge-cancelled">${escapeHtml(type || '-')}</span>`;
  }

  function statusBadge(status) {
    const label = {
      ISSUED: '발급',
      USED: '사용',
      EXPIRED: '만료',
      CANCELLED: '취소'
    }[status] || status || '-';
    const cls = {
      ISSUED: 'badge-issued',
      USED: 'badge-used',
      EXPIRED: 'badge-expired',
      CANCELLED: 'badge-cancelled'
    }[status] || 'badge-cancelled';
    return `<span class="badge ${cls}">${escapeHtml(label)}</span>`;
  }

  function formatDiscount(coupon) {
    if (!coupon) return '-';
    const value = Number(coupon.discountValue || 0).toLocaleString();
    return coupon.discountType === 'PERCENT' ? `${value}%` : `${value}원`;
  }

  function renderEmpty(message) {
    document.getElementById('user-coupon-table-body').innerHTML =
      `<tr><td colspan="12" class="empty-cell">${escapeHtml(message)}</td></tr>`;
  }

  function renderUserCouponRows(pageZeroIndexed, pageSize) {
    const tbody = document.getElementById('user-coupon-table-body');
    tbody.innerHTML = '';

    if (currentUserCouponList.length === 0) {
      renderEmpty('조회된 유저 쿠폰이 없습니다.');
      return;
    }

    currentUserCouponList.forEach((userCoupon, index) => {
      const coupon = userCoupon.coupon || {};
      const rowNumber = pageZeroIndexed * pageSize + index + 1;
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td style="text-align:center;color:var(--text-muted);">${rowNumber}</td>
        <td><strong>${escapeHtml(userCoupon.userCouponId)}</strong></td>
        <td title="${escapeHtml(userCoupon.userId)}">${escapeHtml(userCoupon.userId || '-')}</td>
        <td title="${escapeHtml(coupon.name)}">${escapeHtml(coupon.name || '-')}</td>
        <td title="${escapeHtml(coupon.code)}">${escapeHtml(coupon.code || '-')}</td>
        <td>${discountTypeBadge(coupon.discountType)}</td>
        <td style="text-align:right;">${formatDiscount(coupon)}</td>
        <td>${statusBadge(userCoupon.status)}</td>
        <td>${escapeHtml(userCoupon.issuedAt || '-')}</td>
        <td>${escapeHtml(userCoupon.usedAt || '-')}</td>
        <td>${escapeHtml(userCoupon.expiresAt || '-')}</td>
        <td class="actions" onclick="event.stopPropagation()">
          <button class="btn btn-sm btn-outline" onclick="event.stopPropagation(); openUserCouponDetailModal(${index})">상세</button>
        </td>
      `;
      tr.onclick = () => openUserCouponDetailModal(index);
      tbody.appendChild(tr);
    });
  }

  window.loadUserCouponList = async function (pageZeroIndexed = 0) {
    try {
      const res = await Fetch(`${USER_COUPON_URL}/user-coupon/select`, {
        method: 'POST',
        headers,
        body: JSON.stringify(buildCond(pageZeroIndexed))
      });

      if (!res.ok) {
        showToast('유저 쿠폰 목록 조회에 실패했습니다.', true);
        return;
      }

      const paged = await res.json();
      currentUserCouponList = paged.content || [];
      serverTotalPages = Math.max(paged.page?.totalPages || paged.totalPages || 1, 1);
      const totalCount = paged.totalElements ?? paged.page?.totalElements ?? currentUserCouponList.length;
      const pageSize = parseInt(document.getElementById('pagination-size').value, 10);

      document.getElementById('pagination-total').textContent = serverTotalPages;
      document.getElementById('pagination-current').value = pageZeroIndexed + 1;
      document.getElementById('pagination-total-count').textContent = totalCount;

      renderUserCouponRows(pageZeroIndexed, pageSize);
    } catch (e) {
      console.error('User coupon list load failed', e);
      showToast('유저 쿠폰 목록 조회 중 통신 오류가 발생했습니다.', true);
    }
  };

  window.triggerUserCouponSearch = function () {
    currentSearchFilters = {
      ...currentSearchFilters,
      userId: inputValue('user-coupon-search-user-id') || null
    };
    setValue('cond-user-coupon-user-id', currentSearchFilters.userId);
    loadUserCouponList(0);
  };

  window.resetUserCouponSearch = function () {
    currentSearchFilters = {
      userId: null,
      couponName: null,
      couponCode: null,
      discountType: null,
      status: null,
      issuedAt: null,
      expiresAt: null,
      usedAt: null
    };
    setValue('user-coupon-search-user-id', '');
    setValue('cond-user-coupon-user-id', '');
    setValue('cond-user-coupon-name', '');
    setValue('cond-user-coupon-code', '');
    setValue('cond-user-coupon-discount-type', '');
    setValue('cond-user-coupon-status', '');
    setValue('cond-user-coupon-issued-at', '');
    setValue('cond-user-coupon-expires-at', '');
    setValue('cond-user-coupon-used-at', '');
    loadUserCouponList(0);
  };

  window.handleUserCouponSearchKeydown = function (event) {
    if (event.key === 'Enter') {
      event.preventDefault();
      triggerUserCouponSearch();
    }
  };

  window.openUserCouponSearchModal = function () {
    setValue('cond-user-coupon-user-id', currentSearchFilters.userId);
    setValue('cond-user-coupon-name', currentSearchFilters.couponName);
    setValue('cond-user-coupon-code', currentSearchFilters.couponCode);
    setValue('cond-user-coupon-discount-type', currentSearchFilters.discountType);
    setValue('cond-user-coupon-status', currentSearchFilters.status);
    setValue('cond-user-coupon-issued-at', currentSearchFilters.issuedAt);
    setValue('cond-user-coupon-expires-at', currentSearchFilters.expiresAt);
    setValue('cond-user-coupon-used-at', currentSearchFilters.usedAt);
    document.getElementById('user-coupon-search-modal').style.display = 'flex';
  };

  window.closeUserCouponSearchModal = function () {
    document.getElementById('user-coupon-search-modal').style.display = 'none';
  };

  window.submitUserCouponDetailedSearch = function () {
    currentSearchFilters = {
      userId: inputValue('cond-user-coupon-user-id') || null,
      couponName: inputValue('cond-user-coupon-name') || null,
      couponCode: inputValue('cond-user-coupon-code') || null,
      discountType: inputValue('cond-user-coupon-discount-type') || null,
      status: inputValue('cond-user-coupon-status') || null,
      issuedAt: inputValue('cond-user-coupon-issued-at') || null,
      expiresAt: inputValue('cond-user-coupon-expires-at') || null,
      usedAt: inputValue('cond-user-coupon-used-at') || null
    };
    setValue('user-coupon-search-user-id', currentSearchFilters.userId);
    closeUserCouponSearchModal();
    loadUserCouponList(0);
  };

  window.openUserCouponIssueModal = function () {
    setValue('m-user-coupon-user-id', inputValue('user-coupon-search-user-id'));
    setValue('m-user-coupon-coupon-id', '');
    setValue('m-user-coupon-expires-at', '');
    document.getElementById('user-coupon-issue-modal').style.display = 'flex';
  };

  window.closeUserCouponIssueModal = function () {
    document.getElementById('user-coupon-issue-modal').style.display = 'none';
  };

  window.submitUserCouponIssue = async function () {
    const userId = inputValue('m-user-coupon-user-id');
    const couponId = Number(inputValue('m-user-coupon-coupon-id'));
    const expiresAt = toApiDateTime(inputValue('m-user-coupon-expires-at'));

    if (!userId) {
      showToast('유저 ID를 입력해주세요.', true);
      return;
    }

    if (!Number.isInteger(couponId) || couponId <= 0) {
      showToast('쿠폰 ID를 1 이상의 정수로 입력해주세요.', true);
      return;
    }

    try {
      const res = await Fetch(`${USER_COUPON_URL}/issue`, {
        method: 'POST',
        headers,
        body: JSON.stringify({ userId, couponId, expiresAt })
      });

      if (!res.ok) {
        showToast('쿠폰 발급에 실패했습니다.', true);
        return;
      }

      showToast('쿠폰이 발급되었습니다.');
      closeUserCouponIssueModal();
      setValue('user-coupon-search-user-id', userId);
      currentSearchFilters = { ...currentSearchFilters, userId };
      loadUserCouponList(0);
    } catch (e) {
      console.error('User coupon issue failed', e);
      showToast('쿠폰 발급 중 통신 오류가 발생했습니다.', true);
    }
  };

  window.openUserCouponDetailModal = function (index) {
    const userCoupon = currentUserCouponList[index];
    if (!userCoupon) {
      showToast('유저 쿠폰 데이터를 찾을 수 없습니다.', true);
      return;
    }

    const coupon = userCoupon.coupon || {};
    const rows = [
      ['유저 쿠폰 ID', userCoupon.userCouponId],
      ['유저 ID', userCoupon.userId],
      ['쿠폰 ID', coupon.couponId],
      ['쿠폰명', coupon.name],
      ['쿠폰 코드', coupon.code],
      ['할인 타입', coupon.discountType],
      ['할인 값', formatDiscount(coupon)],
      ['상태', userCoupon.status],
      ['발급일', userCoupon.issuedAt || '-'],
      ['사용일', userCoupon.usedAt || '-'],
      ['만료일', userCoupon.expiresAt || '-']
    ];

    document.getElementById('user-coupon-detail-grid').innerHTML = rows.map(([label, value]) => `
      <div class="detail-label">${escapeHtml(label)}</div>
      <div class="detail-value">${escapeHtml(value ?? '-')}</div>
    `).join('');
    document.getElementById('user-coupon-detail-modal').style.display = 'flex';
  };

  window.closeUserCouponDetailModal = function () {
    document.getElementById('user-coupon-detail-modal').style.display = 'none';
  };

  window.Pagination.register({
    load: window.loadUserCouponList,
    getTotalPages: () => serverTotalPages
  });

  loadUserCouponList(0);
})();
