(function () {
const API = { VERSION: 'v1', LOCAL_PORT: '8999', DEV_PORT: '8080' };
const BASE_URL  = window.location.port === API.LOCAL_PORT ? `http://localhost:${API.LOCAL_PORT}/admin` : '';
const TICKET_PUBLIC_BASE_URL = window.location.port === API.LOCAL_PORT ? 'http://localhost:8082' : '';
const EVENT_URL = `${BASE_URL}/api/${API.VERSION}/event`;
const AREA_URL  = `${BASE_URL}/api/${API.VERSION}/area`;
const SEAT_URL  = `${BASE_URL}/api/${API.VERSION}/seat`;
const headers   = { 'Content-Type': 'application/json', 'Authorization': `Bearer ${localStorage.getItem('accessToken')}` };

let currentEventList    = [];
let currentSearchFilters = {
eventId: null, title: null, artistName: null, venue: null, venueAddress: null, posterUrl: null,
eventDate: null, saleStartDate: null, saleEndDate: null, cancelDeadlineDate: null,
eventDateFrom: null, eventDateTo: null, saleStartDateFrom: null, saleStartDateTo: null,
saleEndDateFrom: null, saleEndDateTo: null, cancelDeadlineDateFrom: null, cancelDeadlineDateTo: null,
runningMinutes: null, ageLimit: null, totalSeats: null, availableSeats: null, status: null
};
let serverTotalPages    = 1;
let currentSortFilters  = {};
const areaLayoutCache = new Map();
const eventLayoutCache = new Map();
const seatLayoutCache = new Map();
const layoutSeatMap = new Map();
let currentLayoutEventId = null;
let currentLayoutEventTitle = '';
let currentLayoutAreas = [];
let currentLayoutMode = 'AREA';
let currentLayoutAreaId = null;
let currentLayoutAreaName = '';
let currentLayoutSelectedSeatId = null;
const layoutDefaultViewBox = { x: 0, y: 0, width: 700, height: 520 };
let layoutViewBox = { ...layoutDefaultViewBox };
let layoutZoom = 1;
let layoutDragState = null;
let layoutDragged = false;
let posterPreviewObjectUrl = null;

function formatDigitInput(input) {
if (!input) return;
const raw = input.value.replace(/[^\d]/g, '');
input.value = raw ? Number(raw).toLocaleString() : '';
}

function parseDigitInputValue(inputId) {
const el = document.getElementById(inputId);
return parseInt((el?.value || '').replace(/,/g, ''), 10);
}

function inputValue(id) {
return document.getElementById(id)?.value?.trim() || '';
}

function nullableNumber(id) {
const value = inputValue(id);
return value ? parseInt(value.replace(/,/g, ''), 10) : null;
}

function resolvePosterUrl(url) {
if (!url) return '';
if (/^https?:\/\//i.test(url)) return url;
return `${TICKET_PUBLIC_BASE_URL}${url}`;
}

function authOnlyHeaders() {
const token = localStorage.getItem('accessToken');
return token ? { 'Authorization': `Bearer ${token}` } : {};
}

/* Bulk selection */
let selectedIds = new Set(); // Set of ev.eventId (Number)

function updateBulkBar() {
const bar = document.getElementById('bulk-action-bar');
document.getElementById('bulk-count').textContent = selectedIds.size;
if (selectedIds.size > 0) bar.classList.add('visible');
else                      bar.classList.remove('visible');
}

window.toggleSelectAll = function (masterCb) {
document.querySelectorAll('.row-checkbox').forEach(cb => {
cb.checked = masterCb.checked;
const id  = parseInt(cb.dataset.id, 10);
const row = cb.closest('tr');
if (masterCb.checked) { selectedIds.add(id);    row.classList.add('selected-row'); }
else                  { selectedIds.delete(id); row.classList.remove('selected-row'); }
});
updateBulkBar();
};

window.toggleRowCheckbox = function (cb, id) {
const row = cb.closest('tr');
if (cb.checked) { selectedIds.add(id);    row.classList.add('selected-row'); }
else            { selectedIds.delete(id); row.classList.remove('selected-row'); }

const all    = document.querySelectorAll('.row-checkbox');
const master = document.getElementById('select-all-checkbox');
if (master) master.checked = all.length > 0 && [...all].every(c => c.checked);
updateBulkBar();
};

window.clearAllSelections = function () {
selectedIds.clear();
document.querySelectorAll('.row-checkbox').forEach(cb => { cb.checked = false; cb.closest('tr').classList.remove('selected-row'); });
const master = document.getElementById('select-all-checkbox');
if (master) master.checked = false;
updateBulkBar();
};

window.openBulkDeleteConfirmModal = function () {
if (selectedIds.size === 0) { showToast('삭제할 이벤트를 선택해주세요.', true); return; }
document.getElementById('bulk-delete-count').textContent = selectedIds.size;
document.getElementById('bulk-delete-modal').style.display = 'flex';
};
window.closeBulkDeleteConfirmModal = function () {
document.getElementById('bulk-delete-modal').style.display = 'none';
};

window.submitBulkDelete = async function () {
const ids = [...selectedIds];
try {
const res = await Fetch(`${EVENT_URL}/delete/bulk`, {
method: 'DELETE',
headers,
body: JSON.stringify({ eventIds: ids })
});

if (res.ok) {
closeBulkDeleteConfirmModal();
selectedIds.clear();
updateBulkBar();
showToast(`${ids.length}건의 이벤트를 삭제했습니다.`);

loadEventList(parseInt(document.getElementById('pagination-current').value, 10) - 1);
} else {
showToast('이벤트 일괄 삭제 처리 중 오류가 발생했습니다.', true);
}
} catch {
showToast('서버 통신에 실패했습니다.', true);
}
};

/* Date filter */
function formatToDatetimeLocal(str) {
if (!str) return '';
try {
const m = str.match(/\d+/g);
if (m && m.length >= 4) {
return `${m[0]}-${m[1].padStart(2,'0')}-${m[2].padStart(2,'0')}T${m[3].padStart(2,'0')}:${(m[4]||'00').padStart(2,'0')}`;
}
return str.replace(' ', 'T').slice(0, 16);
} catch { return ''; }
}

/* List load */
window.loadEventList = async function (pageZeroIndexed = 0) {
const pageSize  = parseInt(document.getElementById('pagination-size').value, 10);
const sortArray = Object.keys(currentSortFilters).reduce((acc, f) => {
if (currentSortFilters[f]) acc.push(`${f}-${currentSortFilters[f]}`);
return acc;
}, []);

const cond = { page: pageZeroIndexed, size: pageSize, sort: sortArray.length ? sortArray : ['eventId-desc'] };
if (currentSearchFilters.eventId    !== null) cond.eventId    = currentSearchFilters.eventId;
if (currentSearchFilters.title      !== null) cond.title      = currentSearchFilters.title;
if (currentSearchFilters.artistName !== null) cond.artistName = currentSearchFilters.artistName;
if (currentSearchFilters.venue      !== null) cond.venue      = currentSearchFilters.venue;
if (currentSearchFilters.venueAddress !== null) cond.venueAddress = currentSearchFilters.venueAddress;
if (currentSearchFilters.posterUrl !== null) cond.posterUrl = currentSearchFilters.posterUrl;
if (currentSearchFilters.eventDate  !== null) cond.eventDate  = currentSearchFilters.eventDate;
if (currentSearchFilters.eventDateFrom !== null) cond.eventDateFrom = currentSearchFilters.eventDateFrom;
if (currentSearchFilters.eventDateTo !== null) cond.eventDateTo = currentSearchFilters.eventDateTo;
if (currentSearchFilters.saleStartDate !== null) cond.saleStartDate = currentSearchFilters.saleStartDate;
if (currentSearchFilters.saleStartDateFrom !== null) cond.saleStartDateFrom = currentSearchFilters.saleStartDateFrom;
if (currentSearchFilters.saleStartDateTo !== null) cond.saleStartDateTo = currentSearchFilters.saleStartDateTo;
if (currentSearchFilters.saleEndDate !== null) cond.saleEndDate = currentSearchFilters.saleEndDate;
if (currentSearchFilters.saleEndDateFrom !== null) cond.saleEndDateFrom = currentSearchFilters.saleEndDateFrom;
if (currentSearchFilters.saleEndDateTo !== null) cond.saleEndDateTo = currentSearchFilters.saleEndDateTo;
if (currentSearchFilters.cancelDeadlineDate !== null) cond.cancelDeadlineDate = currentSearchFilters.cancelDeadlineDate;
if (currentSearchFilters.cancelDeadlineDateFrom !== null) cond.cancelDeadlineDateFrom = currentSearchFilters.cancelDeadlineDateFrom;
if (currentSearchFilters.cancelDeadlineDateTo !== null) cond.cancelDeadlineDateTo = currentSearchFilters.cancelDeadlineDateTo;
if (currentSearchFilters.runningMinutes !== null) cond.runningMinutes = currentSearchFilters.runningMinutes;
if (currentSearchFilters.ageLimit !== null) cond.ageLimit = currentSearchFilters.ageLimit;
if (currentSearchFilters.totalSeats !== null) cond.totalSeats = currentSearchFilters.totalSeats;
if (currentSearchFilters.availableSeats !== null) cond.availableSeats = currentSearchFilters.availableSeats;
if (currentSearchFilters.status     !== null) cond.status     = currentSearchFilters.status;

try {
const res = await Fetch(`${EVENT_URL}/select`, { method: 'POST', headers, body: JSON.stringify(cond) });
if (!res.ok) { showToast('공연 목록 조회에 실패했습니다.', true); return; }

const pagedModel = await res.json();
currentEventList = pagedModel.content || [];
serverTotalPages = Math.max(pagedModel.page?.totalPages || pagedModel.totalPages || 1, 1);
const totalCount = pagedModel.totalElements ?? pagedModel.page?.totalElements ?? currentEventList.length;

document.getElementById('pagination-total').textContent = serverTotalPages;
document.getElementById('pagination-current').value      = pageZeroIndexed + 1;
document.getElementById('pagination-total-count').textContent = totalCount;

// Reset selection on page change
updateBulkBar();
const master = document.getElementById('select-all-checkbox');
if (master) master.checked = false;

const tbody = document.getElementById('event-table-body');
tbody.innerHTML = '';

currentEventList.forEach((ev, index) => {
let statusHtml;
if      (ev.status === 'ON_SALE')  statusHtml = `<span class="badge badge-sale">판매중</span>`;
else if (ev.status === 'SOLD_OUT') statusHtml = `<span class="badge badge-soldout">留ㅼ쭊</span>`;
else                               statusHtml = `<span class="badge badge-closed">종료</span>`;

const rowOrder = (pageZeroIndexed * pageSize) + (index + 1);
const tr = document.createElement('tr');
tr.setAttribute('data-pk', ev.eventId);

tr.innerHTML = `
<td style="text-align:center;" onclick="event.stopPropagation()">
  <input type="checkbox" class="row-checkbox" data-id="${ev.eventId}"
         onclick="event.stopPropagation(); toggleRowCheckbox(this, ${ev.eventId})">
</td>
<td style="text-align:center; color:var(--text-muted); font-size:12px;">${rowOrder}</td>
<td onclick="event.stopPropagation()">${ev.posterUrl ? `<button type="button" class="event-poster-thumb-button" onclick="event.stopPropagation(); openPosterPreviewModal(${ev.eventId})" title="포스터 크게 보기"><span class="event-poster-thumb" style="background-image:url('${resolvePosterUrl(ev.posterUrl)}');"></span></button>` : ''}</td>
<td><strong style="color:var(--text-primary);">${ev.eventId}</strong></td>
<td>${ev.artistName}</td>
<td style="color:var(--text-primary); font-weight:500;">${ev.title}</td>
<td>${ev.venue || ''}</td>
<td title="${ev.venueAddress || ''}">${ev.venueAddress || ''}</td>
<td style="color:var(--text-secondary); font-size:12px;">${ev.eventDateTime || ''}</td>
<td style="color:var(--text-secondary); font-size:12px;">${ev.saleStartAt || ''}</td>
<td style="color:var(--text-secondary); font-size:12px;">${ev.saleEndAt || ''}</td>
<td style="color:var(--text-secondary); font-size:12px;">${ev.cancelDeadlineAt || ''}</td>
<td>${ev.runningMinutes != null ? ev.runningMinutes + '분' : ''}</td>
<td>${ev.ageLimit != null ? ev.ageLimit + '+' : ''}</td>
<td>${ev.totalSeats != null ? Number(ev.totalSeats).toLocaleString() : ''}</td>
<td>${ev.availableSeats != null ? Number(ev.availableSeats).toLocaleString() : ''}</td>
<td>${statusHtml}</td>
<td class="actions">
<button class="btn btn-sm btn-outline" onclick="event.stopPropagation(); window.openModalForUpdate('${ev.eventId}')">수정</button>
<button class="btn btn-sm btn-danger"  onclick="event.stopPropagation(); window.openConfirmModalFromRow('${ev.eventId}')">삭제</button>
<button class="btn btn-sm btn-seat"    onclick="event.stopPropagation(); window.openAreaMenu(${ev.eventId})"><i class="ti ti-armchair"></i>구역</button>
<button class="btn btn-sm btn-layout"  onclick="event.stopPropagation(); window.openLayoutPreview(${ev.eventId})"><i class="ti ti-map"></i>배치도</button>
</td>
`;

tr.onclick = () => window.openModalForView(ev.eventId);
tbody.appendChild(tr);
});

syncSortHeaderUI();
} catch (e) { showToast('서버 통신 오류가 발생했습니다.', true); }
};

/* Sorting */
window.handleSortClick = function (headerEl, event) {
const field   = headerEl.getAttribute('data-sort-field');
const current = currentSortFilters[field];
const next    = !current ? 'asc' : current === 'asc' ? 'desc' : null;

if (!event.shiftKey) currentSortFilters = {};
if (next) currentSortFilters[field] = next;
else      delete currentSortFilters[field];

loadEventList(0);
};

function syncSortHeaderUI() {
document.querySelectorAll('.sortable').forEach(th => {
const dir  = currentSortFilters[th.getAttribute('data-sort-field')];
const icon = th.querySelector('.sort-icon');
th.classList.remove('asc', 'desc');
if      (dir === 'asc')  { th.classList.add('asc');  icon.textContent = '^'; }
else if (dir === 'desc') { th.classList.add('desc'); icon.textContent = 'v'; }
else                     icon.textContent = '-';
});
}

/* Modal bind */
function _bindEventToModal(ev) {
_set('m-target-id',        ev.eventId);
_set('m-event-id',         ev.eventId);
_set('m-artist-name',      ev.artistName);
_set('m-title',            ev.title);
_set('m-venue',            ev.venue);
_set('m-venue-address',    ev.venueAddress);
_set('m-poster-url',       ev.posterUrl);
_set('m-total-seats',      ev.totalSeats);
formatDigitInput(document.getElementById('m-total-seats'));
_set('m-available-seats',  ev.availableSeats);
formatDigitInput(document.getElementById('m-available-seats'));
_set('m-status',           ev.status || 'ON_SALE');
_set('m-max-tickets',      String(ev.maxTicketsPerPerson || 2));
_set('m-description-text', ev.description);
_set('m-event-date-time',  formatToDatetimeLocal(ev.eventDateTime));
_set('m-sale-start-at',    formatToDatetimeLocal(ev.saleStartAt));
_set('m-sale-end-at',      formatToDatetimeLocal(ev.saleEndAt));
_set('m-cancel-deadline-at', formatToDatetimeLocal(ev.cancelDeadlineAt));
_set('m-running-minutes',  ev.runningMinutes);
_set('m-age-limit',        ev.ageLimit);
const posterInput = document.getElementById('m-poster-image');
if (posterInput) posterInput.value = '';
_setPosterFileName();
}

function _set(id, val) {
const el = document.getElementById(id);
if (el) el.value = (val !== undefined && val !== null) ? val : '';
}

function _setAllInputsState(disabled) {
document.querySelectorAll('#event-modal input, #event-modal select, #event-modal textarea').forEach(el => {
el.disabled        = disabled;
el.style.background = disabled ? 'var(--bg)' : '#fafafa';
el.style.color      = disabled ? 'var(--text-muted)' : 'var(--text-primary)';
el.style.cursor     = disabled ? 'not-allowed' : 'text';
});
}

function _setFieldDisabled(id) {
const el = document.getElementById(id);
if (!el) return;
el.disabled        = true;
el.style.background = 'var(--bg)';
el.style.color      = 'var(--text-muted)';
el.style.cursor     = 'not-allowed';
}

function _setPosterFileName() {
const fileInput = document.getElementById('m-poster-image');
const fileName = document.getElementById('m-poster-file-name');
if (!fileInput || !fileName) return;

fileName.textContent = fileInput.files && fileInput.files.length > 0
? fileInput.files[0].name
: '선택된 파일 없음';
}

function eventToUpdateRequest(ev, posterUrlOverride = undefined) {
const eventDateTime = formatToDatetimeLocal(ev.eventDateTime);
const saleStartAt = formatToDatetimeLocal(ev.saleStartAt);
const saleEndAt = formatToDatetimeLocal(ev.saleEndAt);
const cancelDeadlineAt = formatToDatetimeLocal(ev.cancelDeadlineAt);
return {
artistName: ev.artistName,
title: ev.title,
venue: ev.venue,
venueAddress: ev.venueAddress,
posterUrl: posterUrlOverride !== undefined ? posterUrlOverride : (ev.posterUrl || null),
eventDateTime: eventDateTime ? `${eventDateTime}:00` : ev.eventDateTime,
saleStartAt: saleStartAt ? `${saleStartAt}:00` : ev.saleStartAt,
saleEndAt: saleEndAt ? `${saleEndAt}:00` : ev.saleEndAt,
cancelDeadlineAt: cancelDeadlineAt ? `${cancelDeadlineAt}:00` : ev.cancelDeadlineAt,
runningMinutes: ev.runningMinutes,
ageLimit: ev.ageLimit,
totalSeats: ev.totalSeats,
availableSeats: ev.availableSeats,
maxTicketsPerPerson: ev.maxTicketsPerPerson,
description: ev.description,
status: ev.status
};
}

function clearPosterPreviewObjectUrl() {
if (posterPreviewObjectUrl) {
URL.revokeObjectURL(posterPreviewObjectUrl);
posterPreviewObjectUrl = null;
}
}

function setPosterPreviewImage(src) {
const img = document.getElementById('poster-preview-image');
const empty = document.getElementById('poster-preview-empty');
if (!img || !empty) return;
if (src) {
img.src = src;
img.style.display = 'block';
empty.style.display = 'none';
} else {
img.removeAttribute('src');
img.style.display = 'none';
empty.style.display = 'flex';
}
}

window.openPosterPreviewModal = function (eventId) {
const ev = currentEventList.find(e => e.eventId === parseInt(eventId, 10));
if (!ev) { showToast('공연 데이터를 찾을 수 없습니다.', true); return; }

clearPosterPreviewObjectUrl();
document.getElementById('poster-preview-event-id').value = ev.eventId;
document.getElementById('poster-preview-event-id-label').textContent = ev.eventId;
document.getElementById('poster-preview-event-title-label').textContent = ev.title || '-';
document.getElementById('poster-preview-title').textContent = `${ev.title || '공연'} 포스터`;
const fileInput = document.getElementById('poster-preview-file');
if (fileInput) fileInput.value = '';
document.getElementById('poster-preview-file-name').textContent = '선택된 파일 없음';
setPosterPreviewImage(ev.posterUrl ? resolvePosterUrl(ev.posterUrl) : '');
document.getElementById('poster-preview-modal').style.display = 'flex';
};

window.closePosterPreviewModal = function () {
clearPosterPreviewObjectUrl();
document.getElementById('poster-preview-modal').style.display = 'none';
};

function handlePosterPreviewFileChange() {
const fileInput = document.getElementById('poster-preview-file');
const fileName = document.getElementById('poster-preview-file-name');
const file = fileInput?.files?.[0];
if (fileName) fileName.textContent = file ? file.name : '선택된 파일 없음';
clearPosterPreviewObjectUrl();
if (file) {
posterPreviewObjectUrl = URL.createObjectURL(file);
setPosterPreviewImage(posterPreviewObjectUrl);
}
}

window.submitPosterPreviewUpdate = async function () {
const eventId = parseInt(document.getElementById('poster-preview-event-id').value, 10);
const ev = currentEventList.find(e => e.eventId === eventId);
const file = document.getElementById('poster-preview-file')?.files?.[0];
if (!ev) { showToast('공연 데이터를 찾을 수 없습니다.', true); return; }
if (!file) { showToast('변경할 포스터 이미지를 선택해주세요.', true); return; }

const formData = new FormData();
formData.append('event', new Blob([JSON.stringify(eventToUpdateRequest(ev))], { type: 'application/json' }));
formData.append('posterImage', file);

try {
const res = await Fetch(`${EVENT_URL}/update/id/${eventId}`, {
method: 'PUT',
headers: authOnlyHeaders(),
body: formData
});

if (!res.ok) { showToast('포스터 이미지 저장에 실패했습니다.', true); return; }
showToast('포스터 이미지가 저장되었습니다.');
closePosterPreviewModal();
loadEventList(Math.max(parseInt(document.getElementById('pagination-current').value, 10) - 1, 0));
} catch {
showToast('통신 오류가 발생했습니다.', true);
}
};

/* Modal: VIEW */
window.openModalForView = function (eventId) {
const ev = currentEventList.find(e => e.eventId === parseInt(eventId, 10));
if (!ev) { showToast('공연 데이터를 찾을 수 없습니다.', true); return; }

document.getElementById('m-modal-mode').value       = 'VIEW';
document.getElementById('modal-title').textContent    = '공연 상세 정보 조회';
document.getElementById('modal-subtitle').textContent = '읽기 전용 모드입니다.';

_setAllInputsState(true);
_bindEventToModal(ev);

document.getElementById('btn-modal-submit').style.display     = 'none';

const actionRow = document.getElementById('modal-action-row');
actionRow.style.display             = 'block';
actionRow.style.gridTemplateColumns = 'none';

const cancelBtn = document.querySelector('#event-modal .btn-secondary');
if (cancelBtn) { cancelBtn.textContent = '닫기'; cancelBtn.style.width = '100%'; }

document.getElementById('event-modal').style.display = 'flex';
};

/* Modal: UPDATE */
window.openModalForUpdate = function (eventId) {
const ev = currentEventList.find(e => e.eventId === parseInt(eventId, 10));
if (!ev) { showToast('공연 데이터를 찾을 수 없습니다.', true); return; }

document.getElementById('m-modal-mode').value       = 'UPDATE';
document.getElementById('modal-title').textContent    = '공연 정보 수정';
document.getElementById('modal-subtitle').textContent = '정보를 수정한 뒤 저장 버튼을 눌러주세요.';

_setAllInputsState(false);
_setFieldDisabled('m-event-id');
_bindEventToModal(ev);

const actionRow = document.getElementById('modal-action-row');
actionRow.style.display             = 'grid';
actionRow.style.gridTemplateColumns = 'repeat(2, 1fr)';

document.getElementById('btn-modal-submit').textContent = '변경사항 저장';
document.getElementById('btn-modal-submit').style.display   = 'block';

const cancelBtn = document.querySelector('#event-modal .btn-secondary');
if (cancelBtn) { cancelBtn.textContent = '취소'; cancelBtn.style.width = 'auto'; }

document.getElementById('event-modal').style.display = 'flex';
};

/* Modal: CREATE */
window.openModalForCreate = function () {
document.getElementById('m-modal-mode').value       = 'CREATE';
document.getElementById('modal-title').textContent    = '신규 공연 등록';
document.getElementById('modal-subtitle').textContent = '새 공연 일정을 등록합니다.';

_setAllInputsState(false);

[
'm-target-id','m-artist-name','m-title','m-venue','m-venue-address','m-poster-url','m-event-date-time',
'm-sale-start-at','m-sale-end-at','m-cancel-deadline-at','m-total-seats','m-available-seats',
'm-running-minutes','m-age-limit','m-description-text'
].forEach(id => _set(id, ''));
const posterInput = document.getElementById('m-poster-image');
if (posterInput) posterInput.value = '';
_set('m-status',      'ON_SALE');
_set('m-max-tickets', '2');
_set('m-event-id',    '자동 발급');
formatDigitInput(document.getElementById('m-total-seats'));
_setFieldDisabled('m-event-id');
_setFieldDisabled('m-poster-url');
_setFieldDisabled('m-available-seats');

const actionRow = document.getElementById('modal-action-row');
actionRow.style.display             = 'grid';
actionRow.style.gridTemplateColumns = 'repeat(2, 1fr)';

document.getElementById('btn-modal-submit').textContent       = '신규 공연 등록';
document.getElementById('btn-modal-submit').style.display     = 'block';

const cancelBtn = document.querySelector('#event-modal .btn-secondary');
if (cancelBtn) { cancelBtn.textContent = '취소'; cancelBtn.style.width = 'auto'; }

document.getElementById('event-modal').style.display = 'flex';
};

window.closeModal = function () { document.getElementById('event-modal').style.display = 'none'; };

/* Form submit */
window.submitEventForm = async function () {
const mode         = document.getElementById('m-modal-mode').value;
const artistVal    = document.getElementById('m-artist-name').value.trim();
const titleVal     = document.getElementById('m-title').value.trim();
const venueVal     = document.getElementById('m-venue').value.trim();
const venueAddressVal = document.getElementById('m-venue-address').value.trim();
const posterUrlVal = document.getElementById('m-poster-url').value.trim();
const datetimeInput= document.getElementById('m-event-date-time').value;
const saleStartInput = document.getElementById('m-sale-start-at').value;
const saleEndInput = document.getElementById('m-sale-end-at').value;
const cancelDeadlineInput = document.getElementById('m-cancel-deadline-at').value;
const seatsVal     = parseDigitInputValue('m-total-seats');
const availableSeatsVal = parseDigitInputValue('m-available-seats');
const runningMinutesVal = parseInt(document.getElementById('m-running-minutes').value, 10);
const ageLimitVal = parseInt(document.getElementById('m-age-limit').value, 10);
const maxTicketsVal= parseInt(document.getElementById('m-max-tickets').value, 10);
const descVal      = document.getElementById('m-description-text').value.trim();
const posterFile = document.getElementById('m-poster-image')?.files?.[0];
const missingFields = [];
if (!artistVal) missingFields.push('?꾪떚?ㅽ듃');
if (!titleVal) missingFields.push('공연 제목');
if (!venueVal) missingFields.push('개최 장소');
if (!venueAddressVal) missingFields.push('공연장 주소');
if (!datetimeInput) missingFields.push('공연 일정');
if (!saleStartInput) missingFields.push('판매 시작');
if (!saleEndInput) missingFields.push('판매 종료');
if (!cancelDeadlineInput) missingFields.push('취소 마감');
if (mode === 'CREATE' && !posterFile) missingFields.push('?ъ뒪???대?吏');

if (missingFields.length > 0) {
showToast(`필수 항목을 입력해주세요: ${missingFields.join(', ')}`, true); return;
}

if (!artistVal || !titleVal || !venueVal || !venueAddressVal || !datetimeInput || !saleStartInput || !saleEndInput || !cancelDeadlineInput) {
showToast('필수 항목을 모두 입력해주세요.', true); return;
}
if (!seatsVal || seatsVal <= 0) {
showToast('배정 좌석 수는 1 이상이어야 합니다.', true); return;
}
if (!runningMinutesVal || runningMinutesVal <= 0 || Number.isNaN(ageLimitVal) || ageLimitVal < 0) {
showToast('공연 시간과 관람 연령을 확인해주세요.', true); return;
}

const body = {
artistName: artistVal, title: titleVal, venue: venueVal, venueAddress: venueAddressVal,
posterUrl: posterUrlVal || null,
eventDateTime: datetimeInput + ':00',
saleStartAt: saleStartInput + ':00',
saleEndAt: saleEndInput + ':00',
cancelDeadlineAt: cancelDeadlineInput + ':00',
runningMinutes: runningMinutesVal,
ageLimit: ageLimitVal,
totalSeats: seatsVal, maxTicketsPerPerson: maxTicketsVal,
description: descVal
};

const url    = mode === 'CREATE' ? `${EVENT_URL}/insert` : `${EVENT_URL}/update/id/${document.getElementById('m-target-id').value}`;
const method = mode === 'CREATE' ? 'POST' : 'PUT';
if (mode !== 'CREATE') {
body.status = document.getElementById('m-status').value;
if (!Number.isNaN(availableSeatsVal)) body.availableSeats = availableSeatsVal;
}

try {
let requestOptions;
if (mode === 'CREATE' || posterFile) {
const formData = new FormData();
formData.append('event', new Blob([JSON.stringify(body)], { type: 'application/json' }));
if (posterFile) formData.append('posterImage', posterFile);
requestOptions = { method, headers: authOnlyHeaders(), body: formData };
} else {
requestOptions = { method, headers, body: JSON.stringify(body) };
}
const res = await Fetch(url, requestOptions);
if (res.ok) {
showToast(mode === 'CREATE' ? '성공적으로 등록되었습니다.' : '성공적으로 수정되었습니다.');
closeModal();
loadEventList(mode === 'CREATE' ? 0 : parseInt(document.getElementById('pagination-current').value, 10) - 1);
} else { showToast('처리에 실패했습니다.', true); }
} catch { showToast('통신 오류가 발생했습니다.', true); }
};

/* Single delete */
window.openConfirmModalFromRow = function (id) {
document.getElementById('confirm-target-id').value = id;
document.getElementById('confirm-modal').style.display = 'flex';
};
window.closeConfirmModal = function () { document.getElementById('confirm-modal').style.display = 'none'; };

window.submitDeleteConfirm = async function () {
const targetId = document.getElementById('confirm-target-id').value;
try {
const res = await Fetch(`${EVENT_URL}/delete/id/${targetId}`, { method: 'DELETE', headers });
if (res.ok) {
showToast('이벤트 정보가 삭제되었습니다.');
closeConfirmModal(); closeModal();
loadEventList(Math.max(parseInt(document.getElementById('pagination-current').value, 10) - 1, 0));
} else { showToast('삭제 처리에 실패했습니다.', true); }
} catch { showToast('통신 오류가 발생했습니다.', true); }
};

/* Redis cache warmup */
window.openCacheWarmupConfirmModal  = function () { document.getElementById('cache-warmup-modal').style.display = 'flex'; };
window.closeCacheWarmupConfirmModal = function () { document.getElementById('cache-warmup-modal').style.display = 'none'; };

window.submitCacheWarmup = async function () {
const targetId = document.getElementById('m-target-id').value;
try {
const res = await Fetch(`${SEAT_URL}/warm-up/${targetId}`, { method: 'POST', headers });
if (res.ok) { showToast('Redis 캐시 웜업이 완료되었습니다.'); closeCacheWarmupConfirmModal(); }
else         { showToast('웜업 실패: 백엔드 처리 중 오류가 발생했습니다.', true); }
} catch { showToast('서버 네트워크 연결 오류가 발생했습니다.', true); }
};

/* Search */
window.triggerNormalSearch = function () {
currentSearchFilters = {
eventId: null, title: document.getElementById('search-id').value.trim() || null, artistName: null,
venue: null, venueAddress: null, posterUrl: null, eventDate: null, saleStartDate: null, saleEndDate: null,
cancelDeadlineDate: null, eventDateFrom: null, eventDateTo: null, saleStartDateFrom: null, saleStartDateTo: null,
saleEndDateFrom: null, saleEndDateTo: null, cancelDeadlineDateFrom: null, cancelDeadlineDateTo: null,
runningMinutes: null, ageLimit: null, totalSeats: null, availableSeats: null, status: null
};
loadEventList(0);
};
window.openSearchModal  = function () { document.getElementById('search-modal').style.display = 'flex'; };
window.closeSearchModal = function () { document.getElementById('search-modal').style.display = 'none'; };

window.resetEventSearch = function () {
document.getElementById('search-id').value = '';
resetDetailedSearchForm();
currentSearchFilters = {
eventId: null, title: null, artistName: null,
venue: null, venueAddress: null, posterUrl: null, eventDate: null, saleStartDate: null, saleEndDate: null,
cancelDeadlineDate: null, eventDateFrom: null, eventDateTo: null, saleStartDateFrom: null, saleStartDateTo: null,
saleEndDateFrom: null, saleEndDateTo: null, cancelDeadlineDateFrom: null, cancelDeadlineDateTo: null,
runningMinutes: null, ageLimit: null, totalSeats: null, availableSeats: null, status: null
};
loadEventList(0);
};

window.openAreaMenu = function (eventId) {
if (typeof window.switchMenuWithContext === 'function') {
window.switchMenuWithContext('area', { eventId });
return;
}
window.location.href = `/admin/api/v1/view/home?menu=area&eventId=${eventId}`;
};

function clearLayoutSvg() {
const svg = document.getElementById('layout-preview-svg');
if (svg) svg.innerHTML = '';
return svg;
}

function formatSeatStatus(status) {
if (status === 'AVAILABLE') return '판매 가능';
if (status === 'LOCKED') return '결제 진행';
if (status === 'RESERVED') return '판매 완료';
return status || '-';
}

function closeLayoutSeatDetail() {
const body = document.getElementById('layout-preview-body');
const panel = document.getElementById('layout-seat-detail-panel');
body?.classList.remove('has-seat-detail');
panel?.classList.remove('is-open');
document.querySelectorAll('.layout-seat.is-selected').forEach(el => el.classList.remove('is-selected'));
currentLayoutSelectedSeatId = null;
}
window.closeLayoutSeatDetail = closeLayoutSeatDetail;

function showLayoutSeatDetail(seat) {
if (!seat) return;
const body = document.getElementById('layout-preview-body');
const panel = document.getElementById('layout-seat-detail-panel');
body?.classList.add('has-seat-detail');
panel?.classList.add('is-open');

const seatName = seat.seatName || `${seat.seatRow || '-'}행 ${seat.seatCol || '-'}번`;
const price = seat.price != null ? `${Number(seat.price).toLocaleString()}원` : '-';
const x = seat.positionX ?? ((seat.seatCol || 1) - 1) * 18 + 80;
const y = seat.positionY ?? ((seat.seatRow || 1) - 1) * 18 + 80;
const width = seat.seatWidth ?? 14;
const height = seat.seatHeight ?? 14;

document.getElementById('layout-seat-detail-title').textContent = seatName;
document.getElementById('layout-seat-detail-status').textContent = formatSeatStatus(seat.status);
document.getElementById('layout-seat-detail-zone').textContent = seat.zone || currentLayoutAreaName || '-';
document.getElementById('layout-seat-detail-grade').textContent = seat.grade || '-';
document.getElementById('layout-seat-detail-row-col').textContent = `${seat.seatRow || '-'}행 / ${seat.seatCol || '-'}열`;
document.getElementById('layout-seat-detail-price').textContent = price;
document.getElementById('layout-seat-detail-position').textContent = `X ${Number(x).toFixed(1)}, Y ${Number(y).toFixed(1)}`;
document.getElementById('layout-seat-detail-size').textContent = `${Number(width).toFixed(1)} x ${Number(height).toFixed(1)}`;

document.querySelectorAll('.layout-seat.is-selected').forEach(el => el.classList.remove('is-selected'));
const selected = document.querySelector(`.layout-seat[data-seat-id="${seat.seatId}"]`);
selected?.classList.add('is-selected');
currentLayoutSelectedSeatId = seat.seatId || null;
}

function applyLayoutViewBox() {
const svg = document.getElementById('layout-preview-svg');
if (!svg) return;
svg.setAttribute('viewBox', `${layoutViewBox.x} ${layoutViewBox.y} ${layoutViewBox.width} ${layoutViewBox.height}`);
const label = document.getElementById('layout-zoom-label');
if (label) label.textContent = `${Math.round(layoutZoom * 100)}%`;
}

function setLayoutBaseViewBox(x, y, width, height) {
layoutDefaultViewBox.x = x;
layoutDefaultViewBox.y = y;
layoutDefaultViewBox.width = width;
layoutDefaultViewBox.height = height;
layoutViewBox = { ...layoutDefaultViewBox };
layoutZoom = 1;
applyLayoutViewBox();
}

window.resetLayoutZoom = function () {
layoutViewBox = { ...layoutDefaultViewBox };
layoutZoom = 1;
applyLayoutViewBox();
};

window.zoomLayoutPreview = function (factor, centerX = null, centerY = null) {
const nextZoom = Math.min(Math.max(layoutZoom * factor, 0.5), 5);
if (nextZoom === layoutZoom) return;

const actualFactor = nextZoom / layoutZoom;
const cx = centerX ?? (layoutViewBox.x + layoutViewBox.width / 2);
const cy = centerY ?? (layoutViewBox.y + layoutViewBox.height / 2);
const nextWidth = layoutViewBox.width / actualFactor;
const nextHeight = layoutViewBox.height / actualFactor;

layoutViewBox = {
x: cx - (cx - layoutViewBox.x) / actualFactor,
y: cy - (cy - layoutViewBox.y) / actualFactor,
width: nextWidth,
height: nextHeight
};
layoutZoom = nextZoom;
applyLayoutViewBox();
};

function bindLayoutWheelZoom() {
const svg = document.getElementById('layout-preview-svg');
if (!svg || svg.dataset.zoomBound === 'true') return;

svg.addEventListener('wheel', function (event) {
event.preventDefault();
const rect = svg.getBoundingClientRect();
const ratioX = (event.clientX - rect.left) / rect.width;
const ratioY = (event.clientY - rect.top) / rect.height;
const centerX = layoutViewBox.x + layoutViewBox.width * ratioX;
const centerY = layoutViewBox.y + layoutViewBox.height * ratioY;
zoomLayoutPreview(event.deltaY < 0 ? 1.12 : 0.892857, centerX, centerY);
}, { passive: false });

svg.addEventListener('pointerdown', function (event) {
if (event.button !== 0) return;
svg.setPointerCapture(event.pointerId);
const areaEl = event.target.closest ? event.target.closest('.layout-area, .click-area, [data-area-name]') : null;
const seatEl = event.target.closest ? event.target.closest('.layout-seat') : null;
const areaName = areaEl ? areaEl.dataset.areaName : null;
const areaDisplayName = areaEl ? (areaEl.dataset.areaDisplayName || areaName) : null;
const matchedArea = areaEl && !areaEl.dataset.areaId ? findLayoutAreaByName(areaName) : null;
layoutDragged = false;
layoutDragState = {
pointerId: event.pointerId,
startClientX: event.clientX,
startClientY: event.clientY,
startViewBoxX: layoutViewBox.x,
startViewBoxY: layoutViewBox.y,
areaId: areaEl ? (areaEl.dataset.areaId || matchedArea?.areaId || null) : null,
areaName: areaEl ? (areaDisplayName || matchedArea?.areaName || null) : null,
seatId: seatEl ? seatEl.dataset.seatId : null
};
svg.classList.add('is-dragging');
});

svg.addEventListener('pointermove', function (event) {
if (!layoutDragState || layoutDragState.pointerId !== event.pointerId) return;
event.preventDefault();

const rect = svg.getBoundingClientRect();
const dx = event.clientX - layoutDragState.startClientX;
const dy = event.clientY - layoutDragState.startClientY;
if (Math.abs(dx) > 3 || Math.abs(dy) > 3) layoutDragged = true;

layoutViewBox = {
...layoutViewBox,
x: layoutDragState.startViewBoxX - dx * (layoutViewBox.width / rect.width),
y: layoutDragState.startViewBoxY - dy * (layoutViewBox.height / rect.height)
};
applyLayoutViewBox();
});

function endLayoutDrag(event) {
if (!layoutDragState || layoutDragState.pointerId !== event.pointerId) return;
const clickedAreaId = layoutDragState.areaId;
const clickedAreaName = layoutDragState.areaName;
const clickedSeatId = layoutDragState.seatId;
const clickedSeat = clickedSeatId ? layoutSeatMap.get(String(clickedSeatId)) : null;
const shouldOpenArea = !layoutDragged && clickedAreaId;
const shouldOpenSeat = !layoutDragged && clickedSeat;
if (svg.hasPointerCapture(event.pointerId)) svg.releasePointerCapture(event.pointerId);
layoutDragState = null;
svg.classList.remove('is-dragging');
if (shouldOpenSeat) showLayoutSeatDetail(clickedSeat);
else if (shouldOpenArea) openSeatLayoutPreview(clickedAreaId, clickedAreaName);
setTimeout(() => {
layoutDragged = false;
}, 80);
}

svg.addEventListener('pointerup', endLayoutDrag);
svg.addEventListener('pointercancel', endLayoutDrag);
svg.addEventListener('lostpointercapture', function () {
layoutDragState = null;
svg.classList.remove('is-dragging');
});

svg.dataset.zoomBound = 'true';
}

function svgEl(tag, attrs = {}) {
const el = document.createElementNS('http://www.w3.org/2000/svg', tag);
Object.entries(attrs).forEach(([key, value]) => el.setAttribute(key, value));
return el;
}

function findLayoutAreaByName(areaName) {
const normalizedName = String(areaName ?? '').trim();
if (!normalizedName) return null;
return currentLayoutAreas.find(area => String(area.areaName ?? '').trim() === normalizedName) || null;
}

function getSvgAreaLabel(areaElement) {
const nextElement = areaElement?.nextElementSibling;
if (nextElement && nextElement.tagName?.toLowerCase() === 'text') {
const label = nextElement.textContent?.trim();
if (label) return label;
}

const parentElement = areaElement?.parentElement;
if (parentElement && parentElement.tagName?.toLowerCase() === 'g') {
const labelElement = parentElement.querySelector('text');
const label = labelElement?.textContent?.trim();
if (label) return label;
}

return areaElement?.dataset?.areaName || '';
}

function formatLayoutAreaDisplayName(area, svgLabel) {
const grade = String(area?.grade || '').trim();
const label = String(svgLabel || area?.areaName || '').trim();
if (grade && label) return `${grade}등급 ${label}구역`;
return label || area?.areaName || '';
}

async function fetchAreaLayout(eventId) {
if (areaLayoutCache.has(eventId)) return areaLayoutCache.get(eventId);

const res = await Fetch(`${AREA_URL}/select`, {
method: 'POST',
headers,
body: JSON.stringify({ eventId, page: 0, size: 1000, sort: ['areaId-asc'] })
});

if (!res.ok) throw new Error('Area layout load failed');
const paged = await res.json();
const areas = paged.content || [];
areaLayoutCache.set(eventId, areas);
return areas;
}

async function fetchEventLayout(eventId) {
if (eventLayoutCache.has(eventId)) return eventLayoutCache.get(eventId);

const res = await Fetch(`${AREA_URL}/layout/event/${eventId}`, {
method: 'GET',
headers
});

if (res.status === 204 || res.status === 404) {
eventLayoutCache.set(eventId, null);
return null;
}
if (!res.ok) throw new Error('Event layout load failed');

const text = await res.text();
if (!text) {
eventLayoutCache.set(eventId, null);
return null;
}

const layout = JSON.parse(text);
eventLayoutCache.set(eventId, layout);
return layout;
}

async function fetchSeatLayout(areaId) {
if (seatLayoutCache.has(areaId)) return seatLayoutCache.get(areaId);

const res = await Fetch(`${SEAT_URL}/select`, {
method: 'POST',
headers,
body: JSON.stringify({ areaId, page: 0, size: 5000, sort: ['seatRow-asc', 'seatCol-asc'] })
});

if (!res.ok) throw new Error('Seat layout load failed');
const paged = await res.json();
const seats = paged.content || [];
seatLayoutCache.set(areaId, seats);
return seats;
}

function parseViewBox(viewBox) {
const values = String(viewBox || '').trim().split(/\s+/).map(Number);
if (values.length === 4 && values.every(value => !Number.isNaN(value))) {
return { x: values[0], y: values[1], width: values[2], height: values[3] };
}
return { x: 0, y: 0, width: 700, height: 520 };
}

function renderOriginalSvgLayout(layout, areas) {
const svg = clearLayoutSvg();
if (!svg) return false;
layoutSeatMap.clear();
currentLayoutAreas = areas || [];
currentLayoutMode = 'AREA';
currentLayoutAreaId = null;
currentLayoutAreaName = '';
closeLayoutSeatDetail();

const parser = new DOMParser();
const doc = parser.parseFromString(layout?.svgText || '', 'image/svg+xml');
const sourceSvg = doc.documentElement;
if (!sourceSvg || sourceSvg.tagName.toLowerCase() !== 'svg' || doc.querySelector('parsererror')) {
return false;
}

svg.innerHTML = sourceSvg.innerHTML;
const viewBox = parseViewBox(sourceSvg.getAttribute('viewBox'));
setLayoutBaseViewBox(viewBox.x, viewBox.y, viewBox.width, viewBox.height);
bindLayoutWheelZoom();

document.getElementById('layout-back-btn').style.display = 'none';
document.getElementById('layout-preview-mode-label').textContent = '원본 SVG 배치도';
document.getElementById('layout-preview-count').textContent = `${areas.length}개 구역`;

svg.querySelectorAll('.area, [data-area-name]').forEach(el => {
const areaName = el.dataset.areaName || '';
const matchedArea = findLayoutAreaByName(areaName);
if (!matchedArea) return;
const areaDisplayName = formatLayoutAreaDisplayName(matchedArea, getSvgAreaLabel(el));
el.classList.add('layout-area');
el.dataset.areaId = matchedArea.areaId;
el.dataset.areaName = matchedArea.areaName;
el.dataset.areaDisplayName = areaDisplayName;
if (!el.querySelector('title')) {
const title = document.createElementNS('http://www.w3.org/2000/svg', 'title');
title.textContent = `${areaDisplayName || matchedArea.areaName || matchedArea.areaId} / ${matchedArea.price != null ? Number(matchedArea.price).toLocaleString() + '원' : '-'}`;
el.appendChild(title);
}
});

return true;
}

function seatStatusClass(status) {
if (status === 'RESERVED') return 'layout-seat-reserved';
if (status === 'LOCKED') return 'layout-seat-locked';
return 'layout-seat-available';
}

function renderSeatLayout(areaId, areaName, seats) {
const svg = clearLayoutSvg();
if (!svg) return;
layoutSeatMap.clear();
setLayoutBaseViewBox(0, 0, 700, 520);
bindLayoutWheelZoom();
currentLayoutMode = 'SEAT';
currentLayoutAreaId = areaId;
currentLayoutAreaName = areaName || '';
closeLayoutSeatDetail();

document.getElementById('layout-back-btn').style.display = 'inline-flex';
document.getElementById('layout-preview-mode-label').textContent = `${areaName || '구역'} 좌석 배치도`;
document.getElementById('layout-preview-count').textContent = `${seats.length}석`;

if (seats.length === 0) {
const empty = svgEl('text', { x: 350, y: 260, class: 'layout-empty-text', 'text-anchor': 'middle' });
empty.textContent = '등록된 좌석 정보가 없습니다.';
svg.appendChild(empty);
return;
}

seats.forEach(seat => {
const seatKey = String(seat.seatId ?? `${seat.seatRow || '-'}-${seat.seatCol || '-'}`);
layoutSeatMap.set(seatKey, seat);
const x = seat.positionX ?? ((seat.seatCol || 1) - 1) * 18 + 80;
const y = seat.positionY ?? ((seat.seatRow || 1) - 1) * 18 + 80;
const width = seat.seatWidth ?? 14;
const height = seat.seatHeight ?? 14;
const rotation = seat.rotation ?? 0;
const cx = x + width / 2;
const cy = y + height / 2;
const rect = svgEl('rect', {
x, y, width, height, rx: 2,
class: `layout-seat ${seatStatusClass(seat.status)}`,
transform: `rotate(${rotation} ${cx} ${cy})`,
'data-seat-id': seatKey
});
const title = svgEl('title');
const seatName = seat.seatName || `${seat.seatRow || '-'}행 ${seat.seatCol || '-'}번`;
title.textContent = `${seatName} / ${seat.status || '-'} / ${seat.price != null ? Number(seat.price).toLocaleString() + '원' : '-'}`;
rect.appendChild(title);
rect.addEventListener('click', function (event) {
event.stopPropagation();
showLayoutSeatDetail(seat);
});
svg.appendChild(rect);
});
}

window.openLayoutPreview = async function (eventId) {
const ev = currentEventList.find(item => item.eventId === parseInt(eventId, 10));
currentLayoutEventId = parseInt(eventId, 10);
currentLayoutEventTitle = ev?.title || `Event ${eventId}`;
document.getElementById('layout-preview-title').textContent = `${currentLayoutEventTitle} 배치도`;
document.getElementById('layout-preview-subtitle').textContent = '구역을 클릭하면 해당 구역의 좌석 배치도를 확인할 수 있습니다.';
document.getElementById('layout-preview-modal').style.display = 'flex';

try {
const areas = await fetchAreaLayout(currentLayoutEventId);
const layout = await fetchEventLayout(currentLayoutEventId);
if (!layout || !renderOriginalSvgLayout(layout, areas)) {
throw new Error('Event original SVG layout not found');
}
} catch {
showToast('구역 배치도 조회에 실패했습니다.', true);
}
};

window.openSeatLayoutPreview = async function (areaId, areaName) {
try {
const seats = await fetchSeatLayout(areaId);
renderSeatLayout(areaId, areaName, seats);
} catch {
showToast('좌석 배치도 조회에 실패했습니다.', true);
}
};

window.refreshLayoutPreview = async function () {
if (!currentLayoutEventId) return;

try {
if (currentLayoutMode === 'SEAT' && currentLayoutAreaId) {
seatLayoutCache.delete(currentLayoutAreaId);
const seats = await fetchSeatLayout(currentLayoutAreaId);
renderSeatLayout(currentLayoutAreaId, currentLayoutAreaName, seats);
showToast('좌석 배치도를 최신 정보로 업데이트했습니다.');
return;
}

areaLayoutCache.delete(currentLayoutEventId);
eventLayoutCache.delete(currentLayoutEventId);
const areas = await fetchAreaLayout(currentLayoutEventId);
const layout = await fetchEventLayout(currentLayoutEventId);
if (!layout || !renderOriginalSvgLayout(layout, areas)) {
throw new Error('Event original SVG layout not found');
}
showToast('구역 배치도를 최신 정보로 업데이트했습니다.');
} catch {
showToast('배치도 새로고침에 실패했습니다.', true);
}
};

window.showAreaLayoutFromSeat = async function () {
if (!currentLayoutEventId) return;
try {
const areas = await fetchAreaLayout(currentLayoutEventId);
const layout = await fetchEventLayout(currentLayoutEventId);
if (!layout || !renderOriginalSvgLayout(layout, areas)) {
throw new Error('Event original SVG layout not found');
}
} catch {
showToast('구역 배치도 조회에 실패했습니다.', true);
}
};

window.closeLayoutPreviewModal = function () {
closeLayoutSeatDetail();
document.getElementById('layout-preview-modal').style.display = 'none';
};

function readDateSearch(prefix) {
const mode = document.getElementById(`${prefix}-mode`)?.value || 'date';
const from = document.getElementById(`${prefix}-from`)?.value || null;
const to = document.getElementById(`${prefix}-to`)?.value || null;

if (mode === 'range') {
return { single: null, from, to };
}

return { single: from, from: null, to: null };
}

function syncDateSearchMode(prefix) {
const mode = document.getElementById(`${prefix}-mode`)?.value || 'date';
const control = document.getElementById(`${prefix}-control`);
const toInput = document.getElementById(`${prefix}-to`);

control?.classList.toggle('date-mode', mode !== 'range');
if (toInput && mode !== 'range') {
toInput.value = '';
}
}

window.resetDetailedSearchForm = function () {
document.querySelectorAll('#search-modal input, #search-modal textarea').forEach(el => {
el.value = '';
});

document.querySelectorAll('#search-modal select').forEach(el => {
el.selectedIndex = 0;
});

['cond-eventDate', 'cond-saleStartDate', 'cond-saleEndDate', 'cond-cancelDeadlineDate'].forEach(prefix => {
syncDateSearchMode(prefix);
});
};

window.submitDetailedSearch = function () {
const eventIdRaw = document.getElementById('cond-eventId').value.trim();
const eventDateSearch = readDateSearch('cond-eventDate');
const saleStartDateSearch = readDateSearch('cond-saleStartDate');
const saleEndDateSearch = readDateSearch('cond-saleEndDate');
const cancelDeadlineDateSearch = readDateSearch('cond-cancelDeadlineDate');
currentSearchFilters = {
eventId:    eventIdRaw ? parseInt(eventIdRaw, 10) : null,
title:      document.getElementById('cond-title').value.trim()      || null,
artistName: document.getElementById('cond-artistName').value.trim() || null,
venue:      document.getElementById('cond-venue').value.trim()      || null,
venueAddress: document.getElementById('cond-venueAddress').value.trim() || null,
posterUrl: document.getElementById('cond-posterUrl').value.trim() || null,
eventDate: eventDateSearch.single,
eventDateFrom: eventDateSearch.from,
eventDateTo: eventDateSearch.to,
saleStartDate: saleStartDateSearch.single,
saleStartDateFrom: saleStartDateSearch.from,
saleStartDateTo: saleStartDateSearch.to,
saleEndDate: saleEndDateSearch.single,
saleEndDateFrom: saleEndDateSearch.from,
saleEndDateTo: saleEndDateSearch.to,
cancelDeadlineDate: cancelDeadlineDateSearch.single,
cancelDeadlineDateFrom: cancelDeadlineDateSearch.from,
cancelDeadlineDateTo: cancelDeadlineDateSearch.to,
runningMinutes: nullableNumber('cond-runningMinutes'),
ageLimit: nullableNumber('cond-ageLimit'),
totalSeats: nullableNumber('cond-totalSeats'),
availableSeats: nullableNumber('cond-availableSeats'),
status:     document.getElementById('cond-status').value            || null,
};
loadEventList(0);
closeSearchModal();
};

/* Init */
window.Pagination.register({
load: loadEventList,
getTotalPages: function () { return serverTotalPages; }
});
document.getElementById('m-total-seats')?.addEventListener('input', function () {
formatDigitInput(this);
});
document.getElementById('m-available-seats')?.addEventListener('input', function () {
formatDigitInput(this);
});
document.getElementById('m-poster-image')?.addEventListener('change', _setPosterFileName);
document.getElementById('poster-preview-file')?.addEventListener('change', handlePosterPreviewFileChange);
['cond-eventDate', 'cond-saleStartDate', 'cond-saleEndDate', 'cond-cancelDeadlineDate'].forEach(prefix => {
syncDateSearchMode(prefix);
document.getElementById(`${prefix}-mode`)?.addEventListener('change', function () {
syncDateSearchMode(prefix);
});
});
loadEventList(0);
})();
