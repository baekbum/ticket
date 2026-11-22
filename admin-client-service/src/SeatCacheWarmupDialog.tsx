import { useEffect, useRef, useState, type FormEvent } from 'react';
import { adminFetch } from './api';

const modes = [
  {
    value: 'MISSING_ONLY', label: '없는 데이터만 추가',
    description: '판매 전에는 없는 좌석을 DB 상태로 추가합니다. 판매 후에는 DB에서 예매 가능하고 Redis 좌석·락 키가 모두 없는 좌석만 추가합니다.',
  },
  {
    value: 'OVERWRITE', label: '전체 덮어쓰기',
    description: '판매 시작 전까지만 사용할 수 있으며, 대상 좌석을 DB 상태로 다시 적재합니다.',
  },
] as const;

export default function SeatCacheWarmupDialog({ eventId, onClose, onComplete }: {
  eventId: number;
  onClose: () => void;
  onComplete: (message: string) => void;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [mode, setMode] = useState<(typeof modes)[number]['value']>('MISSING_ONLY');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const dialog = dialogRef.current;
    if (dialog && !dialog.open) dialog.showModal();
  }, []);

  function close() {
    dialogRef.current?.close();
    onClose();
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    setError('');
    try {
      const response = await adminFetch(`/ticket/api/v1/manage/seat/cache/warm-up/event/${eventId}?mode=${mode}`, { method: 'POST' });
      if (!response.ok) {
        const body = await response.json().catch(() => null) as { message?: string } | null;
        setError(body?.message || `좌석 캐시 적재에 실패했습니다. (HTTP ${response.status})`);
        return;
      }
      const message = await response.text().catch(() => '');
      close();
      onComplete(message || '좌석 캐시 적재가 완료되었습니다.');
    } catch {
      setError('서버에 연결하지 못했습니다. 좌석 캐시 상태를 확인해 주세요.');
    } finally {
      setSubmitting(false);
    }
  }

  return <dialog ref={dialogRef} className="preflight-dialog preflight-warmup-dialog" aria-labelledby="seat-warmup-title"
    aria-describedby="seat-warmup-description" onCancel={event => {
      event.preventDefault();
      if (!submitting) close();
    }}>
    <form onSubmit={submit}>
      <div className="preflight-warmup-heading">
        <i className="ti ti-database-import" aria-hidden="true" />
        <h2 id="seat-warmup-title">이벤트 {eventId}번 전체 좌석 캐시 적재</h2>
        <p id="seat-warmup-description">이 회차의 전체 좌석 데이터를 Redis에 적재합니다.</p>
      </div>
      <fieldset className="preflight-warmup-modes" aria-label="캐시 적재 방식" disabled={submitting}>
        {modes.map(option => <label key={option.value}>
          <input type="radio" name="seat-warmup-mode" value={option.value} checked={mode === option.value}
            onChange={() => setMode(option.value)} />
          <span><strong>{option.label}</strong><small>{option.description}</small></span>
        </label>)}
      </fieldset>
      {error && <p className="preflight-warning" role="alert">{error}</p>}
      <div className="preflight-warmup-actions">
        <button type="button" disabled={submitting} onClick={close}>취소</button>
        <button type="submit" disabled={submitting}>{submitting ? '적재 중…' : '진행'}</button>
      </div>
    </form>
  </dialog>;
}
