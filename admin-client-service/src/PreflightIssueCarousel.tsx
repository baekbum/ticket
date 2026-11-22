import { useLayoutEffect, useRef, useState } from 'react';
import type { EventSchedule } from './EventPreflight';

export function EventPoster({ url }: { url?: string | null }) {
  const [brokenUrl, setBrokenUrl] = useState<string | null>(null);
  const safeUrl = url && (/^https?:\/\//i.test(url) || /^\/(?!\/)/.test(url)) ? url : null;
  return <span className="preflight-poster">
    {safeUrl && brokenUrl !== safeUrl
      ? <img src={safeUrl} alt="" decoding="async" onError={() => setBrokenUrl(safeUrl)} />
      : <i className="ti ti-photo" aria-hidden="true" />}
  </span>;
}

export default function PreflightIssueCarousel({ events, selectedId, onSelect, loading, notice, onRefresh }: {
  events: EventSchedule[];
  selectedId?: number;
  onSelect: (event: EventSchedule) => void;
  loading: boolean;
  notice: string;
  onRefresh: () => void;
}) {
  const trackRef = useRef<HTMLDivElement>(null);
  const [visible, setVisible] = useState(1);
  const [position, setPosition] = useState(0);
  const lastStart = Math.max(0, events.length - visible);
  const start = Math.min(position, lastStart);
  const hasEvents = events.length > 0;

  useLayoutEffect(() => {
    const track = trackRef.current;
    if (!track) return;
    function measure(width: number) {
      if (width >= 720) setVisible(4);
      else if (width >= 515) setVisible(3);
      else if (width >= 240) setVisible(2);
      else setVisible(1);
    }
    measure(track.clientWidth);
    const observer = new ResizeObserver(([entry]) => measure(entry.contentRect.width));
    observer.observe(track);
    return () => observer.disconnect();
  }, [hasEvents]);

  return <section className="preflight-issues" aria-labelledby="preflight-issue-title">
    <div className="preflight-issue-heading">
      <div className="preflight-issue-title-actions">
        <h2 id="preflight-issue-title">확인이 필요한 공연</h2>
        <button type="button" className="preflight-issue-refresh" disabled={loading} onClick={() => {
          setPosition(0);
          onRefresh();
        }}><i className="ti ti-refresh" aria-hidden="true" /> 새로고침</button>
      </div>
      {hasEvents && <p role="status" aria-label={`총 ${events.length}개 회차 중 첫 카드가 ${start + 1}번째입니다`}>
        <strong>{start + 1}</strong> / {events.length}
      </p>}
    </div>
    {(loading || notice || !hasEvents) && <p className="preflight-issue-notice" role="status">
      {loading ? '무작위로 선택한 회차를 점검하는 중입니다…' : notice || '이번 점검에서 확인이 필요한 회차가 없습니다.'}
    </p>}
    {hasEvents && <div className="preflight-carousel">
      <button type="button" className="preflight-arrow" aria-label="이전 공연" disabled={start === 0} onClick={() => setPosition(start - 1)}>
        <span aria-hidden="true">‹</span>
      </button>
      <div ref={trackRef} className="preflight-carousel-track" style={{ gridTemplateColumns: `repeat(${visible}, minmax(0, 1fr))` }}>
        {events.slice(start, start + visible).map(event => <button key={event.eventId} type="button" className="preflight-event-card"
          title={event.title} aria-current={event.eventId === selectedId ? 'true' : undefined}
          aria-haspopup="dialog"
          aria-label={`${event.title} · ${event.eventDateTime?.replace('T', ' ')} · 회차 ${event.eventId}`}
          onClick={() => onSelect(event)}>
          <EventPoster url={event.posterUrl} />
          <strong>{event.title}</strong>
        </button>)}
      </div>
      <button type="button" className="preflight-arrow" aria-label="다음 공연" disabled={start === lastStart} onClick={() => setPosition(start + 1)}>
        <span aria-hidden="true">›</span>
      </button>
    </div>}
  </section>;
}
