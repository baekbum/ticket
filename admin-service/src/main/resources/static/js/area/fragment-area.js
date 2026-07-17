(function () {
const API = { VERSION: 'v1', LOCAL_PORT: '8999' };
const BASE_URL = window.location.port === API.LOCAL_PORT ? `http://localhost:${API.LOCAL_PORT}/admin` : '';
const AREA_URL = `${BASE_URL}/api/${API.VERSION}/area`;
const headers = { 'Content-Type': 'application/json', 'Authorization': `Bearer ${localStorage.getItem('accessToken')}` };
const authHeaders = { 'Authorization': `Bearer ${localStorage.getItem('accessToken')}` };

let currentAreaList = [];
let currentAreaFilters = { eventId: null, areaName: null };
let serverTotalPages = 1;
let initialized = false;
let selectedAreaIds = new Set();
let pendingSvgReplace = false;

function inputValue(id) {
  return document.getElementById(id)?.value?.trim() || '';
}

function nullableNumber(id) {
  const value = inputValue(id).replace(/,/g, '');
  return value ? Number(value) : null;
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

window.getCurrentAreaEventId = function () {
  const filterEventId = currentAreaFilters.eventId;
  if (filterEventId) return filterEventId;

  const inputEventId = inputValue('area-search-event-id');
  return inputEventId ? parseInt(inputEventId, 10) : null;
};

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
      tbody.innerHTML = `<tr><td colspan="9" style="text-align:center;color:var(--text-muted);padding:2rem;">조회된 구역 정보가 없습니다.</td></tr>`;
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

window.openAreaSvgModal = function () {
  setValue('area-svg-event-id', currentAreaFilters.eventId || inputValue('area-search-event-id') || '');
  const fileInput = document.getElementById('area-svg-file');
  if (fileInput) fileInput.value = '';
  const fileName = document.getElementById('area-svg-file-name');
  if (fileName) fileName.textContent = 'SVG 파일 선택';
  document.getElementById('area-svg-modal').style.display = 'flex';
};

window.closeAreaSvgModal = function () {
  document.getElementById('area-svg-modal').style.display = 'none';
};

window.closeAreaSvgReplaceModal = function () {
  document.getElementById('area-svg-replace-modal').style.display = 'none';
  pendingSvgReplace = false;
};

window.confirmAreaSvgReplace = function () {
  document.getElementById('area-svg-replace-modal').style.display = 'none';
  submitAreaSvgForm(true);
};

window.submitAreaSvgForm = async function (force = false) {
  const eventId = inputValue('area-svg-event-id');
  const file = document.getElementById('area-svg-file')?.files?.[0];

  if (!eventId) {
    showToast('이벤트 ID를 입력해주세요.', true);
    return;
  }
  if (!file) {
    showToast('업로드할 SVG 파일을 선택해주세요.', true);
    return;
  }

  const formData = new FormData();
  formData.append('eventId', eventId);
  formData.append('svgFile', file);

  try {
    const res = await Fetch(`${AREA_URL}/insert/svg?force=${force}`, {
      method: 'POST',
      headers: authHeaders,
      body: formData
    });

    if (res.ok) {
      const inserted = await res.json();
      showToast(`${inserted.length}개 구역을 SVG에서 등록했습니다.`);
      pendingSvgReplace = false;
      closeAreaSvgModal();
      currentAreaFilters.eventId = parseInt(eventId, 10);
      setValue('area-search-event-id', eventId);
      loadAreaList(0);
    } else if (res.status === 409 && !force) {
      pendingSvgReplace = true;
      document.getElementById('area-svg-replace-modal').style.display = 'flex';
    } else {
      showToast('SVG 구역 등록에 실패했습니다.', true);
    }
  } catch (e) {
    showToast('SVG 구역 등록 통신 오류가 발생했습니다.', true);
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

window.openAreaModalForUpdate = function (areaId) {
  const area = currentAreaList.find(item => item.areaId === areaId);
  if (!area) { showToast('구역 정보를 찾을 수 없습니다.', true); return; }

  setValue('area-target-id', area.areaId);
  document.getElementById('area-modal-title').textContent = '구역 수정';
  setValue('area-event-id', area.eventId);
  setValue('area-name', area.areaName);
  setValue('area-grade', area.grade || 'VIP');
  setValue('area-price', area.price != null ? Number(area.price).toLocaleString() : '');
  setValue('area-status', area.status || 'ACTIVE');
  document.getElementById('area-event-id').disabled = true;
  document.getElementById('area-modal').style.display = 'flex';
};

window.closeAreaModal = function () {
  document.getElementById('area-modal').style.display = 'none';
};

window.submitAreaForm = async function () {
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
    status: inputValue('area-status') || 'ACTIVE'
  };

  Object.keys(body).forEach(key => body[key] === null && delete body[key]);

  const url = `${AREA_URL}/update/id/${inputValue('area-target-id')}`;

  try {
    const res = await Fetch(url, { method: 'PUT', headers, body: JSON.stringify(body) });
    if (res.ok) {
      showToast('구역을 수정했습니다.');
      closeAreaModal();
      loadAreaList(parseInt(document.getElementById('pagination-current').value, 10) - 1);
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
      showToast('구역을 삭제했습니다.');
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

  window.openSeatModal(area.eventId, area.eventTitle || `Event ${area.eventId}`, '', area.areaId, area.areaName, area.grade, area.price);
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

  document.getElementById('area-svg-file')?.addEventListener('change', function () {
    const fileName = document.getElementById('area-svg-file-name');
    if (fileName) fileName.textContent = this.files?.[0]?.name || 'SVG 파일 선택';
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
