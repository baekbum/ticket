import { useEffect, useRef, useState } from 'react';
import { adminFetch } from './api';
import PreflightIssueCarousel, { EventPoster } from './PreflightIssueCarousel';
import SeatCacheWarmupDialog from './SeatCacheWarmupDialog';
import './EventPreflight.css';

export type EventSchedule = {
  eventId: number;
  title: string;
  eventGroupCode?: string | null;
  eventDateTime: string | null;
  saleStartAt: string | null;
  posterUrl?: string | null;
};
type EventPage = { content: EventSchedule[]; page: { totalPages: number; totalElements: number } };
type Area = { areaId: number; areaName: string; layoutKey: string; status: string; seatCount: number };
type SeatReadiness = {
  totalSeats: number;
  unassignedSeats: number;
  areas: Area[];
  cache: { expectedKeys: number; readyKeys: number | null };
};
type Layout = { state: 'ready'; shapes: Element[] } | { state: 'missing' | 'unknown' | 'invalid' };
type Report = { seats: SeatReadiness; layout: Layout };

const number = (value: number) => value.toLocaleString('ko-KR');
const eventPageSize = 20;
const issueSampleSize = 10;
const dateTime = (value: string | null) => value?.replace(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2}).*$/, '$1 $2') || '미정';
const areaNameCollator = new Intl.Collator('en', { numeric: true, sensitivity: 'base' });

function compareAreas(left: Area, right: Area) {
  const leftName = left.areaName.trim();
  const rightName = right.areaName.trim();
  const groupOrder = Number(/^\d/.test(leftName)) - Number(/^\d/.test(rightName));
  return groupOrder || areaNameCollator.compare(leftName, rightName);
}

function normalizeLayoutKey(value: string) {
  return value.trim().replace(/^area-(?:2f-|1f-|vip-|floor-)?/, '');
}

async function readLayout(eventId: number, signal: AbortSignal): Promise<Layout> {
  try {
    const response = await adminFetch(`/ticket/api/v1/manage/area/layout/event/${eventId}`, { signal });
    if (response.status === 204) return { state: 'missing' };
    if (!response.ok) return { state: 'unknown' };
    const { svgText } = await response.json() as { svgText?: string };
    if (!svgText?.trim()) return { state: 'missing' };
    const root = new DOMParser().parseFromString(svgText, 'image/svg+xml').documentElement;
    if (root.localName !== 'svg' || root.namespaceURI !== 'http://www.w3.org/2000/svg') return { state: 'invalid' };
    // Inspect the sanitized SVG returned by the management API without mounting it.
    const shapes = Array.from(root.querySelectorAll('path, rect, polygon'))
      .filter(shape => shape.namespaceURI === root.namespaceURI && !shape.closest('defs, clipPath, mask'));
    return { state: 'ready', shapes };
  } catch {
    return { state: 'unknown' };
  }
}

function getLayoutLinks(areas: Area[], layout: Layout) {
  const byKey = new Map<string, number>();
  for (const area of areas) {
    byKey.set(normalizeLayoutKey(area.layoutKey || ''), area.areaId);
    byKey.set(normalizeLayoutKey(area.areaName), area.areaId);
  }

  const linked = new Set<number>();
  const unused = new Set<string>();
  if (layout.state === 'ready') {
    for (const shape of layout.shapes) {
      const explicit = shape.getAttribute('data-layout-key');
      const key = normalizeLayoutKey(explicit || normalizeLayoutKey(shape.id));
      if (!key) continue;
      const areaId = byKey.get(key);
      if (areaId !== undefined) linked.add(areaId);
      else if (explicit || shape.id.startsWith('area-')) unused.add(key);
    }
  }
  return { linked, unused };
}

async function loadEventPage(page: number, signal: AbortSignal, title = '', sort = ['eventId-desc']): Promise<EventPage> {
  const response = await adminFetch('/ticket/api/v1/manage/event/select', {
    method: 'POST', signal, headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ page, size: eventPageSize, sort, title: title || null }),
  });
  if (!response.ok) throw new Error('회차 목록 조회 실패');
  return response.json() as Promise<EventPage>;
}

async function loadPerformanceGroups(title: string, signal: AbortSignal) {
  const groups = new Map<string, EventSchedule[]>();
  const seen = new Set<number>();
  let totalPages = 1;
  for (let page = 0; page < totalPages; page++) {
    signal.throwIfAborted();
    const { content, page: metadata } = await loadEventPage(page, signal, title, ['eventDateTime-asc', 'eventId-asc']);
    totalPages = metadata.totalPages;
    for (const event of content) {
      if (seen.has(event.eventId)) continue;
      seen.add(event.eventId);
      const code = event.eventGroupCode?.trim();
      const key = code && code !== 'DEFAULT_EVENT_GROUP' ? `group:${code}` : `title:${event.title}`;
      const group = groups.get(key);
      if (group) group.push(event);
      else groups.set(key, [event]);
    }
  }
  return [...groups];
}

async function loadReport(eventId: number, signal: AbortSignal): Promise<Report> {
  const readSeats = async () => {
    const response = await adminFetch(`/ticket/api/v1/manage/seat/readiness/event/${eventId}`, { signal });
    if (!response.ok) throw new Error('좌석 점검 조회 실패');
    return response.json() as Promise<SeatReadiness>;
  };
  const [seats, layout] = await Promise.all([readSeats(), readLayout(eventId, signal)]);
  return { seats, layout };
}

function analyzeReport({ seats, layout }: Report) {
  const { linked, unused } = getLayoutLinks(seats.areas, layout);
  const empty = seats.areas.filter(area => area.seatCount === 0).length;
  const duplicateKeys = seats.totalSeats - seats.cache.expectedKeys;
  const missingKeys = seats.cache.readyKeys === null ? null : seats.cache.expectedKeys - seats.cache.readyKeys;
  const flagged = seats.totalSeats === 0 || seats.areas.length === 0 || empty > 0 || seats.unassignedSeats > 0
    || layout.state !== 'ready' || linked.size !== seats.areas.length || unused.size > 0
    || missingKeys !== 0 || duplicateKeys > 0;
  return { linked, unused, empty, duplicateKeys, missingKeys, flagged };
}

async function inspectRandomEvents(signal: AbortSignal) {
  const firstPage = await loadEventPage(0, signal);
  const total = firstPage.page.totalElements;
  const positions = new Set<number>();
  while (positions.size < Math.min(total, issueSampleSize)) {
    positions.add(Math.floor(Math.random() * total));
  }

  const pageNumbers = new Set([...positions].map(position => Math.floor(position / eventPageSize)));
  pageNumbers.delete(0);
  const pages = new Map([[0, firstPage.content]]);
  await Promise.all([...pageNumbers].map(async page => {
    pages.set(page, (await loadEventPage(page, signal)).content);
  }));
  const candidates = new Map<number, EventSchedule>();
  for (const position of positions) {
    const event = pages.get(Math.floor(position / eventPageSize))?.[position % eventPageSize];
    if (event) candidates.set(event.eventId, event);
  }

  const issues: EventSchedule[] = [];
  const events = [...candidates.values()];
  let unreadable = 0;
  for (let offset = 0; offset < events.length; offset += 3) {
    signal.throwIfAborted();
    const batch = events.slice(offset, offset + 3);
    const results = await Promise.allSettled(batch.map(event => loadReport(event.eventId, signal)));
    results.forEach((result, index) => {
      if (result.status === 'rejected') unreadable++;
      else if (analyzeReport(result.value).flagged) issues.push(batch[index]);
    });
  }
  return { issues, unreadable };
}

export default function EventPreflight({ initialEventId, onOpenAreas }: {
  initialEventId?: number;
  onOpenAreas: (eventId: number) => void;
}) {
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState<{ title: string } | null>(null);
  const [groups, setGroups] = useState<[string, EventSchedule[]][] | null>(null);
  const [listLoading, setListLoading] = useState(false);
  const [listError, setListError] = useState(false);
  const [inspection, setInspection] = useState<EventSchedule | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [report, setReport] = useState<Report | null>(null);
  const [warmupMessage, setWarmupMessage] = useState('');
  const [issueEvents, setIssueEvents] = useState<EventSchedule[]>([]);
  const [sampleRequest, setSampleRequest] = useState(0);
  const [sampleLoading, setSampleLoading] = useState(true);
  const [sampleNotice, setSampleNotice] = useState('');
  const dialogRef = useRef<HTMLDialogElement>(null);
  const showPopup = inspection !== null;

  function runInspection(event: EventSchedule) {
    setReport(null);
    setWarmupMessage('');
    setError(false);
    setLoading(true);
    setInspection(event);
  }

  function closePopup() {
    dialogRef.current?.close();
    setInspection(null);
    setReport(null);
    setError(false);
    setLoading(false);
  }

  useEffect(() => {
    const dialog = dialogRef.current;
    if (showPopup && dialog && !dialog.open) dialog.showModal();
  }, [showPopup]);

  useEffect(() => {
    if (!query?.title) return;
    const controller = new AbortController();
    const { signal } = controller;
    setListLoading(true);
    setListError(false);
    loadPerformanceGroups(query.title, signal).then(data => {
      if (!signal.aborted) setGroups(data);
    }).catch(() => { if (!signal.aborted) { setListError(true); setGroups(null); } })
      .finally(() => { if (!signal.aborted) setListLoading(false); });
    return () => controller.abort();
  }, [query]);

  useEffect(() => {
    if (!initialEventId || !Number.isSafeInteger(initialEventId) || initialEventId < 1) return;
    const controller = new AbortController();
    const { signal } = controller;
    adminFetch(`/ticket/api/v1/manage/event/select/id/${initialEventId}`, { signal })
      .then(async response => {
        if (!response.ok) return;
        const event = await response.json() as EventSchedule;
        if (signal.aborted || !event.title.trim()) return;
        setSearch(current => current || event.title);
        setQuery(current => current ?? { title: event.title });
      })
      .catch(() => {});
    return () => controller.abort();
  }, [initialEventId]);

  useEffect(() => {
    if (!inspection) return;
    const controller = new AbortController();
    const { signal } = controller;
    const event = inspection;
    loadReport(event.eventId, signal)
      .then(result => {
        if (signal.aborted) return;
        setReport(result);
        if (!analyzeReport(result).flagged) {
          setIssueEvents(previous => previous.filter(item => item.eventId !== event.eventId));
        }
      })
      .catch(() => { if (!signal.aborted) setError(true); })
      .finally(() => { if (!signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [inspection]);

  useEffect(() => {
    const controller = new AbortController();
    const { signal } = controller;
    inspectRandomEvents(signal)
      .then(({ issues, unreadable }) => {
        if (signal.aborted) return;
        setIssueEvents(issues);
        setSampleNotice(unreadable > 0
          ? `좌석 조회에 실패한 회차가 ${number(unreadable)}개 있습니다.`
          : '');
      })
      .catch(() => { if (!signal.aborted) setSampleNotice('점검 목록을 불러오지 못했습니다. 새로고침해 주세요.'); })
      .finally(() => { if (!signal.aborted) setSampleLoading(false); });
    return () => controller.abort();
  }, [sampleRequest]);

  return <section className="event-preflight" aria-labelledby="event-preflight-title">
    <header>
      <h1 id="event-preflight-title">공연 오픈 전 점검</h1>
      <p>회차별 좌석, 구역 배치도 연결, Redis 캐시 적재 상태를 확인합니다.</p>
    </header>
    <PreflightIssueCarousel events={issueEvents} selectedId={inspection?.eventId} onSelect={runInspection}
      loading={sampleLoading} notice={sampleNotice} onRefresh={() => {
        setSampleLoading(true);
        setSampleNotice('');
        setIssueEvents([]);
        setSampleRequest(request => request + 1);
      }} />
    <hr className="preflight-divider" />
    <form className="preflight-tools" onSubmit={event => { event.preventDefault(); setQuery({ title: search.trim() }); }}>
      <label>공연명 <input value={search} onChange={event => setSearch(event.target.value)} placeholder="공연명 검색" /></label>
      <button type="submit">검색</button>
    </form>
    {query?.title && <div className="preflight-search-results">
      {listLoading && <p role="status">공연 회차를 조회하는 중입니다…</p>}
      {!listLoading && listError && <p role="alert">회차 목록을 조회하지 못했습니다. 검색을 다시 시도해 주세요.</p>}
      {!listLoading && groups?.length === 0 && <p role="status">검색 결과가 없습니다.</p>}
      {!listLoading && groups?.map(([key, schedules]) => <article key={key} className="preflight-performance">
        <EventPoster url={schedules.find(event => event.posterUrl)?.posterUrl} />
        <div className="preflight-performance-schedules">
          <h2>{schedules[0].title}</h2>
          <ul className="preflight-session-list" aria-label="공연 회차">
            {schedules.map((event, index) => <li key={event.eventId}>
              <button type="button" aria-haspopup="dialog" onClick={() => runInspection(event)}>
                <span className="preflight-session-info">
                  <strong>{index + 1}회차 · {dateTime(event.eventDateTime)}</strong>
                  <small>예매 오픈 {dateTime(event.saleStartAt)}</small>
                </span>
                <span className="preflight-session-action">점검 <i className="ti ti-chevron-right" aria-hidden="true" /></span>
              </button>
            </li>)}
          </ul>
        </div>
      </article>)}
    </div>}
    <dialog ref={dialogRef} className="preflight-dialog" aria-labelledby="preflight-dialog-title" onCancel={event => {
      event.preventDefault();
      closePopup();
    }}>
      <div className="preflight-dialog-header">
        <h2 id="preflight-dialog-title">공연 점검 상세</h2>
        <button type="button" onClick={closePopup}>닫기</button>
      </div>
      {inspection && <div className="preflight-dialog-content">
        {loading && <p role="status">좌석과 배치도를 점검하는 중입니다…</p>}
        {error && <p role="alert">좌석 점검 결과를 조회하지 못했습니다. 다시 점검해 주세요.</p>}
        {warmupMessage && <p role="status" className="preflight-verdict">{warmupMessage}</p>}
        {report && <PreflightReport event={inspection} report={report} refreshing={loading} onWarmupComplete={message => {
          setWarmupMessage(message);
          setLoading(true);
          setError(false);
          setInspection({ ...inspection });
        }} onOpenAreas={eventId => {
          closePopup();
          onOpenAreas(eventId);
        }} />}
      </div>}
    </dialog>
  </section>;
}

function PreflightReport({ event, report: { seats, layout }, refreshing, onWarmupComplete, onOpenAreas }: {
  event: EventSchedule;
  report: Report;
  refreshing: boolean;
  onWarmupComplete: (message: string) => void;
  onOpenAreas: (eventId: number) => void;
}) {
  const [showGuide, setShowGuide] = useState(false);
  const [showWarmup, setShowWarmup] = useState(false);
  const { linked, unused, empty, duplicateKeys, missingKeys, flagged } = analyzeReport({ seats, layout });
  const layoutStatus = layout.state === 'missing' ? '미등록' : layout.state === 'invalid' ? 'SVG 확인 필요' : '조회 불가';
  const summary = [
    {
      label: '등록 좌석', value: `${number(seats.totalSeats)}석`,
      note: `구역 미지정 ${number(seats.unassignedSeats)}석`,
      meaning: '이 회차에 등록된 전체 좌석 수입니다. 구역 미지정은 그중 어느 구역에도 연결되지 않은 좌석 수입니다.',
    },
    {
      label: '빈 구역', value: `${number(empty)}개`, note: `전체 구역 ${number(seats.areas.length)}개`,
      meaning: '등록 좌석이 한 석도 없는 구역 수입니다. 전체 구역이 65개라면 0개일 때는 모든 구역에 좌석이 있고, 65개일 때는 모든 구역이 비어 있습니다.',
    },
    {
      label: '배치도 연결', value: layout.state === 'ready' ? `${number(linked.size)} / ${number(seats.areas.length)}개` : layoutStatus,
      note: layout.state === 'ready' ? '연결된 구역 / 전체 구역' : '회차에 등록한 SVG를 확인해 주세요.',
      meaning: '배치도 SVG에 연결된 구역 수 / 등록된 전체 구역 수입니다. 미등록, SVG 확인 필요, 조회 불가는 연결 수를 계산할 수 없는 상태입니다.',
    },
    {
      label: '좌석 캐시', value: seats.cache.readyKeys === null ? '조회 불가' : `${number(seats.cache.readyKeys)} / ${number(seats.cache.expectedKeys)}개`,
      warmup: true,
      note: missingKeys === null ? 'Redis 응답을 확인해 주세요.' : seats.totalSeats === 0 ? '점검할 좌석 없음' : `미적재 또는 상태 이상 ${number(missingKeys)}개`,
      meaning: 'Redis에 유효한 상태로 저장된 좌석 키 수 / 필요한 좌석 키 수입니다. 조회 불가는 Redis에서 키 상태를 확인할 수 없다는 뜻입니다.',
    },
  ];

  return <section className="preflight-report" aria-labelledby="preflight-report-title">
    <div className="preflight-report-header">
      <div>
        <h2 id="preflight-report-title">{event.title}</h2>
        <p>공연 {dateTime(event.eventDateTime)} · 예매 오픈 {dateTime(event.saleStartAt)} · 회차 #{event.eventId}</p>
      </div>
      <div className="preflight-report-actions">
        <button type="button" onClick={() => onOpenAreas(event.eventId)}>구역/좌석 관리</button>
        <button type="button" className="preflight-guide-toggle" aria-expanded={showGuide} aria-controls="preflight-summary-guide"
          onClick={() => setShowGuide(open => !open)}>
          <i className="ti ti-info-circle" aria-hidden="true" /> 참고
        </button>
      </div>
    </div>
    <div id="preflight-summary-guide" className="preflight-summary-guide" hidden={!showGuide}>
      <h3>점검 항목 안내</h3>
      <dl>{summary.map(({ label, value, meaning }) => <div key={label}>
        <dt>{label} <span>{value}</span></dt>
        <dd>{meaning}</dd>
      </div>)}</dl>
    </div>
    {!flagged && <p role="status" className="preflight-verdict">점검 항목이 모두 정상입니다.</p>}
    <div className="preflight-summary">
      {summary.map(({ label, value, note, warmup }) => warmup
        ? <button key={label} type="button" className="preflight-summary-card preflight-cache-card" aria-haspopup="dialog"
            disabled={refreshing} onClick={() => setShowWarmup(true)}>
            <span className="preflight-summary-label">{label} <span className="preflight-cache-action">캐시 적재 <i className="ti ti-chevron-right" aria-hidden="true" /></span></span>
            <strong className="preflight-summary-value">{value}</strong><small>{note}</small>
          </button>
        : <dl key={label} className="preflight-summary-card">
            <dt className="preflight-summary-label">{label}</dt>
            <dd className="preflight-summary-value">{value}</dd><dd className="preflight-summary-note">{note}</dd>
          </dl>)}
    </div>
    {showWarmup && <SeatCacheWarmupDialog eventId={event.eventId} onClose={() => setShowWarmup(false)} onComplete={onWarmupComplete} />}
    {duplicateKeys > 0 && <p className="preflight-warning">좌석 {number(duplicateKeys)}석이 다른 좌석과 같은 Redis 키를 사용합니다.</p>}
    {unused.size > 0 && <p className="preflight-warning">SVG에만 있는 구역 키 · {[...unused].join(', ')}</p>}
    <h3>구역별 결과</h3>
    {seats.areas.length === 0 ? <p>등록된 구역이 없습니다.</p> : <div className="preflight-table-wrap" tabIndex={0} role="region" aria-label="구역별 점검 결과">
      <table>
        <thead><tr><th scope="col">구역</th><th scope="col">상태</th><th scope="col">등록 좌석</th><th scope="col">SVG 연결</th></tr></thead>
        <tbody>{[...seats.areas].sort(compareAreas).map(area => <tr key={area.areaId}>
          <th scope="row">{area.areaName}</th><td>{area.status === 'ACTIVE' ? '활성' : '비활성'}</td>
          <td data-warning={area.seatCount === 0}>{number(area.seatCount)}석</td>
          <td data-warning={layout.state !== 'ready' || !linked.has(area.areaId)}>{layout.state !== 'ready' ? layoutStatus : linked.has(area.areaId) ? '연결됨' : '연결 안 됨'}</td>
        </tr>)}</tbody>
      </table>
    </div>}
  </section>;
}
