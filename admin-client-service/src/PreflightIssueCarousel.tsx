import { useLayoutEffect, useRef, useState } from 'react';
import type { EventSchedule } from './EventPreflight';

function EventPoster({ url }: { url?: string | null }) {
  const [broken, setBroken] = useState(false);
  const safeUrl = url && (/^https?:\/\//i.test(url) || /^\/(?!\/)/.test(url)) ? url : null;
  return <span className="preflight-poster">
    {safeUrl && !broken
      ? <img src={safeUrl} alt="" decoding="async" onError={() => setBroken(true)} />
      : <i className="ti ti-photo" aria-hidden="true" />}
  </span>;
}

export default function PreflightIssueCarousel({ events, selectedId, onSelect }: {
  events: EventSchedule[];
  selectedId?: number;
  onSelect: (event: EventSchedule) => void;
}) {
  const trackRef = useRef<HTMLDivElement>(null);
  const [visible, setVisible] = useState(1);
  const [position, setPosition] = useState(0);
  const lastStart = Math.max(0, events.length - visible);
  const start = Math.min(position, lastStart);

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
  }, []);

  return <section className="preflight-issues" aria-labelledby="preflight-issue-title">
    <div className="preflight-issue-heading">
      <h2 id="preflight-issue-title">확인이 필요한 공연</h2>
      <p role="status" aria-label={`총 ${events.length}개 회차 중 첫 카드가 ${start + 1}번째입니다`}>
        <strong>{start + 1}</strong> / {events.length}
      </p>
    </div>
    <div className="preflight-carousel">
      <button type="button" className="preflight-arrow" aria-label="이전 공연" disabled={start === 0} onClick={() => setPosition(start - 1)}>
        <span aria-hidden="true">‹</span>
      </button>
      <div ref={trackRef} className="preflight-carousel-track" style={{ gridTemplateColumns: `repeat(${visible}, minmax(0, 1fr))` }}>
        {events.slice(start, start + visible).map(event => <button key={event.eventId} type="button" className="preflight-event-card"
          title={event.title} aria-current={event.eventId === selectedId ? 'true' : undefined}
          aria-label={`${event.title} · ${event.eventDateTime?.replace('T', ' ')} · 회차 ${event.eventId}`}
          onClick={() => onSelect(event)}>
          <EventPoster url={event.posterUrl} />
          <strong>{event.title}</strong>
        </button>)}
      </div>
      <button type="button" className="preflight-arrow" aria-label="다음 공연" disabled={start === lastStart} onClick={() => setPosition(start + 1)}>
        <span aria-hidden="true">›</span>
      </button>
    </div>
  </section>;
}
