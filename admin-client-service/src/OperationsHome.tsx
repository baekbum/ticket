import { useEffect, useRef, useState } from 'react';
import { adminFetch } from './api';
import AutoRefreshControls, { defaultRefreshSeconds } from './AutoRefreshControls';

type Count = { state: 'loading' | 'error' } | { state: 'ready'; value: number };

async function loadCount(url: string, options: RequestInit): Promise<Count> {
  try {
    const response = await adminFetch(url, options);
    if (!response.ok) return { state: 'error' };
    const { page } = await response.json() as { page?: { totalElements?: number } };
    const value = page?.totalElements;
    return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0
      ? { state: 'ready', value }
      : { state: 'error' };
  } catch {
    return { state: 'error' };
  }
}

function getCountLabel(count: Count) {
  if (count.state === 'ready') return `${count.value.toLocaleString('ko-KR')}건`;
  return count.state === 'loading' ? '조회 중…' : '조회 실패';
}

export default function OperationsHome({ onOpenList }: {
  onOpenList: (menu: string, context: Record<string, unknown>) => void;
}) {
  const [inquiry, setInquiry] = useState<Count>({ state: 'loading' });
  const [refund, setRefund] = useState<Count>({ state: 'loading' });
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [refreshSeconds, setRefreshSeconds] = useState(defaultRefreshSeconds);
  const initialLoadComplete = useRef(false);

  useEffect(() => {
    const controller = new AbortController();
    const { signal } = controller;
    let timer: number | undefined;

    function scheduleRefresh() {
      timer = window.setTimeout(() => { void refreshCounts(); }, refreshSeconds * 1000);
    }

    async function refreshCounts() {
      const [nextInquiry, nextRefund] = await Promise.all([
        loadCount('/support/api/v1/manage/inquiry/select?status=WAITING&page=0&size=1', { signal }),
        loadCount('/ticket/api/v1/manage/payment/refund-process/select', {
          signal,
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ page: 0, size: 1, statuses: ['GATEWAY_FAILED', 'LOCAL_FAILED'] }),
        }),
      ]);
      if (signal.aborted) return;

      setInquiry(nextInquiry);
      setRefund(nextRefund);
      initialLoadComplete.current = true;
      if (autoRefresh) scheduleRefresh();
    }

    if (!initialLoadComplete.current) void refreshCounts();
    else if (autoRefresh) scheduleRefresh();

    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [autoRefresh, refreshSeconds]);

  return <section className="operations-home" aria-labelledby="operations-home-title">
    <header className="operations-heading">
      <div>
        <h1 id="operations-home-title">처리할 일</h1>
        <p>확인이 필요한 운영 업무를 한눈에 확인하세요.</p>
      </div>
      <AutoRefreshControls enabled={autoRefresh} seconds={refreshSeconds}
        onToggle={() => setAutoRefresh(enabled => !enabled)} onIntervalChange={setRefreshSeconds} />
    </header>
    <div className="operations-cards">
      <article className="operations-card">
        <div className="operations-card-body">
          <span className="operations-card-icon"><i className="ti ti-message-circle" aria-hidden="true" /></span>
          <div>
            <h2>답변 대기 문의</h2>
            <strong aria-live="polite" data-state={inquiry.state}>{getCountLabel(inquiry)}</strong>
          </div>
        </div>
        <button type="button" onClick={() => onOpenList('inquiry', { status: 'WAITING' })}>문의 목록 보기 <i className="ti ti-arrow-right" aria-hidden="true" /></button>
      </article>
      <article className="operations-card">
        <div className="operations-card-body">
          <span className="operations-card-icon refund"><i className="ti ti-refresh-alert" aria-hidden="true" /></span>
          <div>
            <h2>환불 처리 실패</h2>
            <strong aria-live="polite" data-state={refund.state}>{getCountLabel(refund)}</strong>
          </div>
        </div>
        <button type="button" onClick={() => onOpenList('paymentRefundProcess', { status: 'FAILED' })}>환불 목록 보기 <i className="ti ti-arrow-right" aria-hidden="true" /></button>
      </article>
    </div>
  </section>;
}
