import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { SessionExpiredError, type createAuthenticatedRequest } from './authenticatedRequest';
import { CARD_COMPANIES } from './cardPayment';

type Ticket = { ticketId: number; seatName: string; zone: string; seatRow: number; seatCol: number; grade: string; price: number; status: string };
type Detail = {
  reservation: { reservationId: number; userId: string; eventId: number; eventTitle: string; eventDateTime: string; venue: string; cancelDeadlineAt: string | null };
  tickets: Ticket[];
  delivery: null | { recipientName: string; recipientPhone: string; address: string; detailAddress: string; carrier: string | null; trackingNumber: string | null };
  payment: null | { paymentNo: string; method: string; status: string; amount: number; refundedAmount: number; paidAt: string | null; expiresAt: string | null; cardCompany: string | null; maskedCardNumber: string | null; bankName: string | null; accountNumber: string | null; depositorName: string | null };
  totalTicketAmount: number; totalDiscountAmount: number;
};

const paymentLabels: Record<string, string> = { READY: '결제 준비', WAITING_DEPOSIT: '입금 대기', PAID: '결제 완료', CANCELLED: '취소', EXPIRED: '만료', REFUNDED: '환불 완료', PARTIALLY_REFUNDED: '부분 환불' };
const ticketLabels: Record<string, string> = { PAID: '예매 완료', PENDING_PAYMENT: '결제 대기', CANCELLED: '취소', EXPIRED: '만료' };
const refundBanks = [
  ['KB', 'KB국민은행'], ['SHINHAN', '신한은행'], ['WOORI', '우리은행'],
  ['HANA', '하나은행'], ['NH', 'NH농협은행'], ['IBK', 'IBK기업은행'],
  ['KAKAO', '카카오뱅크'], ['TOSS', '토스뱅크'], ['BUSAN', '부산은행'],
] as const;
const money = (value: number) => `${value.toLocaleString()}원`;
const date = (value: string | null) => value?.replace('T', ' ') || '-';
const seatName = (ticket: Ticket) => ticket.seatName || `${ticket.zone} ${ticket.seatRow}열 ${ticket.seatCol}번`;

export default function MyTicketDetailDialog({ reservationId, request, onClose, onRefunded }: {
  reservationId: number;
  request: ReturnType<typeof createAuthenticatedRequest>;
  onClose: () => void;
  onRefunded?: () => void;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [detail, setDetail] = useState<Detail | null>(null);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  const [refundTicket, setRefundTicket] = useState<Ticket | null>(null);
  const [confirmTicket, setConfirmTicket] = useState<Ticket | null>(null);
  const [refundAlert, setRefundAlert] = useState<{ message: string; kind: 'success' | 'error' } | null>(null);
  const [refundingTicketId, setRefundingTicketId] = useState<number | null>(null);
  const [bankCompany, setBankCompany] = useState('');
  const [accountNumber, setAccountNumber] = useState('');
  const [accountHolder, setAccountHolder] = useState('');

  useEffect(() => {
    const dialog = dialogRef.current;
    const previousFocus = document.activeElement;
    dialog?.showModal();
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      dialog?.close();
      document.body.style.overflow = overflow;
      if (previousFocus instanceof HTMLElement) previousFocus.focus();
    };
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    setError('');
    request<Detail>(`/client-api/api/v1/reservation/select/detail/${reservationId}`, { method: 'GET', signal: controller.signal })
      .then((data) => { if (!controller.signal.aborted) setDetail(data); })
      .catch((failure) => { if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : '상세 내역을 불러오지 못했습니다.'); });
    return () => controller.abort();
  }, [reservationId, request, retry]);

  const payment = detail?.payment;
  const fee = detail && payment ? payment.amount - Math.max(0, detail.totalTicketAmount - detail.totalDiscountAmount) : null;
  const isBankTransfer = payment?.method === 'BANK_TRANSFER';
  const refundDeadlinePassed = detail?.reservation.cancelDeadlineAt
    ? new Date(detail.reservation.cancelDeadlineAt).getTime() < Date.now()
    : false;
  const paymentIsRefundable = payment?.status === 'PAID' || payment?.status === 'PARTIALLY_REFUNDED';

  function beginRefund(ticket: Ticket) {
    setRefundTicket(ticket);
    if (!isBankTransfer) setConfirmTicket(ticket);
  }

  function requestRefund(ticket: Ticket, event?: FormEvent) {
    event?.preventDefault();
    if (refundingTicketId != null) return;
    if (isBankTransfer && (!bankCompany || !accountNumber.trim() || !accountHolder.trim())) {
      setRefundAlert({ message: '환불받을 은행, 계좌번호, 예금주를 모두 입력해주세요.', kind: 'error' });
      return;
    }
    setConfirmTicket(ticket);
  }

  async function submitRefund(ticket: Ticket) {
    if (!detail || refundingTicketId != null) return;

    setConfirmTicket(null);
    setRefundingTicketId(ticket.ticketId);
    try {
      await request(`/client-api/api/v1/reservation/cancel/id/${reservationId}`, {
        method: 'PUT',
        body: JSON.stringify({
          userId: detail.reservation.userId,
          selectedTicketIdList: [ticket.ticketId],
          eventId: detail.reservation.eventId,
          refundAccount: isBankTransfer ? {
            bankCompany,
            accountNumber: accountNumber.trim(),
            accountHolder: accountHolder.trim(),
          } : null,
        }),
      });
      setRefundTicket(null);
      setBankCompany('');
      setAccountNumber('');
      setAccountHolder('');
      setRefundAlert({ message: '환불이 완료되었습니다.', kind: 'success' });
      setRetry((value) => value + 1);
      onRefunded?.();
    } catch (failure) {
      setRefundAlert({
        message: failure instanceof SessionExpiredError
          ? failure.message
          : '환불 처리 도중 문제가 발생했습니다. 잠시 후 다시 시도해주세요.',
        kind: 'error',
      });
    } finally {
      setRefundingTicketId(null);
    }
  }

  return <dialog ref={dialogRef} className="my-ticket-detail-dialog" aria-labelledby="my-ticket-detail-title"
    onCancel={(event) => {
      event.preventDefault();
      if (refundAlert) setRefundAlert(null);
      else if (confirmTicket) setConfirmTicket(null);
      else if (refundingTicketId == null) onClose();
    }}>
    <header><div><small>MY TICKET · 예매 번호 {reservationId}</small><h2 id="my-ticket-detail-title">예매 상세</h2></div>
      <button type="button" onClick={onClose} disabled={refundingTicketId != null} aria-label="예매 상세 닫기">×</button></header>
    {error ? <div className="my-ticket-state" role="alert"><p>{error}</p><button type="button" onClick={() => setRetry((n) => n + 1)}>다시 시도</button></div>
      : !detail ? <p className="my-ticket-state" role="status">예매 상세를 불러오는 중입니다.</p> : <>
        <div className="my-ticket-detail-event"><h3>{detail.reservation.eventTitle}</h3><p>{detail.reservation.eventDateTime} · {detail.reservation.venue}</p></div>
        <section><h3>티켓 수령 방법</h3><dl className="my-ticket-detail-facts">
          <div><dt>수령 방법</dt><dd>{detail.delivery ? '배송' : '현장 수령 · 공연 당일 예매 번호와 본인 확인 후 티켓을 수령해주세요.'}</dd></div>
          {detail.delivery && <><div><dt>받는 분</dt><dd>{detail.delivery.recipientName} · {detail.delivery.recipientPhone}</dd></div>
            <div><dt>배송지</dt><dd>{detail.delivery.address} {detail.delivery.detailAddress}</dd></div>
            <div><dt>배송 조회</dt><dd>{detail.delivery.carrier || '-'} / {detail.delivery.trackingNumber || '송장 미등록'}</dd></div></>}
        </dl></section>
        <section><h3>구매 내역</h3><div className="my-ticket-purchase">
          <div><span>티켓 금액</span><strong>{money(detail.totalTicketAmount)}</strong><p>할인 금액 −{money(detail.totalDiscountAmount)}</p></div>
          <div><span>예매·배송 수수료</span><strong>{fee != null && fee >= 0 ? money(fee) : '확인 불가'}</strong></div>
          <div className="my-ticket-purchase-total"><span>총 구매 금액</span><strong>{payment ? money(payment.amount) : '결제 정보 없음'}</strong></div>
        </div></section>
        <section><h3>결제 내역</h3>{!payment ? <p>등록된 결제 내역이 없습니다.</p> : <dl className="my-ticket-detail-facts">
          <div><dt>결제 번호</dt><dd>{payment.paymentNo}</dd></div>
          <div><dt>결제 방법</dt><dd>{payment.method === 'CREDIT_CARD' ? '신용카드' : payment.method === 'BANK_TRANSFER' ? '무통장 입금' : payment.method}</dd></div>
          {payment.method === 'CREDIT_CARD' && <div><dt>카드 정보</dt><dd>{CARD_COMPANIES.find((card) => card.code === payment.cardCompany)?.name || payment.cardCompany || '-'} {payment.maskedCardNumber}</dd></div>}
          {payment.method === 'BANK_TRANSFER' && <><div><dt>입금 계좌</dt><dd>{payment.bankName} {payment.accountNumber || '-'}</dd></div><div><dt>입금자명</dt><dd>{payment.depositorName || '-'}</dd></div><div><dt>입금 기한</dt><dd>{date(payment.expiresAt)}</dd></div></>}
          <div><dt>결제 상태</dt><dd>{paymentLabels[payment.status] || payment.status}</dd></div>
          <div><dt>결제 일시</dt><dd>{date(payment.paidAt)}</dd></div>
          <div><dt>결제 금액</dt><dd>{money(payment.amount)}</dd></div>
          <div><dt>환불 금액</dt><dd>{money(payment.refundedAmount ?? 0)}</dd></div>
        </dl>}</section>
        <section><h3>좌석 정보 ({detail.tickets.length}매)</h3><div className="my-ticket-table-scroll"><table className="my-ticket-seat-table">
          <thead><tr><th>좌석 등급</th><th>좌석 번호</th><th>구매 금액</th><th>예매/취소</th><th>환불</th></tr></thead>
          <tbody>{detail.tickets.map((ticket) => <tr key={ticket.ticketId}><td>{ticket.grade}석</td><td>{seatName(ticket)}</td><td>{money(ticket.price)}</td><td>{ticketLabels[ticket.status] || ticket.status}</td><td>
            {ticket.status === 'PAID' && paymentIsRefundable ? <button className="my-ticket-refund-button" type="button"
              disabled={refundDeadlinePassed || refundingTicketId != null} onClick={() => beginRefund(ticket)}>
              {refundingTicketId === ticket.ticketId ? '처리 중…' : refundDeadlinePassed ? '마감' : '환불'}
            </button> : <span className="my-ticket-refund-unavailable">-</span>}
          </td></tr>)}</tbody>
        </table></div>{!detail.tickets.length && <p>발급된 티켓이 없습니다.</p>}
          {refundTicket && isBankTransfer && <form className="my-ticket-refund-form" onSubmit={(event) => requestRefund(refundTicket, event)}>
            <div><strong>환불 계좌 정보</strong><span>{seatName(refundTicket)}</span></div>
            <label>은행<select value={bankCompany} onChange={(event) => setBankCompany(event.target.value)} disabled={refundingTicketId != null}>
              <option value="">은행 선택</option>{refundBanks.map(([code, name]) => <option key={code} value={code}>{name}</option>)}
            </select></label>
            <label>계좌번호<input value={accountNumber} onChange={(event) => setAccountNumber(event.target.value)} autoComplete="off" placeholder="숫자만 입력" disabled={refundingTicketId != null} /></label>
            <label>예금주<input value={accountHolder} onChange={(event) => setAccountHolder(event.target.value)} autoComplete="name" disabled={refundingTicketId != null} /></label>
            <div className="my-ticket-refund-actions"><button type="button" onClick={() => setRefundTicket(null)} disabled={refundingTicketId != null}>취소</button><button type="submit" disabled={refundingTicketId != null}>{refundingTicketId != null ? '처리 중…' : '환불 신청'}</button></div>
          </form>}
          <p className="my-ticket-deadline">공연 취소 마감: {date(detail.reservation.cancelDeadlineAt)}</p>
        </section>
      </>}
    <footer><button type="button" onClick={onClose} disabled={refundingTicketId != null}>닫기</button></footer>
    {confirmTicket && <div className="my-ticket-refund-confirm-backdrop" role="presentation">
      <section className="my-ticket-refund-confirm" role="alertdialog" aria-modal="true" aria-labelledby="refund-confirm-title" aria-describedby="refund-confirm-message">
        <div className="my-ticket-refund-confirm-icon" aria-hidden="true">!</div>
        <h2 id="refund-confirm-title">환불 확인</h2>
        <p id="refund-confirm-message"><strong>{seatName(confirmTicket)}</strong> 좌석을 환불하시겠습니까?<br />환불 후에는 되돌릴 수 없습니다.</p>
        <div className="my-ticket-refund-confirm-actions">
          <button type="button" onClick={() => { setConfirmTicket(null); if (!isBankTransfer) setRefundTicket(null); }}>취소</button>
          <button type="button" autoFocus onClick={() => void submitRefund(confirmTicket)}>환불하기</button>
        </div>
      </section>
    </div>}
    {refundAlert && <div className="my-ticket-refund-confirm-backdrop" role="presentation">
      <section className="my-ticket-refund-confirm" role="alertdialog" aria-modal="true" aria-labelledby="refund-alert-title" aria-describedby="refund-alert-message">
        <div className={`my-ticket-refund-confirm-icon ${refundAlert.kind}`} aria-hidden="true">{refundAlert.kind === 'success' ? '✓' : '!'}</div>
        <h2 id="refund-alert-title">{refundAlert.kind === 'success' ? '환불 완료' : '안내'}</h2>
        <p id="refund-alert-message">{refundAlert.message}</p>
        <div className="my-ticket-refund-confirm-actions single">
          <button type="button" autoFocus onClick={() => setRefundAlert(null)}>확인</button>
        </div>
      </section>
    </div>}
  </dialog>;
}
