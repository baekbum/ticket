(function () {
const API = { VERSION: 'v1', LOCAL_PORT: '8999', DEV_PORT: '8080' };
const BASE_URL  = window.location.port === API.LOCAL_PORT ? `http://localhost:${API.LOCAL_PORT}/admin` : '';
const TICKET_PUBLIC_BASE_URL = window.location.port === API.LOCAL_PORT ? 'http://localhost:8082' : '';
const EVENT_URL = `${BASE_URL}/api/${API.VERSION}/event`;
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

/* ─────────────────── 다중 선택 ─────────────────── */
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
let successCount = 0, failCount = 0;

for (const id of ids) {
try {
const res = await Fetch(`${EVENT_URL}/delete/id/${id}`, { method: 'DELETE', headers });
if (res.ok) successCount++;
else        failCount++;
} catch { failCount++; }
}

closeBulkDeleteConfirmModal();
selectedIds.clear();
updateBulkBar();

if (failCount === 0) showToast(`${successCount}건의 이벤트가 삭제되었습니다.`);
else                 showToast(`${successCount}건 삭제 완료, ${failCount}건 실패.`, true);

loadEventList(parseInt(document.getElementById('pagination-current').value, 10) - 1);
};

/* ─────────────────── 날짜 포맷 ─────────────────── */
function formatToDatetimeLocal(str) {
if (!str) return '';
try {
if (str.includes('년')) {
const m = str.match(/\d+/g);
if (m && m.length >= 4) {
return `${m[0]}-${m[1].padStart(2,'0')}-${m[2].padStart(2,'0')}T${m[3].padStart(2,'0')}:${(m[4]||'00').padStart(2,'0')}`;
}
}
return str.replace(' ', 'T').slice(0, 16);
} catch { return ''; }
}

/* ─────────────────── 목록 로드 ─────────────────── */
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
if (!res.ok) { showToast('공연 목록 조회 실패', true); return; }

const pagedModel = await res.json();
currentEventList = pagedModel.content || [];
serverTotalPages = Math.max(pagedModel.page?.totalPages || pagedModel.totalPages || 1, 1);
const totalCount = pagedModel.totalElements ?? pagedModel.page?.totalElements ?? currentEventList.length;

document.getElementById('pagination-total').textContent = serverTotalPages;
document.getElementById('pagination-current').value      = pageZeroIndexed + 1;
document.getElementById('pagination-total-count').textContent = totalCount;

// 페이지 전환 시 선택 초기화
selectedIds.clear();
updateBulkBar();
const master = document.getElementById('select-all-checkbox');
if (master) master.checked = false;

const tbody = document.getElementById('event-table-body');
tbody.innerHTML = '';

currentEventList.forEach((ev, index) => {
let statusHtml;
if      (ev.status === 'ON_SALE')  statusHtml = `<span class="badge badge-sale">판매중</span>`;
else if (ev.status === 'SOLD_OUT') statusHtml = `<span class="badge badge-soldout">매진</span>`;
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
<td>${ev.posterUrl ? `<img class="event-poster-thumb" src="${resolvePosterUrl(ev.posterUrl)}" alt="">` : ''}</td>
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
  <button class="btn btn-sm btn-seat"    onclick="event.stopPropagation(); window.openSeatModal(${ev.eventId}, '${ev.title.replace(/'/g,"\\'")}', '${ev.artistName.replace(/'/g,"\\'")}')"><i class="ti ti-armchair"></i>좌석</button>
</td>
`;

tr.onclick = () => window.openModalForView(ev.eventId);
tbody.appendChild(tr);
});

syncSortHeaderUI();
} catch (e) { showToast('서버 통신 오류', true); }
};

/* ─────────────────── 정렬 ─────────────────── */
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
if      (dir === 'asc')  { th.classList.add('asc');  icon.textContent = '▲'; }
else if (dir === 'desc') { th.classList.add('desc'); icon.textContent = '▼'; }
else                     icon.textContent = '↕';
});
}

/* ─────────────────── 모달 공통 바인딩 ─────────────────── */
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

/* ─────────────────── 모달: VIEW ─────────────────── */
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

/* ─────────────────── 모달: UPDATE ─────────────────── */
window.openModalForUpdate = function (eventId) {
const ev = currentEventList.find(e => e.eventId === parseInt(eventId, 10));
if (!ev) { showToast('공연 데이터를 찾을 수 없습니다.', true); return; }

document.getElementById('m-modal-mode').value       = 'UPDATE';
document.getElementById('modal-title').textContent    = '공연 스펙 정보 수정';
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

/* ─────────────────── 모달: CREATE ─────────────────── */
window.openModalForCreate = function () {
document.getElementById('m-modal-mode').value       = 'CREATE';
document.getElementById('modal-title').textContent    = '신규 라이브 공연 등록';
document.getElementById('modal-subtitle').textContent = '새로운 공연 일정을 등록합니다.';

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

document.getElementById('btn-modal-submit').textContent       = '신규 오픈 등록하기';
document.getElementById('btn-modal-submit').style.display     = 'block';

const cancelBtn = document.querySelector('#event-modal .btn-secondary');
if (cancelBtn) { cancelBtn.textContent = '취소'; cancelBtn.style.width = 'auto'; }

document.getElementById('event-modal').style.display = 'flex';
};

window.closeModal = function () { document.getElementById('event-modal').style.display = 'none'; };

/* ─────────────────── Form 제출 ─────────────────── */
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
if (!artistVal) missingFields.push('아티스트');
if (!titleVal) missingFields.push('공연 타이틀');
if (!venueVal) missingFields.push('개최 장소');
if (!venueAddressVal) missingFields.push('공연장 주소');
if (!datetimeInput) missingFields.push('공연 일정');
if (!saleStartInput) missingFields.push('판매 시작');
if (!saleEndInput) missingFields.push('판매 종료');
if (!cancelDeadlineInput) missingFields.push('취소 마감');
if (mode === 'CREATE' && !posterFile) missingFields.push('포스터 이미지');

if (missingFields.length > 0) {
showToast(`필수 항목을 입력해 주세요: ${missingFields.join(', ')}`, true); return;
}

if (!artistVal || !titleVal || !venueVal || !venueAddressVal || !datetimeInput || !saleStartInput || !saleEndInput || !cancelDeadlineInput) {
showToast('필수 항목을 모두 입력해 주세요.', true); return;
}
if (!seatsVal || seatsVal <= 0) {
showToast('배정 좌석수는 1석 이상이어야 합니다.', true); return;
}
if (!runningMinutesVal || runningMinutesVal <= 0 || Number.isNaN(ageLimitVal) || ageLimitVal < 0) {
showToast('공연 시간과 관람 연령을 확인해 주세요.', true); return;
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
} catch { showToast('통신 장애', true); }
};

/* ─────────────────── 단건 삭제 ─────────────────── */
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
} catch { showToast('통신 장애', true); }
};

/* ─────────────────── Redis 캐시 웜업 ─────────────────── */
window.openCacheWarmupConfirmModal  = function () { document.getElementById('cache-warmup-modal').style.display = 'flex'; };
window.closeCacheWarmupConfirmModal = function () { document.getElementById('cache-warmup-modal').style.display = 'none'; };

window.submitCacheWarmup = async function () {
const targetId = document.getElementById('m-target-id').value;
try {
const res = await Fetch(`${SEAT_URL}/warm-up/${targetId}`, { method: 'POST', headers });
if (res.ok) { showToast('Redis 캐시 웜업이 성공적으로 완료되었습니다! ⚡'); closeCacheWarmupConfirmModal(); }
else         { showToast('웜업 실패: 백엔드 처리 중 오류가 발생했습니다.', true); }
} catch { showToast('서버 네트워크 연결 장애 발생', true); }
};

/* ─────────────────── 검색 ─────────────────── */
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

/* ─────────────────── 초기화 ─────────────────── */
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
['cond-eventDate', 'cond-saleStartDate', 'cond-saleEndDate', 'cond-cancelDeadlineDate'].forEach(prefix => {
syncDateSearchMode(prefix);
document.getElementById(`${prefix}-mode`)?.addEventListener('change', function () {
syncDateSearchMode(prefix);
});
});
loadEventList(0);
})();
