import { useEffect, useRef, useState, type FormEvent } from 'react';
import { adminFetch } from './api';
import AutoRefreshControls, { defaultRefreshSeconds } from './AutoRefreshControls';
import DateTimeInput, { isValidDateTime } from './DateTimeInput';
import './TicketingDetectionLogs.css';

type LogItem = {
  id: number;
  eventId: number;
  userId: string;
  detectionType: string;
  occurredAt: string;
};
type LogDetail = LogItem & {
  traceId: string | null;
  sessionKeyHash: string;
  evidence: Record<string, unknown>;
};
type LogPage = {
  content: LogItem[];
  page: { number: number; totalElements: number; totalPages: number };
};
type Search = {
  page: number;
  size: number;
  eventId?: number;
  userId?: string;
  detectionType?: string;
  occurredFrom?: string;
  occurredTo?: string;
};
type Load<T> = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; data: T };

const endpoint = '/audit/api/v1/ticketing-detection-log/select';
const emptyFilters = { eventId: '', userId: '', detectionType: '' };
const pageSizes = [5, 10, 20, 50, 100];
const datePresets = [
  { days: 1, label: '오늘' },
  { days: 3, label: '3일' },
  { days: 7, label: '1주일' },
  { days: 30, label: '1달' },
];
const dateFields = [
  { field: 'occurredFrom', label: '시작' },
  { field: 'occurredTo', label: '종료' },
] as const;
const seoulOffsetMs = 9 * 60 * 60 * 1000;
const detectionLabels: Record<string, string> = {
  AREA_LAYOUT_ACTIVE_TOKEN_MISSING: '배치도 요청에 Active Token 없음',
  AREA_LAYOUT_REFRESH_LIMIT_REACHED: '배치도 새로고침 한도 도달',
  SEAT_OCCUPATION_CAPTCHA_NOT_PASSED: '캡차 미통과 상태에서 좌석 선점 요청',
  PREPARE_TOO_FAST: '결제 준비 시간 기준 이하',
  CAPTCHA_ATTEMPT_LIMIT_REACHED: '캡차 오답 한도 도달',
};
const evidenceLabels: Record<string, { label: string; unit: string }> = {
  refreshCount: { label: '배치도 새로고침 횟수', unit: '회' },
  windowMs: { label: '집계 구간', unit: 'ms' },
  elapsedMs: { label: '결제 준비까지 걸린 시간', unit: 'ms' },
  limitMs: { label: '탐지 기준 시간', unit: 'ms' },
  attempts: { label: '캡차 오답 횟수', unit: '회' },
};
const dateTime = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul', dateStyle: 'medium', timeStyle: 'medium', hourCycle: 'h23',
});

function presetDates(days: number) {
  const end = new Date(Date.now() + seoulOffsetMs);
  const start = new Date(end);
  start.setUTCDate(end.getUTCDate() - (days - 1));
  return {
    occurredFrom: `${start.toISOString().slice(2, 10)} 00:00:00`,
    occurredTo: `${end.toISOString().slice(2, 10)} 23:59:59`,
  };
}

function dateBounds(from?: Date, to?: Date) {
  return {
    occurredFrom: from?.toISOString(),
    // 화면에서 선택한 종료 초에 속하는 PostgreSQL의 마이크로초 단위 로그까지 조회한다.
    occurredTo: to?.toISOString().replace(/\.\d{3}Z$/, '.999999Z'),
  };
}

function asSeoulDate(value: string) {
  if (!value) return undefined;
  return isValidDateTime(value) ? new Date(`20${value.replace(' ', 'T')}+09:00`) : new Date(NaN);
}

async function loadLogs(search: Search, signal: AbortSignal): Promise<Load<LogPage>> {
  try {
    const response = await adminFetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(search),
      signal,
    });
    if (!response.ok) return { status: 'error', message: `탐지 로그 목록을 불러오지 못했습니다. (HTTP ${response.status})` };
    const data: LogPage = await response.json();
    return { status: 'ready', data };
  } catch (error) {
    return { status: 'error', message: error instanceof Error && !(error instanceof TypeError)
      ? error.message : '탐지 로그 목록을 불러오지 못했습니다.' };
  }
}

function formatEvidence(value: unknown, unit?: string) {
  if (typeof value === 'number') return `${value.toLocaleString('ko-KR')}${unit ?? ''}`;
  if (typeof value === 'string') return value;
  return value == null ? '—' : JSON.stringify(value);
}

export default function TicketingDetectionLogs() {
  const [filters, setFilters] = useState(() => ({ ...emptyFilters, ...presetDates(1) }));
  const [preset, setPreset] = useState(1);
  const [search, setSearch] = useState<Search>(() => ({
    page: 0, size: 20, ...dateBounds(asSeoulDate(filters.occurredFrom), asSeoulDate(filters.occurredTo)),
  }));
  const [pageInput, setPageInput] = useState<string | null>(null);
  const [filterError, setFilterError] = useState('');
  const [list, setList] = useState<Load<LogPage>>({ status: 'loading' });
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [refreshSeconds, setRefreshSeconds] = useState(defaultRefreshSeconds);
  const [selection, setSelection] = useState<{ id: number } | null>(null);
  const [detail, setDetail] = useState<Load<LogDetail>>({ status: 'loading' });
  const dialogRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const controller = new AbortController();
    setList({ status: 'loading' });
    void loadLogs(search, controller.signal).then(next => {
      if (!controller.signal.aborted) setList(next);
    });
    return () => controller.abort();
  }, [search]);

  useEffect(() => {
    if (!autoRefresh || list.status === 'loading') return;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      void loadLogs(search, controller.signal).then(next => {
        if (!controller.signal.aborted) setList(next);
      });
    }, refreshSeconds * 1000);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [autoRefresh, refreshSeconds, search, list]);

  useEffect(() => {
    if (!selection) return;
    const controller = new AbortController();
    const { id } = selection;
    async function loadDetail() {
      try {
        const response = await adminFetch(`${endpoint}/id/${id}`, { signal: controller.signal });
        if (response.status === 404) throw new Error('해당 탐지 로그를 찾을 수 없습니다.');
        if (!response.ok) throw new Error(`탐지 로그 상세를 불러오지 못했습니다. (HTTP ${response.status})`);
        const data: LogDetail = await response.json();
        if (!controller.signal.aborted) setDetail({ status: 'ready', data });
      } catch (error) {
        if (!controller.signal.aborted) setDetail({ status: 'error', message: error instanceof Error && !(error instanceof TypeError)
          ? error.message : '탐지 로그 상세를 불러오지 못했습니다.' });
      }
    }
    void loadDetail();
    return () => controller.abort();
  }, [selection]);

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const eventId = filters.eventId ? Number(filters.eventId) : undefined;
    if (eventId !== undefined && (!Number.isSafeInteger(eventId) || eventId <= 0)) {
      setFilterError('공연 회차 ID에 올바른 양의 정수를 입력하세요.');
      return;
    }
    const from = asSeoulDate(filters.occurredFrom);
    const to = asSeoulDate(filters.occurredTo);
    if ((from && !Number.isFinite(from.getTime())) || (to && !Number.isFinite(to.getTime()))) {
      setFilterError('조회 일시를 YY-MM-DD HH:mm:ss 형식의 유효한 날짜와 시간으로 입력하세요.');
      return;
    }
    if (from && to && from > to) {
      setFilterError('조회 시작 일시는 종료 일시보다 늦을 수 없습니다.');
      return;
    }
    setFilterError('');
    setPageInput(null);
    setSearch(current => ({ page: 0, size: current.size, eventId, userId: filters.userId.trim() || undefined,
      detectionType: filters.detectionType || undefined, ...dateBounds(from, to) }));
  }

  function resetFilters() {
    const dates = presetDates(1);
    setFilters({ ...emptyFilters, ...dates });
    setPreset(1);
    setFilterError('');
    setPageInput(null);
    setSearch(current => ({ page: 0, size: current.size,
      ...dateBounds(asSeoulDate(dates.occurredFrom), asSeoulDate(dates.occurredTo)) }));
  }

  function openDetail(id: number) {
    setDetail({ status: 'loading' });
    setSelection({ id });
    const dialog = dialogRef.current;
    if (dialog && !dialog.open) dialog.showModal();
  }

  const result = list.status === 'ready' ? list.data : undefined;
  const currentPage = (result?.page.number ?? search.page) + 1;
  const totalPages = Math.max(1, result?.page.totalPages ?? 0);
  const canPageBack = Boolean(result && currentPage > 1);
  const canPageForward = Boolean(result && currentPage < totalPages);
  const log = detail.status === 'ready' ? detail.data : undefined;
  const evidence = log ? Object.entries(log.evidence) : [];

  function goToPage(page: number) {
    setPageInput(null);
    if (!result) return;
    const target = Math.min(Math.max(Number.isFinite(page) ? Math.trunc(page) : 1, 1), totalPages);
    if (target !== currentPage) setSearch(current => ({ ...current, page: target - 1 }));
  }

  return <section className="detection-logs">
    <header className="detection-header">
      <h1>티켓팅 이상 활동</h1>
      <AutoRefreshControls enabled={autoRefresh} seconds={refreshSeconds}
        onToggle={() => setAutoRefresh(enabled => !enabled)} onIntervalChange={setRefreshSeconds} />
    </header>

    <form className="detection-filters" onSubmit={applyFilters}>
      <label>공연 회차 ID<input inputMode="numeric" pattern="[0-9]+" value={filters.eventId}
        onChange={event => setFilters(current => ({ ...current, eventId: event.target.value }))} /></label>
      <label>사용자 ID<input value={filters.userId} maxLength={50}
        onChange={event => setFilters(current => ({ ...current, userId: event.target.value }))} /></label>
      <label>탐지 유형<select value={filters.detectionType}
        onChange={event => setFilters(current => ({ ...current, detectionType: event.target.value }))}>
        <option value="">전체 유형</option>
        {Object.entries(detectionLabels).map(([type, label]) => <option key={type} value={type}>{label}</option>)}
      </select></label>
      <label className="detection-preset">조회 기간<select value={preset} onChange={event => {
        const days = Number(event.target.value);
        setPreset(days);
        setFilters(current => ({ ...current, ...presetDates(days) }));
        setFilterError('');
      }}>
        <option value={0} disabled>직접 입력</option>
        {datePresets.map(({ days, label }) => <option key={days} value={days}>{label}</option>)}
      </select></label>
      {dateFields.map(({ field, label }) => <label key={field} className="detection-date">{label} 일시 (KST)
        <DateTimeInput value={filters[field]} onChange={value => {
          setPreset(0);
          setFilters(current => ({ ...current, [field]: value }));
        }} />
      </label>)}
      <div className="detection-filter-actions">
        <button type="button" onClick={resetFilters}>초기화</button>
        <button type="submit" className="detection-primary">조회</button>
      </div>
      {filterError && <p role="alert" className="detection-error">{filterError}</p>}
    </form>

    <div className="detection-results">
      <div className="detection-table-wrap">
        <table className="detection-table" aria-label="티켓팅 탐지 로그" aria-busy={list.status === 'loading'}>
          <thead><tr>
            <th scope="col">발생 시각 (KST)</th><th scope="col">공연 회차 ID</th><th scope="col">사용자 ID</th>
            <th scope="col">탐지 유형</th><th scope="col">상세</th>
          </tr></thead>
          <tbody>
            {list.status === 'loading' && <tr><td colSpan={5} className="detection-state"><span role="status">탐지 로그를 불러오는 중입니다.</span></td></tr>}
            {list.status === 'error' && <tr><td colSpan={5} className="detection-state"><span role="alert" className="detection-error">{list.message}</span></td></tr>}
            {result?.content.length === 0 && <tr><td colSpan={5} className="detection-state"><span role="status">조회된 탐지 로그가 없습니다.</span></td></tr>}
            {result?.content.map(item => <tr key={item.id} className="detection-row" onClick={() => openDetail(item.id)}>
              <td><time dateTime={item.occurredAt}>{dateTime.format(new Date(item.occurredAt))}</time></td>
              <td>{item.eventId}</td>
              <td className="detection-user">{item.userId}</td>
              <td><span className="detection-type">{detectionLabels[item.detectionType] ?? item.detectionType}</span></td>
              <td><button type="button" aria-haspopup="dialog" aria-label={`탐지 로그 ${item.id} 상세 보기`}
                onClick={event => { event.stopPropagation(); openDetail(item.id); }}>상세 보기</button></td>
            </tr>)}
          </tbody>
        </table>
      </div>
    </div>
    <footer className="detection-pagination">
      <label className="detection-page-size">페이지당:<select value={search.size} onChange={event => {
        const size = Number(event.target.value);
        setPageInput(null);
        setSearch(current => ({ ...current, page: 0, size }));
      }}>
        {pageSizes.map(size => <option key={size} value={size}>{size}개</option>)}
      </select></label>
      <nav aria-label="탐지 로그 페이지">
        <button type="button" title="5페이지 앞" aria-label="5페이지 앞" disabled={!canPageBack} onClick={() => goToPage(currentPage - 5)}>«</button>
        <button type="button" title="이전" aria-label="이전 페이지" disabled={!canPageBack} onClick={() => goToPage(currentPage - 1)}>‹</button>
        <div className="detection-page-number">
          <input type="number" aria-label="이동할 페이지" min={1} max={totalPages} step={1} disabled={!result || totalPages === 1}
            value={pageInput ?? currentPage} onChange={event => setPageInput(event.target.value)}
            onBlur={event => { if (pageInput !== null) goToPage(Number(event.currentTarget.value)); }}
            onKeyDown={event => {
              if (event.key === 'Enter') {
                event.preventDefault();
                goToPage(Number(event.currentTarget.value));
              }
            }} />
          <span aria-hidden="true">/</span><strong>{totalPages}</strong>
        </div>
        <button type="button" title="다음" aria-label="다음 페이지" disabled={!canPageForward} onClick={() => goToPage(currentPage + 1)}>›</button>
        <button type="button" title="5페이지 뒤" aria-label="5페이지 뒤" disabled={!canPageForward} onClick={() => goToPage(currentPage + 5)}>»</button>
      </nav>
      <span className="detection-page-summary">총 <strong>{(result?.page.totalElements ?? 0).toLocaleString('ko-KR')}</strong>건</span>
    </footer>

    <dialog ref={dialogRef} className="detection-dialog" aria-labelledby="detection-dialog-title"
      onClose={event => { if (!event.currentTarget.open) setSelection(null); }}
      onClick={event => { if (event.target === event.currentTarget) event.currentTarget.close(); }}>
      <div className="detection-dialog-content">
        <header className="detection-dialog-header">
          <h2 id="detection-dialog-title">탐지 로그 상세</h2>
          <button type="button" onClick={() => dialogRef.current?.close()}>닫기</button>
        </header>
        {detail.status === 'loading' && <p role="status">탐지 근거를 불러오는 중입니다.</p>}
        {detail.status === 'error' && <p role="alert" className="detection-error">{detail.message}</p>}
        {log && <>
          <dl>
            <dt>탐지 유형</dt><dd>{detectionLabels[log.detectionType] ?? log.detectionType}</dd>
            <dt>발생 시각 (KST)</dt><dd><time dateTime={log.occurredAt}>{dateTime.format(new Date(log.occurredAt))}</time></dd>
            <dt>공연 회차 ID</dt><dd>{log.eventId}</dd>
            <dt>사용자 ID</dt><dd>{log.userId}</dd>
            <dt>Trace ID</dt><dd><code>{log.traceId || '—'}</code></dd>
            <dt>세션 해시</dt><dd><code>{log.sessionKeyHash}</code></dd>
          </dl>
          <section className="detection-evidence">
            <h3>탐지 근거</h3>
            {evidence.length === 0 ? <p>추가로 기록된 근거가 없습니다.</p> : <dl>
              {evidence.map(([key, value]) => <div key={key}>
                <dt>{evidenceLabels[key]?.label ?? key}</dt><dd>{formatEvidence(value, evidenceLabels[key]?.unit)}</dd>
              </div>)}
            </dl>}
          </section>
        </>}
      </div>
    </dialog>
  </section>;
}
