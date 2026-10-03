import { useEffect, useState, type FormEvent } from 'react';
import { adminFetch } from './api';
import './VirtualAccountReconciliation.css';

type Payment = {
  paymentNo: string;
  orderId: string;
  reservationId: number;
  status: string;
  amount: number;
  bankName: string | null;
  accountNumber: string | null;
  requestedAt: string;
  expiresAt: string | null;
  paidAt: string | null;
};
type GatewayStatus = {
  paymentNo: string;
  status: string;
  bankName: string;
  accountNumber: string;
  depositorName: string | null;
  amount: number;
  depositedAt: string | null;
  expiresAt: string;
};
type Reconciliation = {
  payment: Payment;
  gateway: GatewayStatus;
  completable: boolean;
  message: string;
};
type PaymentPage = {
  content: Payment[];
  page: { number: number; size: number; totalElements: number; totalPages: number };
};

const baseUrl = '/ticket/api/v1/manage/payment/virtual-account';
const statusLabels: Record<string, string> = {
  READY: '결제 준비',
  WAITING_DEPOSIT: '입금 대기',
  DEPOSITED: '입금 확인',
  TICKET_PAYMENT_FAILED: '입금 확인 · Ticket 반영 실패',
  TICKET_PAYMENT_COMPLETED: '입금 확인 · Ticket 반영 완료',
  PAID: '결제 완료',
  FAILED: '실패',
  EXPIRED: '만료',
  CANCELLED: '취소',
  REFUNDED: '환불 완료',
  PARTIALLY_REFUNDED: '부분 환불',
};
const statusOptions = ['WAITING_DEPOSIT', 'PAID', 'EXPIRED', 'CANCELLED', 'REFUNDED', 'PARTIALLY_REFUNDED', 'READY', 'FAILED'];

async function responseError(response: Response): Promise<string> {
  const body = await response.json().catch(() => null) as { message?: string } | null;
  return body?.message || `요청에 실패했습니다. (HTTP ${response.status})`;
}

function display(value: string | number | null | undefined): string {
  return value === null || value === undefined || value === '' ? '-' : String(value).replace('T', ' ');
}

function money(amount: number): string {
  return `${Number(amount).toLocaleString()}원`;
}

export default function VirtualAccountReconciliation() {
  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [pageNumber, setPageNumber] = useState(0);
  const [refreshVersion, setRefreshVersion] = useState(0);
  const [payments, setPayments] = useState<PaymentPage | null>(null);
  const [listLoading, setListLoading] = useState(false);
  const [listError, setListError] = useState('');
  const [selectedPaymentNo, setSelectedPaymentNo] = useState<string | null>(null);
  const [result, setResult] = useState<Reconciliation | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');
  const [reason, setReason] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    const params = new URLSearchParams({ page: String(pageNumber) });
    if (search) params.set('paymentNo', search);
    if (status) params.set('status', status);
    setListLoading(true);
    setListError('');
    setPayments(null);
    adminFetch(`${baseUrl}?${params}`)
      .then(async response => {
        if (!response.ok) throw new Error(await responseError(response));
        return response.json() as Promise<PaymentPage>;
      })
      .then(data => { if (active) setPayments(data); })
      .catch(error => {
        if (active) setListError(error instanceof Error ? error.message : '결제 목록을 불러오지 못했습니다.');
      })
      .finally(() => { if (active) setListLoading(false); });
    return () => { active = false; };
  }, [pageNumber, search, status, refreshVersion]);

  useEffect(() => {
    if (!selectedPaymentNo) return;
    let active = true;
    setResult(null);
    setDetailError('');
    setMessage('');
    setReason('');
    setDetailLoading(true);
    adminFetch(`${baseUrl}/${encodeURIComponent(selectedPaymentNo)}/reconciliation`)
      .then(async response => {
        if (!response.ok) throw new Error(await responseError(response));
        return response.json() as Promise<Reconciliation>;
      })
      .then(data => { if (active) setResult(data); })
      .catch(error => {
        if (active) setDetailError(error instanceof Error ? error.message : 'PG 상태를 확인하지 못했습니다.');
      })
      .finally(() => { if (active) setDetailLoading(false); });
    return () => { active = false; };
  }, [selectedPaymentNo]);

  useEffect(() => {
    if (!selectedPaymentNo) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !busy) setSelectedPaymentNo(null);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [selectedPaymentNo, busy]);

  function searchPayments(event: FormEvent) {
    event.preventDefault();
    setPageNumber(0);
    setSearch(searchInput.trim());
  }

  function resetSearch() {
    setSearchInput('');
    setSearch('');
    setStatus('');
    setPageNumber(0);
  }

  async function complete() {
    if (!result?.completable || !reason.trim() || busy) return;
    if (!window.confirm(`${result.payment.paymentNo} 결제를 PG 입금 정보로 완료 처리하시겠습니까?`)) return;
    setBusy(true);
    setMessage('');
    try {
      const response = await adminFetch(
        `${baseUrl}/${encodeURIComponent(result.payment.paymentNo)}/complete`,
        { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ reason: reason.trim() }) },
      );
      if (!response.ok) throw new Error(await responseError(response));
      const updatedPayment = await response.json() as Payment;
      setResult(current => current ? {
        ...current,
        payment: updatedPayment,
        completable: false,
        message: 'Ticket 결제가 이미 완료되었습니다.',
      } : current);
      setMessage('결제와 예약·티켓·좌석의 완료 상태를 반영했습니다.');
      setReason('');
      setRefreshVersion(current => current + 1);
      try {
        const refreshed = await adminFetch(`${baseUrl}/${encodeURIComponent(result.payment.paymentNo)}/reconciliation`);
        if (refreshed.ok) setResult(await refreshed.json() as Reconciliation);
      } catch { /* 완료 응답은 확인했으므로 재조회 오류로 완료 결과를 덮지 않는다. */ }
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '결제 완료 처리에 실패했습니다.');
    } finally {
      setBusy(false);
    }
  }

  return <div className="va-reconciliation">
    <header className="va-header">
      <h1>가상계좌 입금 현황</h1>
      <p>가상계좌 결제 내역을 조회하고 Ticket과 PG의 입금 상태를 확인합니다.</p>
    </header>
    <form className="va-toolbar" onSubmit={searchPayments}>
      <input aria-label="결제 번호" value={searchInput} onChange={event => setSearchInput(event.target.value)} placeholder="결제 번호" />
      <select aria-label="Ticket 결제 상태" value={status} onChange={event => { setStatus(event.target.value); setPageNumber(0); }}>
        <option value="">전체 상태</option>
        {statusOptions.map(value => <option key={value} value={value}>{statusLabels[value]}</option>)}
      </select>
      <button type="submit">검색</button>
      <button type="button" className="va-outline-button" onClick={resetSearch}>초기화</button>
    </form>
    {listError && <p role="alert" className="va-error">{listError}</p>}
    <div className="va-table-wrap">
      <table className="va-table">
        <colgroup>
          <col style={{ width: 64 }} /><col style={{ width: 225 }} /><col style={{ width: 100 }} />
          <col style={{ width: 115 }} /><col style={{ width: 145 }} /><col style={{ width: 230 }} />
          <col style={{ width: 165 }} /><col style={{ width: 165 }} /><col style={{ width: 100 }} />
        </colgroup>
        <thead><tr>
          <th>순서</th><th>결제 번호</th><th>예매 ID</th><th>결제 금액</th><th>Ticket 상태</th><th>입금 계좌</th><th>결제 요청일</th><th>입금 기한</th><th>관리</th>
        </tr></thead>
        <tbody>
          {payments?.content.map((payment, index) => <tr key={payment.paymentNo} className="va-clickable-row" onClick={() => setSelectedPaymentNo(payment.paymentNo)}>
            <td className="va-number-cell">{payments.page.totalElements - payments.page.number * payments.page.size - index}</td>
            <td className="va-payment-no">{payment.paymentNo}</td>
            <td>{payment.reservationId}</td>
            <td>{money(payment.amount)}</td>
            <td><span className="va-status" data-status={payment.status}>{statusLabels[payment.status] || payment.status}</span></td>
            <td>{payment.bankName ? `${payment.bankName} · ${display(payment.accountNumber)}` : '-'}</td>
            <td>{display(payment.requestedAt)}</td>
            <td>{display(payment.expiresAt)}</td>
            <td><button type="button" className="va-detail-button" onClick={event => { event.stopPropagation(); setSelectedPaymentNo(payment.paymentNo); }}>상세 보기</button></td>
          </tr>)}
          {!listLoading && !listError && payments?.content.length === 0 && <tr><td colSpan={9} className="va-empty">조회된 가상계좌 결제가 없습니다.</td></tr>}
          {listLoading && <tr><td colSpan={9} className="va-empty">결제 목록을 불러오는 중입니다.</td></tr>}
        </tbody>
      </table>
    </div>
    {payments && <div className="va-pagination">
      <div className="va-page-controls">
        <button type="button" aria-label="이전 페이지" disabled={listLoading || pageNumber === 0} onClick={() => setPageNumber(current => current - 1)}>‹</button>
        <span>{payments.page.totalPages === 0 ? 0 : payments.page.number + 1} / {payments.page.totalPages}</span>
        <button type="button" aria-label="다음 페이지" disabled={listLoading || pageNumber + 1 >= payments.page.totalPages} onClick={() => setPageNumber(current => current + 1)}>›</button>
      </div>
      <span className="va-page-summary">총 <strong>{payments.page.totalElements.toLocaleString()}</strong>건</span>
    </div>}
    {selectedPaymentNo && <div className="va-modal-backdrop" onMouseDown={event => { if (event.target === event.currentTarget && !busy) setSelectedPaymentNo(null); }}>
      <div className="va-modal" role="dialog" aria-modal="true" aria-labelledby="va-modal-title">
        <div className="va-modal-header">
          <div><h2 id="va-modal-title">가상계좌 입금 상세</h2><p>{selectedPaymentNo}</p></div>
          <button type="button" className="va-close" aria-label="닫기" disabled={busy} onClick={() => setSelectedPaymentNo(null)}>×</button>
        </div>
        {detailLoading && <p role="status">Ticket·PG 상태를 확인하는 중입니다.</p>}
        {detailError && <p role="alert" className="va-error">{detailError}</p>}
        {message && <p role="status" className="va-message">{message}</p>}
        {result && <>
          <div className="va-panels">
            <section><h3>Ticket 결제</h3><dl>
              <dt>결제번호</dt><dd>{display(result.payment.paymentNo)}</dd>
              <dt>예매 ID</dt><dd>{display(result.payment.reservationId)}</dd>
              <dt>주문번호</dt><dd>{display(result.payment.orderId)}</dd>
              <dt>상태</dt><dd>{statusLabels[result.payment.status] || result.payment.status}</dd>
              <dt>금액</dt><dd>{money(result.payment.amount)}</dd>
              <dt>계좌</dt><dd>{display(result.payment.bankName)} · {display(result.payment.accountNumber)}</dd>
              <dt>입금 기한</dt><dd>{display(result.payment.expiresAt)}</dd>
            </dl></section>
            <section><h3>PG 입금</h3><dl>
              <dt>결제번호</dt><dd>{display(result.gateway.paymentNo)}</dd>
              <dt>상태</dt><dd>{statusLabels[result.gateway.status] || result.gateway.status}</dd>
              <dt>금액</dt><dd>{money(result.gateway.amount)}</dd>
              <dt>계좌</dt><dd>{display(result.gateway.bankName)} · {display(result.gateway.accountNumber)}</dd>
              <dt>입금자</dt><dd>{display(result.gateway.depositorName)}</dd>
              <dt>입금 시각</dt><dd>{display(result.gateway.depositedAt)}</dd>
            </dl></section>
          </div>
          <section className="va-action">
            <strong>{result.message}</strong>
            {result.completable && <>
              <label htmlFor="va-reason">처리 사유 또는 문의번호</label>
              <textarea id="va-reason" value={reason} onChange={event => setReason(event.target.value)} maxLength={500} placeholder="예: 1:1 문의 #123 확인 후 입금 완료 재반영" />
              <button type="button" onClick={() => { void complete(); }} disabled={busy || !reason.trim()}>결제 완료 재반영</button>
            </>}
          </section>
        </>}
      </div>
    </div>}
  </div>;
}
