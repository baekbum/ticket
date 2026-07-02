/* ══════════════════════════════════════════════════════════
     좌석 관리 모달 JS (이벤트 종속 좌석 CRUD + 구역 일괄등록)
  ══════════════════════════════════════════════════════════ */
  (function () {
    const API_VER  = 'v1';
    const SEAT_BASE = window.location.port === '8999'
      ? `http://localhost:8999/admin`
      : '';
    const SEAT_API  = `${SEAT_BASE}/api/${API_VER}/seat`;
    const authHeader = () => ({ 'Content-Type': 'application/json', 'Authorization': `Bearer ${localStorage.getItem('accessToken')}` });

    /* ── 상태 ── */
    let smEventId    = null;
    let smTotalPages = 1;
    let smSelectedIds = new Set();
    let smDetailFilters = { zone: null, seatRow: null, seatCol: null, grade: null, status: null };

    /* ── 좌석 관리 모달 열기 ── */
    window.openSeatModal = function (eventId, title, artist) {
      smEventId = parseInt(eventId, 10);
      smSelectedIds.clear();
      smDetailFilters = { zone: null, seatRow: null, seatCol: null, grade: null, status: null };
      document.getElementById('sm-event-title').textContent  = `${title} — ${artist}`;
      document.getElementById('sm-event-id-label').textContent = eventId;
      document.getElementById('sm-filter-grade').value  = '';
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
      const grade    = smDetailFilters.grade  || document.getElementById('sm-filter-grade').value  || null;
      const status   = smDetailFilters.status || document.getElementById('sm-filter-status').value || null;

      const body = {
        eventId: smEventId,
        grade,
        status,
        zone:    smDetailFilters.zone    || null,
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
        document.getElementById('sm-page-total').textContent   = smTotalPages;
        document.getElementById('sm-page-current').value        = pageZeroIndexed + 1;

        const tbody = document.getElementById('sm-seat-tbody');
        tbody.innerHTML = '';

        if (list.length === 0) {
          tbody.innerHTML = `<tr><td colspan="9" style="text-align:center; color:var(--text-muted); padding:2rem; font-size:12.5px;">등록된 좌석이 없습니다. 구역 일괄 등록으로 좌석을 생성하세요.</td></tr>`;
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

    /* ── Redis 웜업 (좌석 모달에서) ── */
    window.openCacheWarmupFromSeatModal = function () {
      document.getElementById('cache-warmup-modal').style.display = 'flex';
      // 웜업 대상 ID를 seat 모달의 eventId 기준으로 재설정
      window._seatMgmtWarmupTarget = smEventId;
    };
    // 기존 submitCacheWarmup 오버라이드: seat modal 컨텍스트에서 호출 시 smEventId 사용
    window.submitCacheWarmup = async function () {
      const targetId = window._seatMgmtWarmupTarget || document.getElementById('m-target-id')?.value;
      const API_VER2 = 'v1';
      const base2 = window.location.port === '8999' ? `http://localhost:8999/admin` : '';
      try {
        const res = await Fetch(`${base2}/api/${API_VER2}/seat/warm-up/${targetId}`, {
          method: 'POST', headers: authHeader()
        });
        if (res.ok) {
          showToast('Redis 캐시 웜업이 완료되었습니다! ⚡');
          document.getElementById('cache-warmup-modal').style.display = 'none';
        } else { showToast('웜업 실패: 처리 중 오류가 발생했습니다.', true); }
      } catch { showToast('서버 통신 장애', true); }
    };
    window.closeCacheWarmupConfirmModal = function () {
      document.getElementById('cache-warmup-modal').style.display = 'none';
      window._seatMgmtWarmupTarget = null;
    };
    window.openCacheWarmupConfirmModal = window.openCacheWarmupFromSeatModal;

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
      let ok = 0, fail = 0;
      for (const id of ids) {
        try {
          const r = await Fetch(`${SEAT_API}/delete/id/${id}`, { method: 'DELETE', headers: authHeader() });
          r.ok ? ok++ : fail++;
        } catch { fail++; }
      }
      closeSeatBulkDeleteConfirm();
      smSelectedIds.clear(); _smUpdateBulkBar();
      showToast(fail === 0 ? `${ok}석 삭제 완료.` : `${ok}석 완료, ${fail}석 실패.`, fail > 0);
      loadSeatMgmtList(Math.max(parseInt(document.getElementById('sm-page-current').value,10)-1, 0));
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

    function _sbcRecalc() {
      let total = 0;
      document.querySelectorAll('#sbc-zone-wrapper .zone-card').forEach(card => {
        const r = parseInt(card.querySelector('.b-rows')?.value, 10) || 0;
        const c = parseInt(card.querySelector('.b-cols')?.value, 10) || 0;
        total  += r * c;
        card.querySelector('.zone-seat-count').textContent = (r * c).toLocaleString();
      });
      document.getElementById('sbc-total-count').textContent = total.toLocaleString();
    }
    window._sbcRecalc = _sbcRecalc;

    function _sbcUpdateLabels() {
      document.querySelectorAll('#sbc-zone-wrapper .zone-card').forEach((card, i) => {
        const el = card.querySelector('.zone-card-title-text');
        if (el) el.textContent = `배정 구역 #${i+1}`;
      });
    }
    window._sbcUpdateLabels = _sbcUpdateLabels;

    window.sbcAddZoneCard = function (data = {}) {
      const wrapper = document.getElementById('sbc-zone-wrapper');
      const zoneIndex = wrapper.querySelectorAll('.zone-card').length;
      const startX = data.startX ?? 80;
      const startY = data.startY ?? (80 + zoneIndex * 140);
      const seatWidth = data.seatWidth ?? 14;
      const seatHeight = data.seatHeight ?? 14;
      const gapX = data.gapX ?? 4;
      const gapY = data.gapY ?? 4;
      const rotation = data.rotation ?? 0;
      const layoutAngle = data.layoutAngle ?? 0;
      const cid = 'sbc-' + Date.now() + '-' + (sbcZoneCount++);
      const div = document.createElement('div');
      div.id = cid; div.className = 'zone-card';
      div.innerHTML = `
        <div class="zone-card-header">
          <div class="zone-card-title"><i class="ti ti-layers-intersect"></i><span class="zone-card-title-text">배정 구역</span></div>
          <button type="button" class="btn btn-sm btn-danger"
                  onclick="document.getElementById('${cid}').remove(); _sbcUpdateLabels(); _sbcRecalc();">
            <i class="ti ti-x"></i>제거
          </button>
        </div>
        <div class="zone-card-grid">
          <div class="zone-field"><span>구역 명칭 (Zone)</span><input type="text" class="b-zone" placeholder="예: Floor A" value="${data.zone||''}" required></div>
          <div class="zone-field"><span>등급 (Grade)</span>
            <select class="b-grade">
              <option value="VIP" ${data.grade==='VIP'?'selected':''}>VIP석</option>
              <option value="R"   ${data.grade==='R'  ?'selected':''}>R석</option>
              <option value="S"   ${data.grade==='S'  ?'selected':''}>S석</option>
              <option value="A"   ${data.grade==='A'  ?'selected':''}>A석</option>
            </select>
          </div>
          <div class="zone-field"><span>행 수 (Rows)</span><input type="number" class="b-rows" placeholder="행" min="1" value="${data.rows||''}" required style="text-align:center;" oninput="_sbcRecalc()"></div>
          <div class="zone-field"><span>열 수 (Cols)</span><input type="number" class="b-cols" placeholder="열" min="1" value="${data.cols||''}" required style="text-align:center;" oninput="_sbcRecalc()"></div>
          <div class="zone-field"><span>단가 (Price)</span><input type="text" class="b-price" placeholder="원" inputmode="numeric" value="${data.price ? Number(data.price).toLocaleString() : ''}" required style="text-align:right;" oninput="_sbcFormatPrice(this)"></div>
        </div>
        <div class="zone-layout-grid">
          <div class="zone-field"><span>시작 X</span><input type="number" class="b-start-x" step="0.1" value="${startX}" style="text-align:right;"></div>
          <div class="zone-field"><span>시작 Y</span><input type="number" class="b-start-y" step="0.1" value="${startY}" style="text-align:right;"></div>
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
      wrapper.appendChild(div);
      _sbcUpdateLabels();
      _sbcRecalc();
    };
    window._sbcFormatPrice = _formatDigitInput;

    window.openSeatBulkCreatePanel = function () {
      document.getElementById('sbc-subtitle').textContent = `이벤트 ID ${smEventId}에 좌석 구역을 일괄 등록합니다.`;
      const wrapper = document.getElementById('sbc-zone-wrapper');
      wrapper.innerHTML = '';
      sbcAddZoneCard({ zone: 'Floor A', grade: 'VIP', rows: 10, cols: 10, price: 168000, startX: 80, startY: 80, seatWidth: 14, seatHeight: 14, gapX: 4, gapY: 4, rotation: 0, layoutAngle: 0 });
      _sbcRecalc();
      document.getElementById('seat-bulk-create-modal').style.display = 'flex';
    };
    window.closeSeatBulkCreateModal = function () {
      document.getElementById('seat-bulk-create-modal').style.display = 'none';
    };
    window.submitSeatBulkCreate = async function () {
      const cards = document.querySelectorAll('#sbc-zone-wrapper .zone-card');
      if (cards.length === 0) { showToast('구역을 최소 1개 추가해주세요.', true); return; }
      const configs = []; let valid = true;
      cards.forEach(card => {
        const zone  = card.querySelector('.b-zone').value.trim();
        const grade = card.querySelector('.b-grade').value;
        const rows  = card.querySelector('.b-rows').value;
        const cols  = card.querySelector('.b-cols').value;
        const price = card.querySelector('.b-price').value;
        const startX = _nullableFloat(card.querySelector('.b-start-x').value);
        const startY = _nullableFloat(card.querySelector('.b-start-y').value);
        const seatWidth = _nullableFloat(card.querySelector('.b-seat-width').value);
        const seatHeight = _nullableFloat(card.querySelector('.b-seat-height').value);
        const gapX = _nullableFloat(card.querySelector('.b-gap-x').value);
        const gapY = _nullableFloat(card.querySelector('.b-gap-y').value);
        const rotation = _nullableFloat(card.querySelector('.b-rotation').value);
        const layoutAngle = _nullableFloat(card.querySelector('.b-layout-angle').value);
        if (!zone||!rows||!cols||!price) { valid = false; return; }
        const config = { grade, zone, rows: parseInt(rows,10), cols: parseInt(cols,10), price: _parseDigitValue(price) };
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
        const res = await Fetch(`${SEAT_API}/insert`, {
          method: 'POST', headers: authHeader(),
          body: JSON.stringify({ eventId: smEventId, insertSeatAreaConfigs: configs })
        });
        if (res.ok) {
          showToast(`${totalSeats.toLocaleString()}석 일괄 생성 완료!`);
          closeSeatBulkCreateModal();
          loadSeatMgmtList(0);
        } else { showToast('좌석 생성 처리 중 오류 발생', true); }
      } catch { showToast('통신 오류', true); }
    };

    /* ── 좌석 상세 검색 ── */
    window.openSeatDetailSearch = function () {
      document.getElementById('sm-cond-zone').value   = smDetailFilters.zone    || '';
      document.getElementById('sm-cond-row').value    = smDetailFilters.seatRow || '';
      document.getElementById('sm-cond-col').value    = smDetailFilters.seatCol || '';
      document.getElementById('sm-cond-grade').value  = smDetailFilters.grade   || '';
      document.getElementById('sm-cond-status').value = smDetailFilters.status  || '';
      document.getElementById('seat-detail-search-modal').style.display = 'flex';
    };
    window.closeSeatDetailSearch = function () {
      document.getElementById('seat-detail-search-modal').style.display = 'none';
    };
    window.submitSeatDetailSearch = function () {
      const zone   = document.getElementById('sm-cond-zone').value.trim()  || null;
      const rowVal = document.getElementById('sm-cond-row').value.trim();
      const colVal = document.getElementById('sm-cond-col').value.trim();
      const grade  = document.getElementById('sm-cond-grade').value         || null;
      const status = document.getElementById('sm-cond-status').value        || null;

      smDetailFilters = {
        zone,
        seatRow: rowVal ? parseInt(rowVal, 10) : null,
        seatCol: colVal ? parseInt(colVal, 10) : null,
        grade,
        status
      };

      // 드롭다운 필터와 동기화
      document.getElementById('sm-filter-grade').value  = grade  || '';
      document.getElementById('sm-filter-status').value = status || '';

      // 필터 활성 배지 표시
      const hasFilter = Object.values(smDetailFilters).some(v => v !== null);
      document.getElementById('sm-search-reset-btn').style.display = hasFilter ? 'inline-flex' : 'none';

      closeSeatDetailSearch();
      loadSeatMgmtList(0);
    };
    window.resetSeatDetailSearch = function () {
      smDetailFilters = { zone: null, seatRow: null, seatCol: null, grade: null, status: null };
      document.getElementById('sm-filter-grade').value  = '';
      document.getElementById('sm-filter-status').value = '';
      document.getElementById('sm-search-reset-btn').style.display = 'none';
      loadSeatMgmtList(0);
    };

  })();
