(function () {
  const API_VER = API.VERSION || 'v1';
  const ADMIN_BASE = `${base()}/admin/api/${API_VER}`;
  const AREA_API = `${ADMIN_BASE}/area`;
  const SEAT_API = `${ADMIN_BASE}/seat`;

  let eventId = null;
  let areas = [];
  let selectedArea = null;
  let seats = [];
  let selectedSeats = new Map();

  function inputValue(id) {
    return document.getElementById(id)?.value.trim() || '';
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function normalizeLayoutKey(value) {
    return String(value || '')
      .trim()
      .replace(/^area-2f-/, '')
      .replace(/^area-1f-/, '')
      .replace(/^area-vip-/, '')
      .replace(/^area-floor-/, '')
      .replace(/^area-/, '');
  }

  function areaKeyFromElement(element) {
    return element.getAttribute('data-layout-key') || normalizeLayoutKey(element.id);
  }

  function areaLabel(area) {
    if (!area) return '-';
    return `${area.areaName || area.layoutKey || area.areaId} (${area.grade || '-'})`;
  }

  function isSeatAvailable(seat) {
    return String(seat.status || '').toUpperCase() === 'AVAILABLE';
  }

  function seatKey(seat) {
    return `${seat.zone}:${seat.seatRow}:${seat.seatCol}`;
  }

  function svgEl(tagName, attrs = {}) {
    const element = document.createElementNS('http://www.w3.org/2000/svg', tagName);
    Object.entries(attrs).forEach(([key, value]) => {
      if (value !== null && value !== undefined) {
        element.setAttribute(key, value);
      }
    });
    return element;
  }

  async function fetchAreas() {
    const res = await Fetch(`${AREA_API}/select`, {
      method: 'POST',
      body: {
        eventId,
        page: 0,
        size: 500,
        sort: ['areaId-asc']
      }
    });
    if (!res.ok) throw new Error('area select failed');
    const paged = await res.json();
    areas = paged.content || [];
  }

  async function fetchLayout() {
    const res = await Fetch(`${AREA_API}/layout/event/${encodeURIComponent(eventId)}`, { method: 'GET' });
    if (res.status === 204) return null;
    if (!res.ok) throw new Error('layout select failed');
    return res.json();
  }

  function renderLayout(svgText) {
    const wrap = document.getElementById('seat-test-layout');
    const seatSvg = document.getElementById('seat-test-seat-svg');
    selectedArea = null;
    selectedSeats.clear();
    renderSelectedSeats();
    document.getElementById('seat-test-area-label').textContent = '-';
    seatSvg.style.display = 'none';
    seatSvg.innerHTML = '';

    if (!svgText) {
      wrap.innerHTML = '<div style="color:var(--text-muted); padding:2rem; text-align:center;">등록된 SVG 배치도가 없습니다.</div>';
      return;
    }

    const parser = new DOMParser();
    const doc = parser.parseFromString(svgText, 'image/svg+xml');
    const svg = doc.querySelector('svg');
    if (!svg) {
      wrap.innerHTML = '<div style="color:var(--red); padding:2rem; text-align:center;">SVG 데이터를 렌더링할 수 없습니다.</div>';
      return;
    }

    const areaByLayoutKey = new Map();
    const areaByName = new Map();
    areas.forEach(area => {
      if (area.layoutKey) areaByLayoutKey.set(String(area.layoutKey), area);
      if (area.areaName) areaByName.set(String(area.areaName), area);
    });

    [...svg.querySelectorAll('path, rect')].forEach(element => {
      const key = areaKeyFromElement(element);
      const area = areaByLayoutKey.get(key) || areaByName.get(key);
      if (!area) return;

      element.classList.add('seat-test-area');
      element.setAttribute('data-area-id', area.areaId);
      element.setAttribute('tabindex', '0');
      element.addEventListener('click', () => selectArea(area));
      element.addEventListener('keydown', event => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          selectArea(area);
        }
      });

      const title = document.createElementNS('http://www.w3.org/2000/svg', 'title');
      title.textContent = areaLabel(area);
      element.appendChild(title);
    });

    wrap.innerHTML = '';
    wrap.appendChild(svg);
  }

  async function selectArea(area) {
    selectedArea = area;
    selectedSeats.clear();
    document.getElementById('seat-test-area-label').textContent = areaLabel(area);
    document.querySelectorAll('.seat-test-area').forEach(el => {
      el.classList.toggle('is-selected', String(el.getAttribute('data-area-id')) === String(area.areaId));
    });
    await loadSeats();
  }

  async function loadSeats() {
    if (!selectedArea) return;

    const res = await Fetch(`${SEAT_API}/test/select`, {
      method: 'POST',
      body: {
        eventId,
        areaId: selectedArea.areaId,
        page: 0,
        size: 10000,
        sort: ['seatRow-asc', 'seatCol-asc']
      }
    });
    if (!res.ok) {
      showToast('좌석 조회에 실패했습니다.', true);
      return;
    }

    const paged = await res.json();
    seats = paged.content || [];
    renderSeats();
  }

  function renderSeats() {
    const wrap = document.getElementById('seat-test-layout');
    const svg = document.getElementById('seat-test-seat-svg');
    wrap.innerHTML = '';
    svg.style.display = 'block';
    svg.innerHTML = '';

    if (!seats.length) {
      const empty = svgEl('text', { x: 350, y: 260, class: 'seat-test-seat-label', 'text-anchor': 'middle' });
      empty.textContent = '등록된 좌석이 없습니다.';
      svg.appendChild(empty);
      renderSelectedSeats();
      return;
    }

    seats
      .sort((a, b) => (a.seatRow - b.seatRow) || (a.seatCol - b.seatCol))
      .forEach(seat => {
        const key = seatKey(seat);
        const x = seat.positionX ?? ((seat.seatCol || 1) - 1) * 18 + 80;
        const y = seat.positionY ?? ((seat.seatRow || 1) - 1) * 18 + 80;
        const width = seat.seatWidth ?? 14;
        const height = seat.seatHeight ?? 14;
        const rotation = seat.rotation ?? 0;
        const cx = x + width / 2;
        const cy = y + height / 2;
        const status = String(seat.status || '').toUpperCase();
        const className = selectedSeats.has(key)
          ? 'seat-test-seat-selected'
          : status === 'RESERVED'
            ? 'seat-test-seat-reserved'
            : status === 'LOCKED'
              ? 'seat-test-seat-locked'
              : 'seat-test-seat-available';

        const rect = svgEl('rect', {
          x,
          y,
          width,
          height,
          rx: 2,
          class: className,
          transform: `rotate(${rotation} ${cx} ${cy})`,
          'data-seat-id': seat.seatId
        });
        const title = svgEl('title');
        title.textContent = `${seat.seatName || `${seat.seatRow}행 ${seat.seatCol}열`} / ${status} / ${seat.price != null ? Number(seat.price).toLocaleString() + '원' : '-'}`;
        rect.appendChild(title);

        if (isSeatAvailable(seat)) {
          rect.addEventListener('click', event => {
            event.stopPropagation();
            toggleSeat(seat);
          });
        }
        svg.appendChild(rect);
      });

    document.getElementById('seat-test-count-label').textContent = String(seats.length);
    renderSelectedSeats();
  }

  function toggleSeat(seat) {
    const key = seatKey(seat);
    if (selectedSeats.has(key)) {
      selectedSeats.delete(key);
    } else {
      selectedSeats.set(key, seat);
    }
    renderSeats();
  }

  function renderSelectedSeats() {
    const list = document.getElementById('seat-test-selected-list');
    const selected = [...selectedSeats.values()];
    if (!selected.length) {
      list.innerHTML = '<span style="color:var(--text-muted); font-size:12px;">선택된 좌석이 없습니다.</span>';
      return;
    }

    list.innerHTML = selected.map(seat => (
      `<span class="seat-test-chip">${escapeHtml(seat.zone)} ${seat.seatRow}행 ${seat.seatCol}열</span>`
    )).join('');
  }

  window.openSeatReservationTestPopup = async function () {
    const rawEventId = inputValue('seat-test-event-id');
    if (!rawEventId) {
      showToast('이벤트 ID를 입력해주세요.', true);
      return;
    }

    eventId = Number(rawEventId);
    document.getElementById('seat-test-empty').style.display = 'none';
    document.getElementById('seat-test-popup').classList.add('is-open');
    await window.refreshSeatTestAreas();
  };

  window.closeSeatReservationTestPopup = function () {
    document.getElementById('seat-test-popup').classList.remove('is-open');
    document.getElementById('seat-test-empty').style.display = 'flex';
  };

  window.refreshSeatTestAreas = async function () {
    if (!eventId) return;
    try {
      await fetchAreas();
      const layout = await fetchLayout();
      renderLayout(layout?.svgText || '');
    } catch (e) {
      console.error(e);
      showToast('구역 배치도 조회에 실패했습니다.', true);
    }
  };

  window.refreshSeatTestSeats = async function () {
    if (!selectedArea) {
      await window.refreshSeatTestAreas();
      return;
    }
    selectedSeats.clear();
    await loadSeats();
  };

  window.occupySeatTestSelection = async function () {
    const userId = inputValue('seat-test-user-id');
    const selected = [...selectedSeats.values()];
    if (!userId) {
      showToast('User ID를 입력해주세요.', true);
      return;
    }
    if (!selected.length) {
      showToast('선점할 좌석을 선택해주세요.', true);
      return;
    }

    try {
      const res = await Fetch(`${SEAT_API}/occupy`, {
        method: 'POST',
        body: {
          eventId,
          userId,
          maxTicketsPerPerson: 999,
          seats: selected.map(seat => ({
            id: seat.seatId,
            zone: seat.zone,
            row: seat.seatRow,
            col: seat.seatCol
          }))
        }
      });

      if (!res.ok) {
        showToast('이미 선택된 좌석입니다.', true);
        await loadSeats();
        return;
      }

      showToast('좌석 선점에 성공했습니다.');
      selectedSeats.clear();
      await loadSeats();
    } catch (e) {
      console.error(e);
      showToast('좌석 선점에 실패했습니다.', true);
    }
  };

  window.releaseAllSeatTestLocks = async function () {
    if (!eventId) {
      showToast('이벤트 ID를 입력해주세요.', true);
      return;
    }
    if (!window.confirm('이 이벤트의 Redis 좌석 선점을 모두 취소할까요?')) return;

    try {
      const res = await Fetch(`${SEAT_API}/cache/event/${encodeURIComponent(eventId)}/test-unlock`, {
        method: 'POST'
      });
      if (!res.ok) {
        showToast('좌석 전체 선점 취소에 실패했습니다.', true);
        return;
      }
      const message = await res.text();
      showToast(message || '좌석 전체 선점 취소가 완료되었습니다.');
      selectedSeats.clear();
      if (selectedArea) {
        await loadSeats();
      }
    } catch (e) {
      console.error(e);
      showToast('좌석 전체 선점 취소에 실패했습니다.', true);
    }
  };
})();
