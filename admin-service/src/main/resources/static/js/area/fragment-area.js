(function () {
const API = { VERSION: 'v1', LOCAL_PORT: '8999' };
const BASE_URL = window.location.port === API.LOCAL_PORT ? `http://localhost:${API.LOCAL_PORT}/admin` : '';
const AREA_URL = `${BASE_URL}/api/${API.VERSION}/area`;
const headers = { 'Content-Type': 'application/json', 'Authorization': `Bearer ${localStorage.getItem('accessToken')}` };

let currentAreaList = [];
let currentAreaFilters = { eventId: null, areaName: null };
let serverTotalPages = 1;
let initialized = false;
let areaBulkCardSeq = 0;
let selectedAreaIds = new Set();

function inputValue(id) {
  return document.getElementById(id)?.value?.trim() || '';
}

function nullableNumber(id) {
  const value = inputValue(id).replace(/,/g, '');
  return value ? Number(value) : null;
}

function nullableFloat(id) {
  const value = inputValue(id);
  if (!value) return null;
  const parsed = parseFloat(value);
  return Number.isNaN(parsed) ? null : parsed;
}

function formatDigitInput(input) {
  if (!input) return;
  const raw = input.value.replace(/[^\d]/g, '');
  input.value = raw ? Number(raw).toLocaleString() : '';
}

function setValue(id, value) {
  const el = document.getElementById(id);
  if (el) el.value = value ?? '';
}

function syncContextChip() {
  const chip = document.getElementById('area-context-chip');
  const label = document.getElementById('area-context-event-id');
  if (!chip || !label) return;
  if (currentAreaFilters.eventId) {
    label.textContent = currentAreaFilters.eventId;
    chip.classList.add('visible');
  } else {
    label.textContent = '-';
    chip.classList.remove('visible');
  }
}

function updateAreaBulkBar() {
  const bar = document.getElementById('area-bulk-action-bar');
  const count = document.getElementById('area-selected-count');
  if (!bar || !count) return;
  count.textContent = selectedAreaIds.size;
  if (selectedAreaIds.size > 0) bar.classList.add('visible');
  else bar.classList.remove('visible');
}

window.toggleAreaSelectAll = function (masterCb) {
  document.querySelectorAll('.area-row-checkbox').forEach(cb => {
    cb.checked = masterCb.checked;
    const id = parseInt(cb.dataset.id, 10);
    const row = cb.closest('tr');
    if (masterCb.checked) {
      selectedAreaIds.add(id);
      row?.classList.add('selected-row');
    } else {
      selectedAreaIds.delete(id);
      row?.classList.remove('selected-row');
    }
  });
  updateAreaBulkBar();
};

window.toggleAreaRowCheckbox = function (cb, id) {
  const row = cb.closest('tr');
  if (cb.checked) {
    selectedAreaIds.add(id);
    row?.classList.add('selected-row');
  } else {
    selectedAreaIds.delete(id);
    row?.classList.remove('selected-row');
  }

  const all = document.querySelectorAll('.area-row-checkbox');
  const master = document.getElementById('area-select-all-checkbox');
  if (master) master.checked = all.length > 0 && [...all].every(c => c.checked);
  updateAreaBulkBar();
};

window.clearAreaSelections = function () {
  selectedAreaIds.clear();
  document.querySelectorAll('.area-row-checkbox').forEach(cb => {
    cb.checked = false;
    cb.closest('tr')?.classList.remove('selected-row');
  });
  const master = document.getElementById('area-select-all-checkbox');
  if (master) master.checked = false;
  updateAreaBulkBar();
};

window.openAreaBulkDeleteConfirmModal = function () {
  if (selectedAreaIds.size === 0) {
    showToast('삭제할 구역을 선택해주세요.', true);
    return;
  }
  document.getElementById('area-bulk-delete-count').textContent = selectedAreaIds.size;
  document.getElementById('area-bulk-delete-modal').style.display = 'flex';
};

window.closeAreaBulkDeleteConfirmModal = function () {
  document.getElementById('area-bulk-delete-modal').style.display = 'none';
};

window.submitAreaBulkDelete = async function () {
  const ids = [...selectedAreaIds];
  try {
    const res = await Fetch(`${AREA_URL}/delete/bulk`, {
      method: 'DELETE',
      headers,
      body: JSON.stringify({ areaIds: ids })
    });

    if (res.ok) {
      closeAreaBulkDeleteConfirmModal();
      selectedAreaIds.clear();
      updateAreaBulkBar();
      showToast(`${ids.length}개 구역을 삭제했습니다.`);
      loadAreaList(Math.max(parseInt(document.getElementById('pagination-current').value, 10) - 1, 0));
    } else {
      showToast('구역 일괄 삭제 처리 중 오류가 발생했습니다.', true);
    }
  } catch (e) {
    showToast('서버 통신 실패', true);
  }
};

window.loadAreaList = async function (pageZeroIndexed = 0) {
  const pageSize = parseInt(document.getElementById('pagination-size').value, 10);
  const cond = {
    page: pageZeroIndexed,
    size: pageSize,
    sort: ['areaId-desc']
  };

  if (currentAreaFilters.eventId !== null) cond.eventId = currentAreaFilters.eventId;
  if (currentAreaFilters.areaName !== null) cond.areaName = currentAreaFilters.areaName;

  syncContextChip();

  try {
    const res = await Fetch(`${AREA_URL}/select`, { method: 'POST', headers, body: JSON.stringify(cond) });
    if (!res.ok) { showToast('구역 목록 조회에 실패했습니다.', true); return; }

    const paged = await res.json();
    currentAreaList = paged.content || [];
    serverTotalPages = Math.max(paged.page?.totalPages || paged.totalPages || 1, 1);
    const totalCount = paged.totalElements ?? paged.page?.totalElements ?? currentAreaList.length;

    document.getElementById('pagination-total').textContent = serverTotalPages;
    document.getElementById('pagination-current').value = pageZeroIndexed + 1;
    document.getElementById('pagination-total-count').textContent = totalCount;

    const tbody = document.getElementById('area-table-body');
    tbody.innerHTML = '';
    selectedAreaIds.clear();
    updateAreaBulkBar();
    const master = document.getElementById('area-select-all-checkbox');
    if (master) master.checked = false;

    if (currentAreaList.length === 0) {
      tbody.innerHTML = `<tr><td colspan="15" style="text-align:center;color:var(--text-muted);padding:2rem;">조회된 구역 정보가 없습니다.</td></tr>`;
      return;
    }

    currentAreaList.forEach((area, index) => {
      const tr = document.createElement('tr');
      const statusClass = area.status === 'ACTIVE' ? 'badge-active' : 'badge-inactive';
      const rowNumber = pageZeroIndexed * pageSize + index + 1;
      tr.innerHTML = `
        <td style="text-align:center;" onclick="event.stopPropagation()">
          <input type="checkbox" class="area-row-checkbox" data-id="${area.areaId}"
                 onclick="event.stopPropagation(); toggleAreaRowCheckbox(this, ${area.areaId})">
        </td>
        <td style="text-align:center;color:var(--text-muted);">${rowNumber}</td>
        <td><strong>${area.areaId}</strong></td>
        <td>${area.eventId ?? ''}</td>
        <td title="${area.areaName || ''}">${area.areaName || ''}</td>
        <td>${area.grade || ''}</td>
        <td style="text-align:right;">${area.price != null ? Number(area.price).toLocaleString() : ''}</td>
        <td>${area.positionX ?? ''}</td>
        <td>${area.positionY ?? ''}</td>
        <td>${area.width ?? ''}</td>
        <td>${area.height ?? ''}</td>
        <td>${area.rotation ?? ''}</td>
        <td>${area.layoutAngle ?? ''}</td>
        <td><span class="badge ${statusClass}">${area.status || ''}</span></td>
        <td class="actions">
          <button class="btn btn-sm btn-outline" onclick="event.stopPropagation(); openAreaModalForUpdate(${area.areaId})">수정</button>
          <button class="btn btn-sm btn-danger" onclick="event.stopPropagation(); openAreaDeleteModal(${area.areaId})">삭제</button>
          <button class="btn btn-sm btn-outline" onclick="event.stopPropagation(); openAreaSeatModal(${area.areaId})"><i class="ti ti-armchair"></i>좌석</button>
        </td>
      `;
      tr.onclick = () => openAreaModalForUpdate(area.areaId);
      tbody.appendChild(tr);
    });
  } catch (e) {
    showToast('구역 목록 통신 오류가 발생했습니다.', true);
  }
};

window.triggerAreaSearch = function () {
  const eventId = inputValue('area-search-event-id');
  currentAreaFilters = {
    eventId: eventId ? parseInt(eventId, 10) : null,
    areaName: inputValue('area-search-name') || null
  };
  loadAreaList(0);
};

window.resetAreaSearch = function () {
  setValue('area-search-event-id', '');
  setValue('area-search-name', '');
  currentAreaFilters = { eventId: null, areaName: null };
  loadAreaList(0);
};

function refreshAreaBulkCount() {
  const countEl = document.getElementById('area-bulk-count');
  if (countEl) countEl.textContent = document.querySelectorAll('#area-bulk-wrapper .area-card').length;
}

function removeAreaBulkCard(cardId) {
  document.getElementById(cardId)?.remove();
  refreshAreaBulkCount();
}

function areaBulkCardTemplate(data = {}) {
  const cardId = `area-bulk-card-${Date.now()}-${areaBulkCardSeq++}`;
  const eventId = data.eventId ?? currentAreaFilters.eventId ?? '';
  const areaName = data.areaName ?? '';
  const grade = data.grade ?? 'VIP';
  const price = data.price != null ? Number(data.price).toLocaleString() : '';
  const positionX = data.positionX ?? 80;
  const positionY = data.positionY ?? 80;
  const width = data.width ?? 100;
  const height = data.height ?? 100;
  const rotation = data.rotation ?? 0;
  const layoutAngle = data.layoutAngle ?? 0;
  const status = data.status ?? 'ACTIVE';

  return `
    <div class="area-card" id="${cardId}">
      <div class="area-card-header">
        <div class="area-card-title"><i class="ti ti-layout-grid"></i><span>구역 정보</span></div>
        <button type="button" class="btn btn-sm btn-danger" onclick="removeAreaBulkCard('${cardId}')">
          <i class="ti ti-x"></i>제거
        </button>
      </div>
      <div class="area-card-grid">
        <label class="area-field"><span>이벤트 ID</span><input type="number" class="ab-event-id" min="1" value="${eventId}"></label>
        <label class="area-field"><span>구역명</span><input type="text" class="ab-area-name" value="${areaName}" placeholder="예: FLOOR-가"></label>
        <label class="area-field"><span>등급</span>
          <select class="ab-grade">
            <option value="VIP" ${grade === 'VIP' ? 'selected' : ''}>VIP</option>
            <option value="R" ${grade === 'R' ? 'selected' : ''}>R</option>
            <option value="S" ${grade === 'S' ? 'selected' : ''}>S</option>
            <option value="A" ${grade === 'A' ? 'selected' : ''}>A</option>
          </select>
        </label>
        <label class="area-field"><span>가격</span><input type="text" class="ab-price" inputmode="numeric" value="${price}" oninput="formatAreaBulkPrice(this)"></label>
        <label class="area-field"><span>위치 X</span><input type="number" class="ab-position-x" step="0.1" value="${positionX}"></label>
        <label class="area-field"><span>위치 Y</span><input type="number" class="ab-position-y" step="0.1" value="${positionY}"></label>
        <label class="area-field"><span>구역 너비</span><input type="number" class="ab-width" step="0.1" min="0" value="${width}"></label>
        <label class="area-field"><span>구역 높이</span><input type="number" class="ab-height" step="0.1" min="0" value="${height}"></label>
        <label class="area-field"><span>회전</span><input type="number" class="ab-rotation" step="0.1" value="${rotation}"></label>
        <label class="area-field"><span>배치 각도</span><input type="number" class="ab-layout-angle" step="0.1" value="${layoutAngle}"></label>
        <label class="area-field"><span>상태</span>
          <select class="ab-status">
            <option value="ACTIVE" ${status === 'ACTIVE' ? 'selected' : ''}>ACTIVE</option>
            <option value="INACTIVE" ${status === 'INACTIVE' ? 'selected' : ''}>INACTIVE</option>
          </select>
        </label>
      </div>
    </div>
  `;
}

window.formatAreaBulkPrice = function (input) {
  formatDigitInput(input);
};

window.removeAreaBulkCard = removeAreaBulkCard;

window.addAreaBulkCard = function (data = {}) {
  const wrapper = document.getElementById('area-bulk-wrapper');
  wrapper.insertAdjacentHTML('beforeend', areaBulkCardTemplate(data));
  refreshAreaBulkCount();
  wrapper.lastElementChild?.scrollIntoView({ behavior: 'smooth', block: 'end' });
};

window.openAreaBulkModal = function () {
  const wrapper = document.getElementById('area-bulk-wrapper');
  wrapper.innerHTML = '';
  addAreaBulkCard();
  document.getElementById('area-bulk-modal').style.display = 'flex';
};

window.closeAreaBulkModal = function () {
  document.getElementById('area-bulk-modal').style.display = 'none';
};

function readAreaBulkCard(card) {
  const value = selector => card.querySelector(selector)?.value?.trim() || '';
  const numberValue = selector => {
    const raw = value(selector).replace(/,/g, '');
    return raw ? Number(raw) : null;
  };

  return {
    eventId: numberValue('.ab-event-id'),
    areaName: value('.ab-area-name'),
    grade: value('.ab-grade'),
    price: numberValue('.ab-price'),
    positionX: numberValue('.ab-position-x'),
    positionY: numberValue('.ab-position-y'),
    width: numberValue('.ab-width'),
    height: numberValue('.ab-height'),
    rotation: numberValue('.ab-rotation'),
    layoutAngle: numberValue('.ab-layout-angle'),
    status: value('.ab-status') || 'ACTIVE'
  };
}

window.submitAreaBulkForm = async function () {
  const cards = [...document.querySelectorAll('#area-bulk-wrapper .area-card')];
  if (cards.length === 0) {
    showToast('등록할 구역을 추가해주세요.', true);
    return;
  }

  const areas = cards.map(readAreaBulkCard);
  const invalidIndex = areas.findIndex(area => !area.eventId || !area.areaName || !area.grade || !area.price);
  if (invalidIndex >= 0) {
    showToast(`${invalidIndex + 1}번째 구역의 이벤트 ID, 구역명, 등급, 가격을 입력해주세요.`, true);
    return;
  }

  areas.forEach(area => Object.keys(area).forEach(key => area[key] === null && delete area[key]));

  try {
    const res = await Fetch(`${AREA_URL}/insert/bulk`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ areas })
    });

    if (res.ok) {
      showToast(`${areas.length}개 구역을 등록했습니다.`);
      closeAreaBulkModal();
      loadAreaList(0);
    } else {
      showToast('구역 일괄 등록에 실패했습니다.', true);
    }
  } catch (e) {
    showToast('구역 일괄 등록 통신 오류가 발생했습니다.', true);
  }
};

window.openAreaJsonModal = function () {
  document.getElementById('area-json-modal').style.display = 'flex';
};

window.closeAreaJsonModal = function () {
  document.getElementById('area-json-modal').style.display = 'none';
};

window.submitAreaJsonForm = async function () {
  const jsonText = inputValue('area-json-text');
  if (!jsonText) {
    showToast('등록할 JSON을 입력해주세요.', true);
    return;
  }

  try {
    const res = await Fetch(`${AREA_URL}/insert/json`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ jsonText })
    });

    if (res.ok) {
      const inserted = await res.json();
      showToast(`${inserted.length}개 구역을 등록했습니다.`);
      closeAreaJsonModal();
      loadAreaList(0);
    } else {
      showToast('구역 JSON 등록에 실패했습니다.', true);
    }
  } catch (e) {
    showToast('구역 JSON 등록 통신 오류가 발생했습니다.', true);
  }
};

window.openAreaModalForCreate = function () {
  setValue('area-modal-mode', 'CREATE');
  setValue('area-target-id', '');
  document.getElementById('area-modal-title').textContent = '구역 등록';
  setValue('area-event-id', currentAreaFilters.eventId || '');
  setValue('area-name', '');
  setValue('area-grade', 'VIP');
  setValue('area-price', '');
  setValue('area-position-x', '');
  setValue('area-position-y', '');
  setValue('area-width', '');
  setValue('area-height', '');
  setValue('area-rotation', '0');
  setValue('area-layout-angle', '0');
  setValue('area-status', 'ACTIVE');
  document.getElementById('area-event-id').disabled = false;
  document.getElementById('area-modal').style.display = 'flex';
};

window.openAreaModalForUpdate = function (areaId) {
  const area = currentAreaList.find(item => item.areaId === areaId);
  if (!area) { showToast('구역 정보를 찾을 수 없습니다.', true); return; }

  setValue('area-modal-mode', 'UPDATE');
  setValue('area-target-id', area.areaId);
  document.getElementById('area-modal-title').textContent = '구역 수정';
  setValue('area-event-id', area.eventId);
  setValue('area-name', area.areaName);
  setValue('area-grade', area.grade || 'VIP');
  setValue('area-price', area.price != null ? Number(area.price).toLocaleString() : '');
  setValue('area-position-x', area.positionX);
  setValue('area-position-y', area.positionY);
  setValue('area-width', area.width);
  setValue('area-height', area.height);
  setValue('area-rotation', area.rotation ?? 0);
  setValue('area-layout-angle', area.layoutAngle ?? 0);
  setValue('area-status', area.status || 'ACTIVE');
  document.getElementById('area-event-id').disabled = true;
  document.getElementById('area-modal').style.display = 'flex';
};

window.closeAreaModal = function () {
  document.getElementById('area-modal').style.display = 'none';
};

window.submitAreaForm = async function () {
  const mode = inputValue('area-modal-mode');
  const eventId = nullableNumber('area-event-id');
  const areaName = inputValue('area-name');
  const price = nullableNumber('area-price');

  if (!eventId || !areaName || !price) {
    showToast('이벤트 ID, 구역명, 가격은 필수입니다.', true);
    return;
  }

  const body = {
    areaName,
    grade: inputValue('area-grade'),
    price,
    positionX: nullableFloat('area-position-x'),
    positionY: nullableFloat('area-position-y'),
    width: nullableFloat('area-width'),
    height: nullableFloat('area-height'),
    rotation: nullableFloat('area-rotation'),
    layoutAngle: nullableFloat('area-layout-angle'),
    status: inputValue('area-status') || 'ACTIVE'
  };

  if (mode === 'CREATE') body.eventId = eventId;

  Object.keys(body).forEach(key => body[key] === null && delete body[key]);

  const url = mode === 'CREATE' ? `${AREA_URL}/insert` : `${AREA_URL}/update/id/${inputValue('area-target-id')}`;
  const method = mode === 'CREATE' ? 'POST' : 'PUT';

  try {
    const res = await Fetch(url, { method, headers, body: JSON.stringify(body) });
    if (res.ok) {
      showToast(mode === 'CREATE' ? '구역이 등록되었습니다.' : '구역이 수정되었습니다.');
      closeAreaModal();
      loadAreaList(mode === 'CREATE' ? 0 : parseInt(document.getElementById('pagination-current').value, 10) - 1);
    } else {
      showToast('구역 저장에 실패했습니다.', true);
    }
  } catch (e) {
    showToast('구역 저장 통신 오류가 발생했습니다.', true);
  }
};

window.openAreaDeleteModal = function (areaId) {
  setValue('area-delete-id', areaId);
  document.getElementById('area-delete-modal').style.display = 'flex';
};

window.closeAreaDeleteModal = function () {
  document.getElementById('area-delete-modal').style.display = 'none';
};

window.submitAreaDelete = async function () {
  const areaId = inputValue('area-delete-id');
  try {
    const res = await Fetch(`${AREA_URL}/delete/id/${areaId}`, { method: 'DELETE', headers });
    if (res.ok) {
      showToast('구역이 삭제되었습니다.');
      closeAreaDeleteModal();
      loadAreaList(Math.max(parseInt(document.getElementById('pagination-current').value, 10) - 1, 0));
    } else {
      showToast('구역 삭제에 실패했습니다.', true);
    }
  } catch (e) {
    showToast('구역 삭제 통신 오류가 발생했습니다.', true);
  }
};

window.openAreaSeatModal = function (areaId) {
  const area = currentAreaList.find(item => item.areaId === areaId);
  if (!area) { showToast('구역 정보를 찾을 수 없습니다.', true); return; }
  if (typeof window.openSeatModal !== 'function') {
    showToast('좌석 관리 화면을 초기화하지 못했습니다.', true);
    return;
  }

  window.openSeatModal(area.eventId, area.eventTitle || `Event ${area.eventId}`, '', area.areaId, area.areaName);
};

function initAreaFragment(context = {}) {
  if (initialized) {
    if (context.eventId) {
      setValue('area-search-event-id', context.eventId);
      currentAreaFilters.eventId = parseInt(context.eventId, 10);
      loadAreaList(0);
    }
    return;
  }
  initialized = true;

  document.getElementById('area-price')?.addEventListener('input', function () {
    formatDigitInput(this);
  });

  if (context.eventId) {
    setValue('area-search-event-id', context.eventId);
    currentAreaFilters.eventId = parseInt(context.eventId, 10);
  }

  window.Pagination.register({
    load: loadAreaList,
    getTotalPages: function () { return serverTotalPages; }
  });

  loadAreaList(0);
}

window.addEventListener('admin:fragment-loaded', function (event) {
  if (event.detail?.menuName === 'area') {
    initAreaFragment(event.detail.context || {});
  }
});

initAreaFragment(window.__areaInitialContext || {});
})();
