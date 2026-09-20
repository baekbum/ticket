(function () {
  const COUPON_URL = `${base()}/admin/api/${API.VERSION}/coupon`;
  const headers = { 'Content-Type': 'application/json' };

  let currentCouponList = [];
  let currentSearchFilters = {
    name: null,
    code: null,
    discountType: null,
    status: null,
    validFrom: null,
    validUntil: null,
    validDaysAfterIssue: null
  };
  let currentSortFilters = { couponId: 'desc' };
  let serverTotalPages = 1;

  function inputValue(id) {
    return document.getElementById(id)?.value?.trim() || '';
  }

  function nullableNumber(id) {
    const value = inputValue(id).replace(/,/g, '');
    if (!value) return null;
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
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

  function toDateTimeLocal(value) {
    if (!value) return '';
    return String(value).replace(' ', 'T').slice(0, 16);
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
    if (currentSearchFilters.name) cond.name = currentSearchFilters.name;
    if (currentSearchFilters.code) cond.code = currentSearchFilters.code;
    if (currentSearchFilters.discountType) cond.discountType = currentSearchFilters.discountType;
    if (currentSearchFilters.status) cond.status = currentSearchFilters.status;
    if (currentSearchFilters.validFrom) cond.validFrom = currentSearchFilters.validFrom;
    if (currentSearchFilters.validUntil) cond.validUntil = currentSearchFilters.validUntil;
    if (currentSearchFilters.validDaysAfterIssue) cond.validDaysAfterIssue = currentSearchFilters.validDaysAfterIssue;
    return cond;
  }

  function statusBadge(status) {
    const label = {
      ACTIVE: '활성',
      INACTIVE: '비활성',
      EXPIRED: '만료'
    }[status] || status || '-';
    const cls = {
      ACTIVE: 'badge-active',
      INACTIVE: 'badge-inactive',
      EXPIRED: 'badge-expired'
    }[status] || 'badge-inactive';
    return `<span class="badge ${cls}">${escapeHtml(label)}</span>`;
  }

  function discountTypeBadge(type) {
    if (type === 'PERCENT') return '<span class="badge badge-percent">정률</span>';
    if (type === 'FIXED_AMOUNT') return '<span class="badge badge-fixed">정액</span>';
    return `<span class="badge badge-inactive">${escapeHtml(type || '-')}</span>`;
  }

  function formatDiscount(coupon) {
    if (coupon.discountType === 'PERCENT') return `${Number(coupon.discountValue || 0).toLocaleString()}%`;
    return `${Number(coupon.discountValue || 0).toLocaleString()}원`;
  }

  function formatMoney(value) {
    return value == null ? '-' : `${Number(value).toLocaleString()}원`;
  }

  function formatNumberInputValue(value) {
    if (value == null || value === '') return '';
    const numeric = String(value).replace(/[^\d]/g, '');
    return numeric ? Number(numeric).toLocaleString() : '';
  }

  function bindMoneyInputFormatters() {
    document.querySelectorAll('#coupon-modal .money-input').forEach(input => {
      input.addEventListener('input', function () {
        this.value = formatNumberInputValue(this.value);
      });
    });
  }

  function formatValidDaysAfterIssue(value) {
    return value ? `${Number(value).toLocaleString()}일` : '-';
  }

  window.loadCouponList = async function (pageZeroIndexed = 0) {
    try {
      const res = await Fetch(`${COUPON_URL}/select`, {
        method: 'POST',
        headers,
        body: JSON.stringify(buildCond(pageZeroIndexed))
      });

      if (!res.ok) {
        showToast('쿠폰 목록 조회에 실패했습니다.', true);
        return;
      }

      const paged = await res.json();
      currentCouponList = paged.content || [];
      serverTotalPages = Math.max(paged.page?.totalPages || paged.totalPages || 1, 1);
      const totalCount = paged.totalElements ?? paged.page?.totalElements ?? currentCouponList.length;
      const pageSize = parseInt(document.getElementById('pagination-size').value, 10);

      document.getElementById('pagination-total').textContent = serverTotalPages;
      document.getElementById('pagination-current').value = pageZeroIndexed + 1;
      document.getElementById('pagination-total-count').textContent = totalCount;

      const tbody = document.getElementById('coupon-table-body');
      tbody.innerHTML = '';

      if (currentCouponList.length === 0) {
        tbody.innerHTML = `<tr><td colspan="13" style="text-align:center;color:var(--text-muted);padding:2rem;">조회된 쿠폰이 없습니다.</td></tr>`;
        syncCouponSortHeaderUI();
        return;
      }

      currentCouponList.forEach((coupon, index) => {
        const rowNumber = pageZeroIndexed * pageSize + index + 1;
        const tr = document.createElement('tr');
        tr.innerHTML = `
          <td style="text-align:center;color:var(--text-muted);">${rowNumber}</td>
          <td><strong>${escapeHtml(coupon.couponId)}</strong></td>
          <td title="${escapeHtml(coupon.name)}">${escapeHtml(coupon.name || '-')}</td>
          <td title="${escapeHtml(coupon.code)}">${escapeHtml(coupon.code || '-')}</td>
          <td>${discountTypeBadge(coupon.discountType)}</td>
          <td style="text-align:right;">${formatDiscount(coupon)}</td>
          <td style="text-align:right;">${formatMoney(coupon.maxDiscountAmount)}</td>
          <td style="text-align:right;">${formatMoney(coupon.minOrderAmount)}</td>
          <td>${escapeHtml(coupon.validFrom || '-')}</td>
          <td>${escapeHtml(coupon.validUntil || '-')}</td>
          <td>${formatValidDaysAfterIssue(coupon.validDaysAfterIssue)}</td>
          <td>${statusBadge(coupon.status)}</td>
          <td class="actions" onclick="event.stopPropagation()">
            <button class="btn btn-sm btn-outline" onclick="event.stopPropagation(); openCouponModalForView(${coupon.couponId})">상세</button>
            <button class="btn btn-sm btn-outline" onclick="event.stopPropagation(); openCouponModalForUpdate(${coupon.couponId})">수정</button>
          </td>
        `;
        tr.onclick = () => openCouponModalForView(coupon.couponId);
        tbody.appendChild(tr);
      });

      syncCouponSortHeaderUI();
    } catch (e) {
      console.error('Coupon list load failed', e);
      showToast('쿠폰 목록 통신 오류가 발생했습니다.', true);
    }
  };

  window.triggerCouponSearch = function () {
    currentSearchFilters = {
      ...currentSearchFilters,
      name: inputValue('coupon-search-name') || null
    };
    setValue('cond-coupon-name', currentSearchFilters.name);
    loadCouponList(0);
  };

  window.resetCouponSearch = function () {
    currentSearchFilters = {
      name: null,
      code: null,
      discountType: null,
      status: null,
      validFrom: null,
      validUntil: null,
      validDaysAfterIssue: null
    };
    setValue('coupon-search-name', '');
    setValue('cond-coupon-name', '');
    setValue('cond-coupon-code', '');
    setValue('cond-coupon-discount-type', '');
    setValue('cond-coupon-status', '');
    setValue('cond-coupon-valid-from', '');
    setValue('cond-coupon-valid-until', '');
    setValue('cond-coupon-valid-days-after-issue', '');
    loadCouponList(0);
  };

  window.openCouponSearchModal = function () {
    setValue('cond-coupon-name', currentSearchFilters.name);
    setValue('cond-coupon-code', currentSearchFilters.code);
    setValue('cond-coupon-discount-type', currentSearchFilters.discountType);
    setValue('cond-coupon-status', currentSearchFilters.status);
    setValue('cond-coupon-valid-from', currentSearchFilters.validFrom);
    setValue('cond-coupon-valid-until', currentSearchFilters.validUntil);
    setValue('cond-coupon-valid-days-after-issue', currentSearchFilters.validDaysAfterIssue);
    document.getElementById('coupon-search-modal').style.display = 'flex';
  };

  window.closeCouponSearchModal = function () {
    document.getElementById('coupon-search-modal').style.display = 'none';
  };

  window.submitCouponDetailedSearch = function () {
    const validFrom = inputValue('cond-coupon-valid-from') || null;
    const validUntil = inputValue('cond-coupon-valid-until') || null;
    const validDaysAfterIssue = nullableNumber('cond-coupon-valid-days-after-issue');

    if (validFrom && validUntil && validFrom > validUntil) {
      showToast('유효 시작일은 종료일보다 늦을 수 없습니다.', true);
      return;
    }

    if (validDaysAfterIssue != null && (!Number.isInteger(validDaysAfterIssue) || validDaysAfterIssue <= 0)) {
      showToast('발급 후 만료 일수는 1 이상의 정수로 입력해주세요.', true);
      return;
    }

    currentSearchFilters = {
      name: inputValue('cond-coupon-name') || null,
      code: inputValue('cond-coupon-code') || null,
      discountType: inputValue('cond-coupon-discount-type') || null,
      status: inputValue('cond-coupon-status') || null,
      validFrom,
      validUntil,
      validDaysAfterIssue
    };
    setValue('coupon-search-name', currentSearchFilters.name);
    closeCouponSearchModal();
    loadCouponList(0);
  };

  window.handleCouponSortClick = function (headerEl, event) {
    const field = headerEl.getAttribute('data-sort-field');
    const current = currentSortFilters[field];
    const next = !current ? 'asc' : current === 'asc' ? 'desc' : null;

    if (!event.shiftKey) currentSortFilters = {};
    if (next) currentSortFilters[field] = next;
    else delete currentSortFilters[field];

    loadCouponList(0);
  };

  function syncCouponSortHeaderUI() {
    document.querySelectorAll('.coupon-table .sortable').forEach(th => {
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

  function clearCouponModal() {
    setValue('coupon-target-id', '');
    setValue('m-coupon-name', '');
    setValue('m-coupon-code', '');
    setValue('m-coupon-discount-type', 'FIXED_AMOUNT');
    setValue('m-coupon-discount-value', '');
    setValue('m-coupon-max-discount', '');
    setValue('m-coupon-min-order', '');
    setValue('m-coupon-valid-from', '');
    setValue('m-coupon-valid-until', '');
    setValue('m-coupon-validity-mode', 'FIXED');
    setValue('m-coupon-valid-days-after-issue', '');
    setValue('m-coupon-status', 'ACTIVE');
    syncCouponDiscountHelp();
    syncCouponValidityMode();
  }

  function bindCouponToModal(coupon) {
    setValue('coupon-target-id', coupon.couponId);
    setValue('m-coupon-name', coupon.name);
    setValue('m-coupon-code', coupon.code);
    setValue('m-coupon-discount-type', coupon.discountType || 'FIXED_AMOUNT');
    setValue('m-coupon-discount-value', formatNumberInputValue(coupon.discountValue));
    setValue('m-coupon-max-discount', formatNumberInputValue(coupon.maxDiscountAmount));
    setValue('m-coupon-min-order', formatNumberInputValue(coupon.minOrderAmount));
    setValue('m-coupon-valid-from', toDateTimeLocal(coupon.validFrom));
    setValue('m-coupon-valid-until', toDateTimeLocal(coupon.validUntil));
    setValue('m-coupon-validity-mode', coupon.validDaysAfterIssue ? 'AFTER_ISSUE' : 'FIXED');
    setValue('m-coupon-valid-days-after-issue', coupon.validDaysAfterIssue);
    setValue('m-coupon-status', coupon.status || 'ACTIVE');
    syncCouponDiscountHelp();
    syncCouponValidityMode();
  }

  function setCouponModalReadonly(readonly) {
    document.querySelectorAll('#coupon-modal input, #coupon-modal select').forEach(el => {
      if (el.type === 'hidden') return;
      el.disabled = readonly;
    });
    document.getElementById('coupon-submit-btn').style.display = readonly ? 'none' : 'inline-flex';
    document.getElementById('coupon-modal-actions').style.gridTemplateColumns = readonly ? '1fr' : '1fr 1fr';
  }

  window.openCouponModalForCreate = function () {
    clearCouponModal();
    setCouponModalReadonly(false);
    syncCouponValidityMode();
    setValue('coupon-modal-mode', 'CREATE');
    document.getElementById('coupon-modal-title').textContent = '쿠폰 등록';
    document.getElementById('coupon-modal-subtitle').textContent = '새 쿠폰 정책을 등록합니다.';
    document.getElementById('coupon-submit-btn').innerHTML = '<i class="ti ti-device-floppy"></i>등록';
    document.getElementById('coupon-modal').style.display = 'flex';
  };

  window.openCouponModalForView = function (couponId) {
    const coupon = currentCouponList.find(item => Number(item.couponId) === Number(couponId));
    if (!coupon) {
      showToast('쿠폰 데이터를 찾을 수 없습니다.', true);
      return;
    }
    bindCouponToModal(coupon);
    setCouponModalReadonly(true);
    setValue('coupon-modal-mode', 'VIEW');
    document.getElementById('coupon-modal-title').textContent = '쿠폰 상세 조회';
    document.getElementById('coupon-modal-subtitle').textContent = '읽기 전용 모드입니다.';
    document.getElementById('coupon-modal').style.display = 'flex';
  };

  window.openCouponModalForUpdate = function (couponId) {
    const coupon = currentCouponList.find(item => Number(item.couponId) === Number(couponId));
    if (!coupon) {
      showToast('쿠폰 데이터를 찾을 수 없습니다.', true);
      return;
    }
    bindCouponToModal(coupon);
    setCouponModalReadonly(false);
    syncCouponValidityMode();
    setValue('coupon-modal-mode', 'UPDATE');
    document.getElementById('coupon-modal-title').textContent = '쿠폰 수정';
    document.getElementById('coupon-modal-subtitle').textContent = '쿠폰 정책 정보를 수정합니다.';
    document.getElementById('coupon-submit-btn').innerHTML = '<i class="ti ti-device-floppy"></i>저장';
    document.getElementById('coupon-modal').style.display = 'flex';
  };

  window.closeCouponModal = function () {
    document.getElementById('coupon-modal').style.display = 'none';
  };

  window.syncCouponDiscountHelp = function () {
    const type = inputValue('m-coupon-discount-type');
    const label = document.getElementById('m-coupon-discount-value-label');
    if (label) label.textContent = type === 'PERCENT' ? '할인율 (%)' : '할인 금액';
  };

  window.syncCouponValidityMode = function () {
    const mode = inputValue('m-coupon-validity-mode') || 'FIXED';
    const validUntilGroup = document.getElementById('m-coupon-valid-until-group');
    const validDaysGroup = document.getElementById('m-coupon-valid-days-after-issue-group');
    const validUntil = document.getElementById('m-coupon-valid-until');
    const validDays = document.getElementById('m-coupon-valid-days-after-issue');

    if (validUntilGroup) validUntilGroup.style.display = mode === 'AFTER_ISSUE' ? 'none' : 'block';
    if (validDaysGroup) validDaysGroup.style.display = mode === 'AFTER_ISSUE' ? 'block' : 'none';
    if (validUntil) {
      validUntil.disabled = mode === 'AFTER_ISSUE';
      if (mode === 'AFTER_ISSUE') validUntil.value = '';
    }
    if (validDays) {
      validDays.disabled = mode === 'FIXED';
      if (mode === 'FIXED') validDays.value = '';
    }
  };

  function readCouponPayload() {
    const validityMode = inputValue('m-coupon-validity-mode') || 'FIXED';
    return {
      name: inputValue('m-coupon-name'),
      code: inputValue('m-coupon-code'),
      discountType: inputValue('m-coupon-discount-type'),
      discountValue: nullableNumber('m-coupon-discount-value'),
      maxDiscountAmount: nullableNumber('m-coupon-max-discount'),
      minOrderAmount: nullableNumber('m-coupon-min-order'),
      validFrom: toApiDateTime(inputValue('m-coupon-valid-from')),
      validUntil: validityMode === 'AFTER_ISSUE' ? null : toApiDateTime(inputValue('m-coupon-valid-until')),
      validDaysAfterIssue: validityMode === 'AFTER_ISSUE' ? nullableNumber('m-coupon-valid-days-after-issue') : 0,
      status: inputValue('m-coupon-status') || 'ACTIVE'
    };
  }

  function validateCouponPayload(payload) {
    if (!payload.name) return '쿠폰명을 입력해주세요.';
    if (!payload.code) return '쿠폰 코드를 입력해주세요.';
    if (!payload.discountType) return '할인 타입을 선택해주세요.';
    if (!payload.discountValue || payload.discountValue <= 0) return '할인 값을 1 이상 입력해주세요.';
    if (payload.discountType === 'PERCENT' && payload.discountValue > 100) return '정률 할인은 100%를 초과할 수 없습니다.';
    if (payload.validDaysAfterIssue != null && payload.validDaysAfterIssue < 0) return '발급 후 유효 일수는 0 이상이어야 합니다.';
    if (payload.validDaysAfterIssue > 0 && !Number.isInteger(payload.validDaysAfterIssue)) return '발급 후 유효 일수는 정수로 입력해주세요.';
    if (payload.validFrom && payload.validUntil && payload.validFrom > payload.validUntil) return '유효 시작일은 종료일보다 늦을 수 없습니다.';
    return null;
  }

  window.submitCouponForm = async function () {
    const mode = inputValue('coupon-modal-mode');
    const payload = readCouponPayload();
    const validationMessage = validateCouponPayload(payload);
    if (validationMessage) {
      showToast(validationMessage, true);
      return;
    }

    try {
      const couponId = inputValue('coupon-target-id');
      const url = mode === 'UPDATE' ? `${COUPON_URL}/update/id/${couponId}` : `${COUPON_URL}/insert`;
      const method = mode === 'UPDATE' ? 'PUT' : 'POST';
      const res = await Fetch(url, { method, headers, body: JSON.stringify(payload) });

      if (!res.ok) {
        showToast(mode === 'UPDATE' ? '쿠폰 수정에 실패했습니다.' : '쿠폰 등록에 실패했습니다.', true);
        return;
      }

      showToast(mode === 'UPDATE' ? '쿠폰을 수정했습니다.' : '쿠폰을 등록했습니다.');
      closeCouponModal();
      loadCouponList(Math.max(parseInt(document.getElementById('pagination-current').value, 10) - 1, 0));
    } catch (e) {
      console.error('Coupon submit failed', e);
      showToast('쿠폰 저장 중 통신 오류가 발생했습니다.', true);
    }
  };

  window.Pagination.register({
    load: window.loadCouponList,
    getTotalPages: () => serverTotalPages
  });

  bindMoneyInputFormatters();
  loadCouponList(0);
})();
