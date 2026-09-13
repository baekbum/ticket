import { useEffect, useRef, useState } from 'react';
import type { createAuthenticatedRequest } from './authenticatedRequest';
import { CARD_COMPANIES } from './cardPayment';

type Detail = {
  reservation: { reservationId: number; eventTitle: string; eventDateTime: string; venue: string; cancelDeadlineAt: string | null };
  tickets: { ticketId: number; seatName: string; zone: string; seatRow: number; seatCol: number; grade: string; price: number; status: string }[];
  delivery: null | { recipientName: string; recipientPhone: string; address: string; detailAddress: string; carrier: string | null; trackingNumber: string | null };
  payment: null | { paymentNo: string; method: string; status: string; amount: number; refundedAmount: number; paidAt: string | null; expiresAt: string | null; cardCompany: string | null; maskedCardNumber: string | null; bankName: string | null; accountNumber: string | null; depositorName: string | null };
  totalTicketAmount: number; totalDiscountAmount: number;
};
const paymentLabels: Record<string, string> = { READY: '결제 준비', WAITING_DEPOSIT: '입금 대기', PAID: '결제 완료', CANCELLED: '취소', EXPIRED: '만료', REFUNDED: '환불 완료', PARTIALLY_REFUNDED: '부분 환불' };
const ticketLabels: Record<string, string> = { PAID: '예매 완료', PENDING_PAYMENT: '결제 대기', CANCELLED: '취소', EXPIRED: '만료' };
const money = (value: number) => `${value.toLocaleString()}원`;
const date = (value: string | null) => value?.replace('T', ' ') || '-';

export default function MyTicketDetailDialog({ reservationId, request, onClose }: {
  reservationId: number; request: ReturnType<typeof createAuthenticatedRequest>; onClose: () => void;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [detail, setDetail] = useState<Detail | null>(null);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
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
    setDetail(null);
    request<Detail>(`/ticket/api/v1/reservation/select/detail/${reservationId}`, { method: 'GET', signal: controller.signal })
      .then((data) => { if (!controller.signal.aborted) setDetail(data); })
      .catch((failure) => { if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : '상세 내역을 불러오지 못했습니다.'); });
    return () => controller.abort();
  }, [reservationId, request, retry]);
  const payment = detail?.payment;
  const fee = detail && payment ? payment.amount - Math.max(0, detail.totalTicketAmount - detail.totalDiscountAmount) : null;
  return <dialog ref={dialogRef} className="my-ticket-detail-dialog" aria-labelledby="my-ticket-detail-title"
    onCancel={(event) => { event.preventDefault(); onClose(); }}>
    <header><div><small>MY TICKET · 예매 번호 {reservationId}</small><h2 id="my-ticket-detail-title">예매 상세</h2></div>
      <button type="button" onClick={onClose} aria-label="예매 상세 닫기">×</button></header>
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
          <thead><tr><th>좌석 등급</th><th>좌석 번호</th><th>구매 금액</th><th>예매/취소</th></tr></thead>
          <tbody>{detail.tickets.map((ticket) => <tr key={ticket.ticketId}><td>{ticket.grade}석</td><td>{ticket.seatName || `${ticket.zone} ${ticket.seatRow}열 ${ticket.seatCol}번`}</td><td>{money(ticket.price)}</td><td>{ticketLabels[ticket.status] || ticket.status}</td></tr>)}</tbody>
        </table></div>{!detail.tickets.length && <p>발급된 티켓이 없습니다.</p>}
          <p className="my-ticket-deadline">공연 취소 마감: {date(detail.reservation.cancelDeadlineAt)}</p>
        </section>
      </>}
    <footer><button type="button" onClick={onClose}>닫기</button></footer>
  </dialog>;
}
