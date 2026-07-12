/* ══════════════════════════════════════════════════════════
     좌석 관리 모달 JS (이벤트 종속 좌석 CRUD + 구역 일괄등록)
  ══════════════════════════════════════════════════════════ */
  (function () {
    const API_VER  = 'v1';
    function adminBase() {
      if (window.location.port === '8999' || window.location.port === '8080') {
        return 'http://localhost:8999/admin';
      }

      const adminIndex = window.location.pathname.indexOf('/admin');
      if (adminIndex >= 0) {
        return `${window.location.origin}/admin`;
      }

      return '';
    }

    const SEAT_BASE = adminBase();
    const SEAT_API  = `${SEAT_BASE}/api/${API_VER}/seat`;
    const authHeader = () => ({ 'Content-Type': 'application/json', 'Authorization': `Bearer ${localStorage.getItem('accessToken')}` });

    /* ── 상태 ── */
    let smEventId    = null;
    let smAreaId     = null;
    let smAreaName   = null;
    let smAreaGrade  = null;
    let smAreaPrice  = null;
    let smTotalPages = 1;
    let smSelectedIds = new Set();
    let smDetailFilters = { seatRow: null, seatCol: null, status: null };

    /* ── 좌석 관리 모달 열기 ── */
    window.openSeatModal = function (eventId, title, artist, areaId = null, areaName = null, areaGrade = null, areaPrice = null) {
      smEventId = parseInt(eventId, 10);
      smAreaId = areaId ? parseInt(areaId, 10) : null;
      smAreaName = areaName || null;
      smAreaGrade = areaGrade || null;
      smAreaPrice = areaPrice ?? null;
      smSelectedIds.clear();
      smDetailFilters = { seatRow: null, seatCol: null, status: null };
      document.getElementById('sm-event-title').textContent  = smAreaName ? `${smAreaName} — ${title}` : `${title} — ${artist}`;
      document.getElementById('sm-event-id-label').textContent = eventId;
      document.getElementById('sm-area-label').textContent = smAreaName || '-';
      document.getElementById('sm-area-grade-label').textContent = smAreaGrade || '-';
      document.getElementById('sm-area-price-label').textContent = smAreaPrice !== null && smAreaPrice !== undefined && smAreaPrice !== ''
        ? `${Number(smAreaPrice).toLocaleString()}원`
        : '-';
      document.getElementById('sm-filter-row').value = '';
      document.getElementById('sm-filter-col').value = '';
      document.getElementById('sm-filter-status').value = '';
      document.getElementById('sm-page-current').value  = 1;
      document.getElementById('sm-select-all').checked  = false;
      document.getElementById('sm-search-reset-btn').style.display = 'none';
      _smUpdateBulkBar();
      document.getElementById('seat-mgmt-modal').style.display = 'flex';
      loadSeatMgmtList(0);
    };
    window.closeSeatMgmtModal = function () {
      document.getElementById('seat-mgmt-modal').style.display = 'none';
    };

    /* ── 목록 로드 ── */
    window.loadSeatMgmtList = async function (pageZeroIndexed = 0) {
      const pageSize = parseInt(document.getElementById('sm-page-size').value, 10);
      const rowVal   = document.getElementById('sm-filter-row').value.trim();
      const colVal   = document.getElementById('sm-filter-col').value.trim();
      const status   = document.getElementById('sm-filter-status').value || null;
      smDetailFilters = {
        seatRow: rowVal ? parseInt(rowVal, 10) : null,
        seatCol: colVal ? parseInt(colVal, 10) : null,
        status
      };
      const hasFilter = Object.values(smDetailFilters).some(v => v !== null);
      document.getElementById('sm-search-reset-btn').style.display = hasFilter ? 'inline-flex' : 'none';

      const body = {
        eventId: smEventId,
        areaId: smAreaId,
        status,
        seatRow: smDetailFilters.seatRow || null,
        seatCol: smDetailFilters.seatCol || null,
        page: pageZeroIndexed,
        size: pageSize,
        sort: ['seatId-desc']
      };
      Object.keys(body).forEach(k => (body[k] === null || body[k] === undefined) && delete body[k]);

      smSelectedIds.clear();
      _smUpdateBulkBar();
      document.getElementById('sm-select-all').checked = false;

      try {
        const res = await Fetch(`${SEAT_API}/select`, { method: 'POST', headers: authHeader(), body: JSON.stringify(body) });
        if (!res.ok) { showToast('좌석 조회 실패', true); return; }

        const paged = await res.json();
        const list  = paged.content || [];
        smTotalPages = Math.max(paged.page?.totalPages || paged.totalPages || 1, 1);
        const totalCount = paged.page?.totalElements ?? paged.totalElements ?? 0;
        document.getElementById('sm-page-total').textContent   = smTotalPages;
        document.getElementById('sm-page-current').value        = pageZeroIndexed + 1;
        document.getElementById('sm-page-total-count').textContent = totalCount;

        const tbody = document.getElementById('sm-seat-tbody');
        tbody.innerHTML = '';

        if (list.length === 0) {
          tbody.innerHTML = `<tr><td colspan="10" style="text-align:center; color:var(--text-muted); padding:2rem; font-size:12.5px;">등록된 좌석이 없습니다. 구역 일괄 등록으로 좌석을 생성하세요.</td></tr>`;
          return;
        }

        list.forEach(seat => {
          const statusBadge = _smStatusBadge(seat.status);
          const gradeCls    = `s-grade s-grade-${(seat.grade||'').toLowerCase()}`;
          const tr = document.createElement('tr');
          tr.innerHTML = `
            <td style="text-align:center;" onclick="event.stopPropagation()">
              <input type="checkbox" class="sm-row-cb" data-id="${seat.seatId}"
                     style="width:14px;height:14px;accent-color:var(--purple);cursor:pointer;"
                     onclick="event.stopPropagation(); _smToggleCb(this, ${seat.seatId})">
            </td>
            <td style="text-align:center; color:var(--text-muted); font-size:11.5px;">${seat.seatId}</td>
            <td><strong style="color:var(--text-primary); font-size:12px;">${seat.zone}</strong></td>
            <td style="text-align:center; color:var(--text-secondary);">${seat.seatRow}행</td>
            <td style="text-align:center; color:var(--text-secondary);">${seat.seatCol}열</td>
            <td style="text-align:right; font-weight:600; color:var(--text-primary);">${Number(seat.price).toLocaleString()}원</td>
            <td style="text-align:center;"><span class="${gradeCls}">${seat.grade}</span></td>
            <td style="text-align:center;">${statusBadge}</td>
            <td style="text-align:center;">
              <button class="btn btn-sm btn-cache-test" title="현재 사용자로 Redis 테스트 선점" onclick="event.stopPropagation(); lockSeatCacheForCurrentUser(${seat.seatId})"><i class="ti ti-database-import"></i></button>
              <button class="btn btn-sm btn-cache-test danger" title="Redis 테스트 선점 취소" onclick="event.stopPropagation(); unlockSeatCacheForTest(${seat.seatId})"><i class="ti ti-database-minus"></i></button>
            </td>
            <td style="text-align:center;">
              <button class="btn btn-sm btn-outline" onclick="event.stopPropagation(); openSeatEditModal(${seat.seatId}, '${seat.zone}', '${seat.grade}', ${seat.seatRow}, ${seat.seatCol}, ${seat.price}, '${seat.status}')">수정</button>
              <button class="btn btn-sm btn-danger"  onclick="event.stopPropagation(); openSeatSingleDeleteConfirm(${seat.seatId})">삭제</button>
            </td>
          `;
          tbody.appendChild(tr);
        });
      } catch (e) { showToast('서버 통신 오류', true); }
    };

    function _smStatusBadge(s) {
      if (s === 'AVAILABLE') return `<span class="s-badge s-badge-available">예매가능</span>`;
      if (s === 'LOCKED')    return `<span class="s-badge s-badge-locked">결제진행</span>`;
      if (s === 'RESERVED')  return `<span class="s-badge s-badge-reserved">예매완료</span>`;
      return `<span style="color:var(--text-muted);">${s||'-'}</span>`;
    }

    /* ── 체크박스 ── */
    window.smToggleAll = function (masterCb) {
      document.querySelectorAll('.sm-row-cb').forEach(cb => {
        cb.checked = masterCb.checked;
        const id = parseInt(cb.dataset.id, 10);
        masterCb.checked ? smSelectedIds.add(id) : smSelectedIds.delete(id);
      });
      _smUpdateBulkBar();
    };
    window._smToggleCb = function (cb, id) {
      cb.checked ? smSelectedIds.add(id) : smSelectedIds.delete(id);
      const all = document.querySelectorAll('.sm-row-cb');
      const master = document.getElementById('sm-select-all');
      if (master) master.checked = all.length > 0 && [...all].every(c => c.checked);
      _smUpdateBulkBar();
    };
    window.clearSeatSelections = function () {
      smSelectedIds.clear();
      document.querySelectorAll('.sm-row-cb').forEach(cb => cb.checked = false);
      const master = document.getElementById('sm-select-all');
      if (master) master.checked = false;
      _smUpdateBulkBar();
    };
    function _smUpdateBulkBar() {
      const bar = document.getElementById('sm-bulk-bar');
      document.getElementById('sm-bulk-count').textContent = smSelectedIds.size;
      bar.style.display = smSelectedIds.size > 0 ? 'flex' : 'none';
    }

    /* ── 페이지네이션 ── */
    window.smNavigatePage = function (offset) {
      const inp = document.getElementById('sm-page-current');
      let t = Math.min(Math.max((parseInt(inp.value,10)||1) + offset, 1), smTotalPages);
      inp.value = t;
      loadSeatMgmtList(t - 1);
    };
    window.smJump = function () {
      const inp = document.getElementById('sm-page-current');
      let t = parseInt(inp.value, 10);
      if (isNaN(t)||t<1) t=1;
      else if (t>smTotalPages) t=smTotalPages;
      inp.value = t;
      loadSeatMgmtList(t - 1);
    };

    /* ── Redis 좌석 캐시 ── */
    let cacheTarget = null;

    function _cacheEventIdFromAreaPage() {
      if (typeof window.getCurrentAreaEventId === 'function') {
        return window.getCurrentAreaEventId();
      }
      return smEventId || null;
    }

    function _setWarmupModal(scope, id, label) {
      cacheTarget = { action: 'WARM_UP', scope, id };
      const titleEl = document.getElementById('cache-warmup-title');
      const subtitleEl = document.getElementById('cache-warmup-subtitle');
      if (titleEl) titleEl.textContent = `${label} 좌석 캐시 적재`;
      if (subtitleEl) subtitleEl.textContent = `${label} 좌석 데이터를 Redis에 적재합니다.`;
      const defaultMode = document.querySelector('input[name="cache-warmup-mode"][value="MISSING_ONLY"]');
      if (defaultMode) defaultMode.checked = true;
      document.getElementById('cache-warmup-modal').style.display = 'flex';
    }

    function _setDeleteModal(scope, id, label) {
      cacheTarget = { action: 'DELETE', scope, id };
      const titleEl = document.getElementById('cache-delete-title');
      const subtitleEl = document.getElementById('cache-delete-subtitle');
      const modalEl = document.getElementById('cache-delete-modal');
      if (titleEl) titleEl.textContent = `${label} 좌석 캐시 삭제`;
      if (subtitleEl) subtitleEl.textContent = `${label} 좌석 캐시와 임시 락 정보를 Redis에서 삭제합니다.`;
      if (modalEl) modalEl.style.display = 'flex';
    }

    window.openEventSeatCacheWarmupModal = function () {
      const eventId = _cacheEventIdFromAreaPage();
      if (!eventId) { showToast('이벤트 ID로 먼저 조회해주세요.', true); return; }
      _setWarmupModal('EVENT', eventId, `이벤트 ${eventId}번 전체`);
    };

    window.openEventSeatCacheDeleteModal = function () {
      const eventId = _cacheEventIdFromAreaPage();
      if (!eventId) { showToast('이벤트 ID로 먼저 조회해주세요.', true); return; }
      _setDeleteModal('EVENT', eventId, `이벤트 ${eventId}번 전체`);
    };

    window.openAreaSeatCacheWarmupModal = function () {
      if (!smAreaId) { showToast('구역 정보가 없습니다.', true); return; }
      _setWarmupModal('AREA', smAreaId, `구역 ${smAreaName || smAreaId}`);
    };

    window.openAreaSeatCacheDeleteModal = function () {
      if (!smAreaId) { showToast('구역 정보가 없습니다.', true); return; }
      _setDeleteModal('AREA', smAreaId, `구역 ${smAreaName || smAreaId}`);
    };

    window.openCacheWarmupFromSeatModal = function () {
      if (smAreaId) {
        window.openAreaSeatCacheWarmupModal();
        return;
      }
      if (!smEventId) { showToast('이벤트 정보가 없습니다.', true); return; }
      _setWarmupModal('EVENT', smEventId, `이벤트 ${smEventId}번 전체`);
    };
    window.openCacheWarmupConfirmModal = window.openCacheWarmupFromSeatModal;

    window.submitCacheWarmup = async function () {
      if (!cacheTarget || cacheTarget.action !== 'WARM_UP') return;

      const mode = document.querySelector('input[name="cache-warmup-mode"]:checked')?.value || 'MISSING_ONLY';
      const targetPath = cacheTarget.scope === 'EVENT'
        ? `event/${cacheTarget.id}`
        : `area/${cacheTarget.id}`;

      try {
        const res = await Fetch(`${SEAT_API}/cache/warm-up/${targetPath}?mode=${encodeURIComponent(mode)}`, {
          method: 'POST',
          headers: authHeader()
        });
        if (res.ok) {
          const message = await res.text();
          showToast(message || '좌석 캐시 적재가 완료되었습니다.');
          closeCacheWarmupConfirmModal();
        } else {
          showToast('좌석 캐시 적재에 실패했습니다.', true);
        }
      } catch {
        showToast('서버 통신 장애', true);
      }
    };

    window.submitCacheDelete = async function () {
      if (!cacheTarget || cacheTarget.action !== 'DELETE') return;

      const targetPath = cacheTarget.scope === 'EVENT'
        ? `event/${cacheTarget.id}`
        : `area/${cacheTarget.id}`;

      try {
        const res = await Fetch(`${SEAT_API}/cache/${targetPath}`, {
          method: 'DELETE',
          headers: authHeader()
        });
        if (res.ok) {
          const message = await res.text();
          showToast(message || '좌석 캐시가 삭제되었습니다.');
          closeCacheDeleteConfirmModal();
        } else {
          showToast('좌석 캐시 삭제에 실패했습니다.', true);
        }
      } catch {
        showToast('서버 통신 장애', true);
      }
    };

    window.closeCacheWarmupConfirmModal = function () {
      document.getElementById('cache-warmup-modal').style.display = 'none';
      cacheTarget = null;
    };

    window.closeCacheDeleteConfirmModal = function () {
      const modalEl = document.getElementById('cache-delete-modal');
      if (modalEl) modalEl.style.display = 'none';
      cacheTarget = null;
    };

    window.lockSeatCacheForCurrentUser = async function (seatId) {
      try {
        const res = await Fetch(`${SEAT_API}/cache/seat/${seatId}/test-lock`, {
          method: 'POST',
          headers: authHeader()
        });
        if (res.ok) {
          const message = await res.text();
          showToast(message || '현재 사용자로 좌석 Redis 테스트 선점이 완료되었습니다.');
        } else {
          showToast('좌석 Redis 테스트 선점에 실패했습니다.', true);
        }
      } catch {
        showToast('서버 통신 장애', true);
      }
    };

    window.unlockSeatCacheForTest = async function (seatId) {
      try {
        const res = await Fetch(`${SEAT_API}/cache/seat/${seatId}/test-unlock`, {
          method: 'POST',
          headers: authHeader()
        });
        if (res.ok) {
          const message = await res.text();
          showToast(message || '좌석 Redis 테스트 선점이 취소되었습니다.');
        } else {
          showToast('좌석 Redis 테스트 선점 취소에 실패했습니다.', true);
        }
      } catch {
        showToast('서버 통신 장애', true);
      }
    };

    /* ── 단건 수정 모달 ── */
    window.openSeatEditModal = function (seatId, zone, grade, row, col, price, status) {
      document.getElementById('se-seat-id').value = seatId;
      document.getElementById('se-zone').value    = zone;
      document.getElementById('se-grade').value   = grade;
      document.getElementById('se-row').value     = row;
      document.getElementById('se-col').value     = col;
      document.getElementById('se-price').value   = price;
      document.getElementById('se-status').value  = status;
      document.getElementById('seat-edit-modal').style.display = 'flex';
    };
    window.closeSeatEditModal = function () {
      document.getElementById('seat-edit-modal').style.display = 'none';
    };
    window.submitSeatEdit = async function () {
      const seatId = document.getElementById('se-seat-id').value;
      const price  = document.getElementById('se-price').value;
      const status = document.getElementById('se-status').value;
      if (!price) { showToast('가격을 입력해주세요.', true); return; }
      try {
        const res = await Fetch(`${SEAT_API}/update`, {
          method: 'PUT', headers: authHeader(),
          body: JSON.stringify({ updateSeatAreaConfigs: [{ id: parseInt(seatId,10), price: parseInt(price,10), status }] })
        });
        if (res.ok) {
          showToast('좌석 정보가 수정되었습니다.');
          closeSeatEditModal();
          loadSeatMgmtList(parseInt(document.getElementById('sm-page-current').value,10) - 1);
        } else { showToast('수정 처리 실패', true); }
      } catch { showToast('통신 오류', true); }
    };

    /* ── 단건 삭제 ── */
    window.openSeatSingleDeleteConfirm = function (seatId) {
      document.getElementById('ssd-target-id').value = seatId;
      document.getElementById('seat-single-delete-modal').style.display = 'flex';
    };
    window.closeSeatSingleDeleteConfirm = function () {
      document.getElementById('seat-single-delete-modal').style.display = 'none';
    };
    window.submitSeatSingleDelete = async function () {
      const id = document.getElementById('ssd-target-id').value;
      try {
        const res = await Fetch(`${SEAT_API}/delete/id/${id}`, { method: 'DELETE', headers: authHeader() });
        if (res.ok) {
          showToast('좌석이 삭제되었습니다.');
          closeSeatSingleDeleteConfirm();
          closeSeatEditModal();
          loadSeatMgmtList(Math.max(parseInt(document.getElementById('sm-page-current').value,10)-1, 0));
        } else { showToast('삭제 실패', true); }
      } catch { showToast('통신 오류', true); }
    };

    /* ── 선택 다중 삭제 ── */
    window.openSeatBulkDeleteConfirm = function () {
      if (smSelectedIds.size === 0) { showToast('삭제할 좌석을 선택해주세요.', true); return; }
      document.getElementById('sbd-count').textContent = smSelectedIds.size;
      document.getElementById('seat-bulk-delete-modal').style.display = 'flex';
    };
    window.closeSeatBulkDeleteConfirm = function () {
      document.getElementById('seat-bulk-delete-modal').style.display = 'none';
    };
    window.submitSeatBulkDelete = async function () {
      const ids = [...smSelectedIds];
      try {
        const res = await Fetch(`${SEAT_API}/delete/bulk`, {
          method: 'DELETE',
          headers: authHeader(),
          body: JSON.stringify({ seatIdList: ids })
        });

        if (res.ok) {
          closeSeatBulkDeleteConfirm();
          smSelectedIds.clear(); _smUpdateBulkBar();
          showToast(`${ids.length}석 삭제 완료.`);
          loadSeatMgmtList(Math.max(parseInt(document.getElementById('sm-page-current').value,10)-1, 0));
        } else {
          showToast('좌석 일괄 삭제 처리 중 오류가 발생했습니다.', true);
        }
      } catch {
        showToast('서버 통신 실패', true);
      }
    };

    /* ── 선택 일괄 수정 패널 ── */
    window.openSeatBulkEditPanel = function () {
      if (smSelectedIds.size === 0) { showToast('수정할 좌석을 선택해주세요.', true); return; }
      document.getElementById('sbe-subtitle').textContent = `선택한 ${smSelectedIds.size}석을 일괄 변경합니다.`;
      document.getElementById('sbe-price').value  = '';
      document.getElementById('sbe-status').value = '';
      document.getElementById('seat-bulk-edit-modal').style.display = 'flex';
    };
    window.closeSeatBulkEditPanel = function () {
      document.getElementById('seat-bulk-edit-modal').style.display = 'none';
    };
    window.submitSeatBulkEdit = async function () {
      const priceVal  = document.getElementById('sbe-price').value;
      const statusVal = document.getElementById('sbe-status').value;
      if (!priceVal && !statusVal) { showToast('변경할 항목을 하나 이상 입력해주세요.', true); return; }
      const configs = [...smSelectedIds].map(id => {
        const e = { id };
        if (priceVal)  e.price  = parseInt(priceVal, 10);
        if (statusVal) e.status = statusVal;
        return e;
      });
      try {
        const res = await Fetch(`${SEAT_API}/update`, {
          method: 'PUT', headers: authHeader(),
          body: JSON.stringify({ updateSeatAreaConfigs: configs })
        });
        if (res.ok) {
          showToast(`${smSelectedIds.size}석 일괄 수정 완료.`);
          closeSeatBulkEditPanel();
          smSelectedIds.clear(); _smUpdateBulkBar();
          loadSeatMgmtList(parseInt(document.getElementById('sm-page-current').value,10)-1);
        } else { showToast('일괄 수정 실패', true); }
      } catch { showToast('통신 오류', true); }
    };

    /* ── 구역 일괄 등록 ── */
    let sbcZoneCount = 0;
    let sbcBlocks = [];
    let sbcSyncing = false;

    function _formatDigitInput(input) {
      if (!input) return;
      const raw = input.value.replace(/[^\d]/g, '');
      input.value = raw ? Number(raw).toLocaleString() : '';
    }

    function _parseDigitValue(value) {
      return parseInt((value || '').replace(/,/g, ''), 10);
    }

    function _nullableFloat(value) {
      const trimmed = (value || '').trim();
      if (!trimmed) return null;
      const parsed = parseFloat(trimmed);
      return Number.isNaN(parsed) ? null : parsed;
    }

    function _escapeAttr(value) {
      return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/"/g, '&quot;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
    }

    function _sbcNumber(value, fallback = 0) {
      const parsed = Number(value);
      return Number.isFinite(parsed) ? parsed : fallback;
    }

    function _sbcSetValue(card, selector, value) {
      const input = card?.querySelector(selector);
      if (input) input.value = value;
    }

    function _sbcBlockById(id) {
      return sbcBlocks.find(block => block.id === id) || null;
    }

    function _sbcCardById(id) {
      return document.getElementById(id);
    }

    function _sbcRecalc() {
      _sbcSyncLinkedBlocks();
      let total = 0;
      document.querySelectorAll('#sbc-zone-wrapper .zone-card').forEach(card => {
        const r = parseInt(card.querySelector('.b-rows')?.value, 10) || 0;
        const c = parseInt(card.querySelector('.b-cols')?.value, 10) || 0;
        total  += r * c;
        card.querySelector('.zone-seat-count').textContent = (r * c).toLocaleString();
      });
      document.getElementById('sbc-total-count').textContent = total.toLocaleString();
      _sbcRenderPreview();
    }
    window._sbcRecalc = _sbcRecalc;

    function _sbcSvgEl(tag, attrs = {}) {
      const el = document.createElementNS('http://www.w3.org/2000/svg', tag);
      Object.entries(attrs).forEach(([key, value]) => el.setAttribute(key, value));
      return el;
    }

    function _sbcRenderPreviewGrid(svg) {
      for (let x = 0; x <= 700; x += 50) {
        svg.appendChild(_sbcSvgEl('line', { x1: x, y1: 0, x2: x, y2: 520, class: 'sbc-preview-grid-line' }));
      }
      for (let y = 0; y <= 520; y += 50) {
        svg.appendChild(_sbcSvgEl('line', { x1: 0, y1: y, x2: 700, y2: y, class: 'sbc-preview-grid-line' }));
      }
      svg.appendChild(_sbcSvgEl('rect', { x: 0, y: 0, width: 700, height: 520, class: 'sbc-preview-boundary' }));
    }

    function _sbcReadCardLayout(card) {
      return {
        rows: parseInt(card.querySelector('.b-rows')?.value, 10) || 0,
        cols: parseInt(card.querySelector('.b-cols')?.value, 10) || 0,
        startRow: parseInt(card.querySelector('.b-start-row')?.value, 10) || 1,
        startCol: parseInt(card.querySelector('.b-start-col')?.value, 10) || 1,
        startX: _nullableFloat(card.querySelector('.b-start-x')?.value) ?? 80,
        startY: _nullableFloat(card.querySelector('.b-start-y')?.value) ?? 80,
        seatWidth: _nullableFloat(card.querySelector('.b-seat-width')?.value) ?? 14,
        seatHeight: _nullableFloat(card.querySelector('.b-seat-height')?.value) ?? 14,
        gapX: _nullableFloat(card.querySelector('.b-gap-x')?.value) ?? 4,
        gapY: _nullableFloat(card.querySelector('.b-gap-y')?.value) ?? 4,
        rotation: _nullableFloat(card.querySelector('.b-rotation')?.value) ?? 0,
        layoutAngle: _nullableFloat(card.querySelector('.b-layout-angle')?.value) ?? 0,
        rowOffset: parseInt(card.querySelector('.b-row-offset')?.value, 10) || 0,
        colOffset: parseInt(card.querySelector('.b-col-offset')?.value, 10) || 0,
        xOffset: _nullableFloat(card.querySelector('.b-x-offset')?.value) ?? 0,
        yOffset: _nullableFloat(card.querySelector('.b-y-offset')?.value) ?? 0
      };
    }

    function _sbcCalculateLinkedPosition(parentLayout, childLayout, side) {
      if (side === 'RIGHT') {
        return {
          startRow: parentLayout.startRow + childLayout.rowOffset,
          startCol: parentLayout.startCol + parentLayout.cols + childLayout.colOffset,
          startX: parentLayout.startX + parentLayout.cols * (parentLayout.seatWidth + parentLayout.gapX) + childLayout.xOffset,
          startY: parentLayout.startY + childLayout.yOffset
        };
      }

      if (side === 'BOTTOM') {
        return {
          startRow: parentLayout.startRow + parentLayout.rows + childLayout.rowOffset,
          startCol: parentLayout.startCol + childLayout.colOffset,
          startX: parentLayout.startX + childLayout.xOffset,
          startY: parentLayout.startY + parentLayout.rows * (parentLayout.seatHeight + parentLayout.gapY) + childLayout.yOffset
        };
      }

      return null;
    }

    function _sbcSyncLinkedBlocks() {
      if (sbcSyncing) return;
      sbcSyncing = true;
      try {
        for (let i = 0; i < sbcBlocks.length; i++) {
          sbcBlocks.forEach(block => {
            if (!block.anchorId || block.anchorSide === 'DIRECT') return;
            const parent = _sbcBlockById(block.anchorId);
            const parentCard = _sbcCardById(block.anchorId);
            const childCard = _sbcCardById(block.id);
            if (!parent || !parentCard || !childCard) return;

            const parentLayout = _sbcReadCardLayout(parentCard);
            const childLayout = _sbcReadCardLayout(childCard);
            const next = _sbcCalculateLinkedPosition(parentLayout, childLayout, block.anchorSide);
            if (!next) return;

            _sbcSetValue(childCard, '.b-start-row', next.startRow);
            _sbcSetValue(childCard, '.b-start-col', next.startCol);
          });
        }
      } finally {
        sbcSyncing = false;
      }
    }

    function _sbcRenderPreview() {
      const svg = document.getElementById('sbc-preview-svg');
      const info = document.getElementById('sbc-preview-info');
      if (!svg) return;
      svg.innerHTML = '';
      _sbcRenderPreviewGrid(svg);

      const cards = document.querySelectorAll('#sbc-zone-wrapper .zone-card');
      if (cards.length === 0) {
        const empty = _sbcSvgEl('text', { x: 350, y: 260, class: 'sbc-preview-empty', 'text-anchor': 'middle' });
        empty.textContent = '좌석 묶음을 추가하면 미리보기가 표시됩니다.';
        svg.appendChild(empty);
        if (info) {
          info.textContent = '좌석 범위 X: - / Y: -';
          info.classList.remove('is-warning');
        }
        return;
      }

      const bounds = [];
      const logicalSeatKeys = new Set();
      let hasDuplicateLogicalSeat = false;
      cards.forEach((card, index) => {
        const layout = _sbcReadCardLayout(card);
        if (layout.rows <= 0 || layout.cols <= 0 || layout.seatWidth <= 0 || layout.seatHeight <= 0) return;

        const group = _sbcSvgEl('g', { class: 'sbc-preview-seat-group', 'data-index': index + 1 });
        const angle = layout.layoutAngle * Math.PI / 180;
        const colDx = (layout.seatWidth + layout.gapX) * Math.cos(angle);
        const colDy = (layout.seatWidth + layout.gapX) * Math.sin(angle);
        const rowDx = (layout.seatHeight + layout.gapY) * Math.cos(angle + Math.PI / 2);
        const rowDy = (layout.seatHeight + layout.gapY) * Math.sin(angle + Math.PI / 2);

        for (let row = 0; row < layout.rows; row++) {
          for (let col = 0; col < layout.cols; col++) {
            const x = layout.startX + col * colDx + row * rowDx;
            const y = layout.startY + col * colDy + row * rowDy;
            const cx = x + layout.seatWidth / 2;
            const cy = y + layout.seatHeight / 2;
            const outOfBounds = x < 0 || y < 0 || x + layout.seatWidth > 700 || y + layout.seatHeight > 520;
            const seatRow = layout.startRow + row;
            const seatCol = layout.startCol + col;
            const logicalKey = `${smEventId || ''}:${smAreaName || ''}:${seatRow}:${seatCol}`;
            const duplicated = logicalSeatKeys.has(logicalKey);
            if (duplicated) hasDuplicateLogicalSeat = true;
            logicalSeatKeys.add(logicalKey);
            const rect = _sbcSvgEl('rect', {
              x,
              y,
              width: layout.seatWidth,
              height: layout.seatHeight,
              rx: 2,
              class: `sbc-preview-seat${outOfBounds ? ' is-out-of-bounds' : ''}${duplicated ? ' is-duplicated' : ''}`,
              transform: `rotate(${layout.rotation} ${cx} ${cy})`
            });
            const title = _sbcSvgEl('title');
            title.textContent = `${seatRow}행 ${seatCol}열`;
            rect.appendChild(title);
            group.appendChild(rect);
            bounds.push({ minX: x, minY: y, maxX: x + layout.seatWidth, maxY: y + layout.seatHeight, outOfBounds });
          }
        }
        svg.appendChild(group);
      });

      if (!info) return;
      if (bounds.length === 0) {
        info.textContent = '좌석 범위 X: - / Y: -';
        info.classList.remove('is-warning');
        return;
      }

      const minX = Math.floor(Math.min(...bounds.map(b => b.minX)));
      const minY = Math.floor(Math.min(...bounds.map(b => b.minY)));
      const maxX = Math.ceil(Math.max(...bounds.map(b => b.maxX)));
      const maxY = Math.ceil(Math.max(...bounds.map(b => b.maxY)));
      const hasOutOfBounds = bounds.some(b => b.outOfBounds);
      const warnings = [];
      if (hasOutOfBounds) warnings.push('700x520 영역 밖 좌석이 있습니다.');
      if (hasDuplicateLogicalSeat) warnings.push('중복된 행/열 좌석이 있습니다.');
      info.textContent = `좌석 범위 X: ${minX}~${maxX} / Y: ${minY}~${maxY}${warnings.length ? ' - ' + warnings.join(' ') : ''}`;
      info.classList.toggle('is-warning', hasOutOfBounds || hasDuplicateLogicalSeat);
    }
    window._sbcRenderPreview = _sbcRenderPreview;

    function _sbcUpdateLabels() {
      document.querySelectorAll('#sbc-zone-wrapper .zone-card').forEach((card, i) => {
        const el = card.querySelector('.zone-card-title-text');
        const label = `좌석 묶음 #${i+1}`;
        if (el) el.textContent = label;
        const block = _sbcBlockById(card.id);
        if (block) block.label = label;
      });
    }
    window._sbcUpdateLabels = _sbcUpdateLabels;

    window.sbcAddZoneCard = function (data = {}, mode = 'DIRECT', anchorId = null) {
      const wrapper = document.getElementById('sbc-zone-wrapper');
      const zoneIndex = wrapper.querySelectorAll('.zone-card').length;
      const anchorBlock = anchorId ? _sbcBlockById(anchorId) : null;
      const anchorCard = anchorBlock ? _sbcCardById(anchorBlock.id) : null;
      const anchorLayout = anchorCard ? _sbcReadCardLayout(anchorCard) : null;
      const startX = data.startX ?? 80;
      const startY = data.startY ?? (80 + zoneIndex * 140);
      const startRow = data.startRow ?? 1;
      const startCol = data.startCol ?? (1 + zoneIndex * 10);
      const seatWidth = data.seatWidth ?? 14;
      const seatHeight = data.seatHeight ?? 14;
      const gapX = data.gapX ?? 4;
      const gapY = data.gapY ?? 4;
      const rotation = data.rotation ?? 0;
      const layoutAngle = data.layoutAngle ?? 0;
      const zone = data.zone ?? smAreaName ?? '';
      const grade = data.grade ?? smAreaGrade ?? 'VIP';
      const price = data.price ?? smAreaPrice ?? '';
      const rowOffset = data.rowOffset ?? 0;
      const colOffset = data.colOffset ?? 0;
      const xOffset = data.xOffset ?? 0;
      const yOffset = data.yOffset ?? 0;
      const cid = 'sbc-' + Date.now() + '-' + (sbcZoneCount++);
      const linked = mode === 'RIGHT' || mode === 'BOTTOM';
      let linkedStart = null;
      if (linked && anchorLayout) {
        linkedStart = _sbcCalculateLinkedPosition(
          anchorLayout,
          { rowOffset, colOffset, xOffset, yOffset },
          mode
        );
      }
      const div = document.createElement('div');
      div.id = cid; div.className = 'zone-card';
      div.innerHTML = `
        <div class="zone-card-header">
          <div class="zone-card-title"><i class="ti ti-layers-intersect"></i><span class="zone-card-title-text">좌석 묶음</span></div>
          <div class="seat-block-card-actions">
            <button type="button" class="btn btn-sm btn-outline" onclick="sbcAddSeatBlock('RIGHT', '${cid}')"><i class="ti ti-arrow-right"></i>오른쪽</button>
            <button type="button" class="btn btn-sm btn-outline" onclick="sbcAddSeatBlock('BOTTOM', '${cid}')"><i class="ti ti-arrow-down"></i>아래</button>
            <button type="button" class="btn btn-sm btn-danger" onclick="sbcRemoveSeatBlock('${cid}')"><i class="ti ti-x"></i>제거</button>
          </div>
        </div>
        ${linked ? `
          <div class="seat-block-link-info">
            <span>${mode === 'RIGHT' ? '오른쪽 연결' : '아래 연결'}</span>
            <strong>${anchorBlock ? anchorBlock.label : '기준 묶음'}</strong>
          </div>
          <div class="zone-link-grid">
            <div class="zone-field"><span>행 보정</span><input type="number" class="b-row-offset" value="${rowOffset}" style="text-align:right;"></div>
            <div class="zone-field"><span>열 보정</span><input type="number" class="b-col-offset" value="${colOffset}" style="text-align:right;"></div>
          </div>
          <input type="hidden" class="b-x-offset" value="${xOffset}">
          <input type="hidden" class="b-y-offset" value="${yOffset}">
        ` : `
          <input type="hidden" class="b-row-offset" value="0">
          <input type="hidden" class="b-col-offset" value="0">
          <input type="hidden" class="b-x-offset" value="0">
          <input type="hidden" class="b-y-offset" value="0">
        `}
        <input type="hidden" class="b-zone" value="${_escapeAttr(zone)}">
        <input type="hidden" class="b-grade" value="${_escapeAttr(grade)}">
        <input type="hidden" class="b-price" value="${_escapeAttr(price)}">
        <div class="zone-card-grid">
          <div class="zone-field"><span>시작 행</span><input type="number" class="b-start-row" placeholder="행" min="1" value="${linkedStart?.startRow ?? startRow}" required style="text-align:center;" ${linked ? 'readonly' : ''} oninput="_sbcRecalc()"></div>
          <div class="zone-field"><span>시작 열</span><input type="number" class="b-start-col" placeholder="열" min="1" value="${linkedStart?.startCol ?? startCol}" required style="text-align:center;" ${linked ? 'readonly' : ''} oninput="_sbcRecalc()"></div>
          <div class="zone-field"><span>행 수 (Rows)</span><input type="number" class="b-rows" placeholder="행" min="1" value="${data.rows||''}" required style="text-align:center;" oninput="_sbcRecalc()"></div>
          <div class="zone-field"><span>열 수 (Cols)</span><input type="number" class="b-cols" placeholder="열" min="1" value="${data.cols||''}" required style="text-align:center;" oninput="_sbcRecalc()"></div>
        </div>
        <div class="zone-layout-grid">
          <div class="zone-field"><span>시작 X</span><input type="number" class="b-start-x" step="0.1" value="${linkedStart?.startX ?? startX}" style="text-align:right;"></div>
          <div class="zone-field"><span>시작 Y</span><input type="number" class="b-start-y" step="0.1" value="${linkedStart?.startY ?? startY}" style="text-align:right;"></div>
          <div class="zone-field"><span>좌석 너비</span><input type="number" class="b-seat-width" step="0.1" min="0" value="${seatWidth}" style="text-align:right;"></div>
          <div class="zone-field"><span>좌석 높이</span><input type="number" class="b-seat-height" step="0.1" min="0" value="${seatHeight}" style="text-align:right;"></div>
          <div class="zone-field"><span>가로 간격</span><input type="number" class="b-gap-x" step="0.1" min="0" value="${gapX}" style="text-align:right;"></div>
          <div class="zone-field"><span>세로 간격</span><input type="number" class="b-gap-y" step="0.1" min="0" value="${gapY}" style="text-align:right;"></div>
          <div class="zone-field"><span>회전</span><input type="number" class="b-rotation" step="0.1" value="${rotation}" style="text-align:right;"></div>
          <div class="zone-field"><span>배치 각도</span><input type="number" class="b-layout-angle" step="0.1" value="${layoutAngle}" style="text-align:right;"></div>
        </div>
        <div class="zone-preview-chip">
          <i class="ti ti-armchair" style="color:var(--purple);"></i>이 구역: <strong class="zone-seat-count">0</strong>석
        </div>
      `;
      div.addEventListener('input', _sbcRecalc);
      wrapper.appendChild(div);
      sbcBlocks.push({
        id: cid,
        label: `좌석 묶음 #${sbcBlocks.length + 1}`,
        anchorId: linked ? anchorBlock?.id || null : null,
        anchorSide: linked ? mode : 'DIRECT'
      });
      _sbcUpdateLabels();
      _sbcRecalc();
    };
    window.sbcAddSeatBlock = function (mode = 'DIRECT', requestedAnchorId = null) {
      const anchorId = mode === 'RIGHT' || mode === 'BOTTOM' ? requestedAnchorId : null;
      if ((mode === 'RIGHT' || mode === 'BOTTOM') && !anchorId) {
        sbcAddZoneCard({ zone: smAreaName || '', grade: smAreaGrade || 'VIP', rows: 10, cols: 10, price: smAreaPrice ?? '', startX: 80, startY: 80, seatWidth: 14, seatHeight: 14, gapX: 4, gapY: 4, rotation: 0, layoutAngle: 0 }, 'DIRECT');
        return;
      }
      const anchorCard = anchorId ? _sbcCardById(anchorId) : null;
      const anchorLayout = anchorCard ? _sbcReadCardLayout(anchorCard) : null;
      sbcAddZoneCard({
        zone: smAreaName || '',
        grade: smAreaGrade || 'VIP',
        rows: anchorLayout?.rows || 10,
        cols: anchorLayout?.cols || 10,
        price: smAreaPrice ?? '',
        seatWidth: anchorLayout?.seatWidth ?? 14,
        seatHeight: anchorLayout?.seatHeight ?? 14,
        gapX: anchorLayout?.gapX ?? 4,
        gapY: anchorLayout?.gapY ?? 4,
        rotation: anchorLayout?.rotation ?? 0,
        layoutAngle: anchorLayout?.layoutAngle ?? 0
      }, mode, anchorId);
    };
    window.sbcRemoveSeatBlock = function (id) {
      const children = sbcBlocks.filter(block => block.anchorId === id);
      if (children.length > 0) {
        showToast('연결된 좌석 묶음이 있어 먼저 하위 묶음을 삭제해주세요.', true);
        return;
      }
      document.getElementById(id)?.remove();
      sbcBlocks = sbcBlocks.filter(block => block.id !== id);
      _sbcUpdateLabels();
      _sbcRecalc();
    };
    window._sbcFormatPrice = _formatDigitInput;

    window.openSeatBulkCreatePanel = function () {
      document.getElementById('sbc-subtitle').textContent = '현재 선택한 구역에 좌석 묶음을 추가합니다.';
      document.getElementById('sbc-event-id-label').textContent = smEventId || '-';
      document.getElementById('sbc-area-label').textContent = smAreaName || '-';
      document.getElementById('sbc-grade-label').textContent = smAreaGrade || '-';
      document.getElementById('sbc-price-label').textContent = smAreaPrice !== null && smAreaPrice !== undefined && smAreaPrice !== ''
        ? `${Number(smAreaPrice).toLocaleString()}원`
        : '-';
      const wrapper = document.getElementById('sbc-zone-wrapper');
      wrapper.innerHTML = '';
      sbcBlocks = [];
      sbcAddZoneCard({ zone: smAreaName || '', grade: smAreaGrade || 'VIP', rows: 10, cols: 10, price: smAreaPrice ?? '', startX: 80, startY: 80, seatWidth: 14, seatHeight: 14, gapX: 4, gapY: 4, rotation: 0, layoutAngle: 0 }, 'DIRECT');
      _sbcRecalc();
      document.getElementById('seat-bulk-create-modal').style.display = 'flex';
    };
    window.closeSeatBulkCreateModal = function () {
      document.getElementById('seat-bulk-create-modal').style.display = 'none';
    };

    async function _sbcConfirmReplaceExistingSeats() {
      if (!smAreaId) return true;

      const checkRes = await Fetch(`${SEAT_API}/select`, {
        method: 'POST',
        headers: authHeader(),
        body: JSON.stringify({ eventId: smEventId, areaId: smAreaId, page: 0, size: 1, sort: ['seatId-desc'] })
      });

      if (!checkRes.ok) {
        showToast('기존 좌석 정보를 확인하지 못했습니다.', true);
        return false;
      }

      const paged = await checkRes.json();
      const total = paged.page?.totalElements ?? paged.totalElements ?? 0;
      if (total <= 0) return true;

      const confirmed = window.confirm(`현재 구역에 이미 ${Number(total).toLocaleString()}개의 좌석이 있습니다.\n기존 좌석을 삭제하고 새로 등록할까요?`);
      if (!confirmed) return false;

      const deleteRes = await Fetch(`${SEAT_API}/delete/area/${smAreaId}`, {
        method: 'DELETE',
        headers: authHeader()
      });

      if (!deleteRes.ok) {
        showToast('기존 좌석 삭제에 실패했습니다.', true);
        return false;
      }

      return true;
    }

    window.submitSeatBulkCreate = async function () {
      const cards = document.querySelectorAll('#sbc-zone-wrapper .zone-card');
      if (cards.length === 0) { showToast('구역을 최소 1개 추가해주세요.', true); return; }
      const configs = []; let valid = true;
      cards.forEach(card => {
        const zone  = card.querySelector('.b-zone').value.trim();
        const grade = card.querySelector('.b-grade').value;
        const rows  = card.querySelector('.b-rows').value;
        const cols  = card.querySelector('.b-cols').value;
        const startRow = card.querySelector('.b-start-row').value;
        const startCol = card.querySelector('.b-start-col').value;
        const price = card.querySelector('.b-price').value;
        const startX = _nullableFloat(card.querySelector('.b-start-x').value);
        const startY = _nullableFloat(card.querySelector('.b-start-y').value);
        const seatWidth = _nullableFloat(card.querySelector('.b-seat-width').value);
        const seatHeight = _nullableFloat(card.querySelector('.b-seat-height').value);
        const gapX = _nullableFloat(card.querySelector('.b-gap-x').value);
        const gapY = _nullableFloat(card.querySelector('.b-gap-y').value);
        const rotation = _nullableFloat(card.querySelector('.b-rotation').value);
        const layoutAngle = _nullableFloat(card.querySelector('.b-layout-angle').value);
        if (!zone||!rows||!cols||!startRow||!startCol||!price) { valid = false; return; }
        const config = { grade, zone, rows: parseInt(rows,10), cols: parseInt(cols,10), startRow: parseInt(startRow,10), startCol: parseInt(startCol,10), price: _parseDigitValue(price) };
        if (startX !== null) config.startX = startX;
        if (startY !== null) config.startY = startY;
        if (seatWidth !== null) config.seatWidth = seatWidth;
        if (seatHeight !== null) config.seatHeight = seatHeight;
        if (gapX !== null) config.gapX = gapX;
        if (gapY !== null) config.gapY = gapY;
        if (rotation !== null) config.rotation = rotation;
        if (layoutAngle !== null) config.layoutAngle = layoutAngle;
        configs.push(config);
      });
      if (!valid) { showToast('모든 구역 항목을 빠짐없이 입력해주세요.', true); return; }
      const totalSeats = configs.reduce((s,c) => s + c.rows*c.cols, 0);
      try {
        const canProceed = await _sbcConfirmReplaceExistingSeats();
        if (!canProceed) return;

        const res = await Fetch(`${SEAT_API}/insert`, {
          method: 'POST', headers: authHeader(),
          body: JSON.stringify({ eventId: smEventId, areaId: smAreaId, insertSeatAreaConfigs: configs })
        });
        if (res.ok) {
          showToast(`${totalSeats.toLocaleString()}석 일괄 생성 완료!`);
          closeSeatBulkCreateModal();
          loadSeatMgmtList(0);
        } else { showToast('좌석 생성 처리 중 오류 발생', true); }
      } catch { showToast('통신 오류', true); }
    };

    /* ── 좌석 검색 ── */
    window.submitSeatInlineSearch = function () {
      loadSeatMgmtList(0);
    };
    window.resetSeatDetailSearch = function () {
      smDetailFilters = { seatRow: null, seatCol: null, status: null };
      document.getElementById('sm-filter-row').value = '';
      document.getElementById('sm-filter-col').value = '';
      document.getElementById('sm-filter-status').value = '';
      document.getElementById('sm-search-reset-btn').style.display = 'none';
      loadSeatMgmtList(0);
    };

    ['sm-filter-row', 'sm-filter-col'].forEach(id => {
      document.getElementById(id)?.addEventListener('keydown', event => {
        if (event.key === 'Enter') loadSeatMgmtList(0);
      });
    });

  })();
