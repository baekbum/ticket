import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiRequestError, SessionExpiredError, type createAuthenticatedRequest } from './authenticatedRequest';
import { CARD_COMPANIES } from './cardPayment';

type Ticket = { ticketId: number; seatName: string; zone: string; seatRow: number; seatCol: number; grade: string; price: number; status: string };
type RefundApplication = { paymentRefundProcessId: number; selectedTicketIds: string; refundAmount: number; cancellationFeeAmount: number; status: string; refundBankCompany: string | null; refundAccountNumberMasked: string | null; createdAt: string };
type CancellationResult = { reservationId: number; cancelledTicketIds: number[]; message: string };
type RefundAction = { outcome: 'SUCCEEDED' | 'WITHDRAWN'; application: RefundApplication; message: string };
const finishedRefund = (status: string) => ['LOCAL_SUCCEEDED', 'WITHDRAWN'].includes(status);
const refundStatusLabels: Record<string, string> = { REQUESTED: '결과 확인 대기', GATEWAY_FAILED: '결과 확인 필요', GATEWAY_SUCCEEDED: '환불 반영 대기', LOCAL_FAILED: '환불 반영 재시도 필요', LOCAL_SUCCEEDED: '환불 완료', WITHDRAW_REQUESTED: '철회 확인 중', WITHDRAWN: '철회 완료' };
type Detail = {
  reservation: { reservationId: number; userId: string; eventId: number; eventTitle: string; eventDateTime: string; venue: string; cancelDeadlineAt: string | null };
  tickets: Ticket[];
  delivery: null | { recipientName: string; recipientPhone: string; address: string; detailAddress: string; carrier: string | null; trackingNumber: string | null };
  payment: null | { pgPaymentId?: string | null; paymentNo: string; method: string; status: string; amount: number; refundedAmount: number; paidAt: string | null; expiresAt: string | null; cardCompany: string | null; maskedCardNumber: string | null; bankName: string | null; accountNumber: string | null; depositorName: string | null };
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

export default function MyTicketDetailDialog({ reservationId, request, onClose, onReservationChanged }: {
  reservationId: number;
  request: ReturnType<typeof createAuthenticatedRequest>;
  onClose: () => void;
  onReservationChanged?: () => void;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [detail, setDetail] = useState<Detail | null>(null);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  const [refundTicket, setRefundTicket] = useState<Ticket | null>(null);
  const [confirmTicket, setConfirmTicket] = useState<Ticket | null>(null);
  const [confirmCancellation, setConfirmCancellation] = useState(false);
  const [cancellingReservation, setCancellingReservation] = useState(false);
  const cancellationInFlight = useRef(false);
  const [refundAlert, setRefundAlert] = useState<{ message: string; kind: 'success' | 'error' } | null>(null);
  const [refundingTicketId, setRefundingTicketId] = useState<number | null>(null);
  const [bankCompany, setBankCompany] = useState('');
  const [accountNumber, setAccountNumber] = useState('');
  const [accountHolder, setAccountHolder] = useState('');
  const [applications, setApplications] = useState<RefundApplication[]>([]);
  const [applicationsError, setApplicationsError] = useState('');
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [refundSelection, setRefundSelection] = useState<number[]>([]);
  const isProcessing = refundingTicketId != null || cancellingReservation;

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
    request<Detail>(`/ticket/api/v1/reservation/select/detail/${reservationId}`, { method: 'GET', signal: controller.signal })
      .then((data) => { if (!controller.signal.aborted) setDetail(data); })
      .catch((failure) => { if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : '상세 내역을 불러오지 못했습니다.'); });
    return () => controller.abort();
  }, [reservationId, request, retry]);

  const payment = detail?.payment;
  const fee = detail && payment ? payment.amount - Math.max(0, detail.totalTicketAmount - detail.totalDiscountAmount) : null;
  const isBankTransfer = payment?.method === 'BANK_TRANSFER';
  const isGatewayPayment = (isBankTransfer || payment?.method === 'CREDIT_CARD') && Boolean(payment?.pgPaymentId);

  useEffect(() => {
    if (!isGatewayPayment) return;
    const controller = new AbortController();
    request<RefundApplication[]>(`/ticket/api/v1/reservation/${reservationId}/refunds`, { method: 'GET', signal: controller.signal })
      .then((rows) => { if (!controller.signal.aborted) { setApplications(rows); setApplicationsError(''); } })
      .catch(() => { if (!controller.signal.aborted) setApplicationsError('환불 신청 이력을 불러오지 못했습니다. 다시 확인해주세요.'); });
    return () => controller.abort();
  }, [isGatewayPayment, request, reservationId, retry]);
  const paymentIsRefundable = payment?.status === 'PAID' || payment?.status === 'PARTIALLY_REFUNDED';
  const canCancelUnpaid = isGatewayPayment && isBankTransfer && payment?.status === 'WAITING_DEPOSIT'
    && detail != null && detail.tickets.length > 0 && detail.tickets.every((ticket) => ticket.status === 'PENDING_PAYMENT');
  const canRefund = isGatewayPayment && paymentIsRefundable;

  async function cancelUnpaidReservation() {
    if (!detail || !canCancelUnpaid || isProcessing || cancellationInFlight.current) return;

    cancellationInFlight.current = true;
    setConfirmCancellation(false);
    setCancellingReservation(true);
    try {
      const result = await request<CancellationResult>(`/ticket/api/v1/reservation/cancel/id/${reservationId}`, {
        method: 'PUT', body: JSON.stringify({ eventId: detail.reservation.eventId,
          selectedTicketIdList: detail.tickets.map((ticket) => ticket.ticketId) }),
      });
      setRefundAlert({ message: result.message, kind: 'success' });
    } catch (failure) {
      setRefundAlert({ message: failure instanceof SessionExpiredError || failure instanceof ApiRequestError
        ? failure.message : '예매 취소 결과를 확인하지 못했습니다. 예매 상태를 확인하고 다시 시도해주세요.', kind: 'error' });
    } finally {
      cancellationInFlight.current = false;
      setCancellingReservation(false);
      setSelectedIds([]);
      setRetry((value) => value + 1);
      onReservationChanged?.();
    }
  }

  function selectCancellationTicket(ticketId: number, checked: boolean) {
    if (canCancelUnpaid && detail) {
      setSelectedIds(checked ? detail.tickets.map((ticket) => ticket.ticketId) : []);
    } else {
      setSelectedIds((ids) => checked ? [...ids, ticketId] : ids.filter((id) => id !== ticketId));
    }
    setRefundTicket(null);
    setConfirmTicket(null);
    setRefundSelection([]);
  }

  function beginCancellation() {
    if (!detail || isProcessing || selectedIds.length === 0) return;

    if (canCancelUnpaid) {
      setConfirmCancellation(true);
    } else if (canRefund) {
      const tickets = detail.tickets.filter((ticket) => ticket.status === 'PAID' && selectedIds.includes(ticket.ticketId));
      if (tickets.length > 0) beginRefund(tickets[0], tickets.map((ticket) => ticket.ticketId));
    }
  }

  function beginRefund(ticket: Ticket, ids: number[] = [ticket.ticketId]) {
    setRefundSelection(ids);
    setRefundTicket(ticket);
    if (!isBankTransfer) setConfirmTicket(ticket);
  }

  function requestRefund(ticket: Ticket, event?: FormEvent) {
    event?.preventDefault();
    if (isProcessing) return;
    if (isBankTransfer && (!bankCompany || !accountNumber.trim() || !accountHolder.trim())) {
      setRefundAlert({ message: '환불받을 은행, 계좌번호, 예금주를 모두 입력해주세요.', kind: 'error' });
      return;
    }
    setConfirmTicket(ticket);
  }

  async function submitRefund(ticket: Ticket) {
    if (!detail || isProcessing) return;
    if (!isGatewayPayment) {
      setConfirmTicket(null);
      setRefundAlert({ message: 'PG 결제 정보가 없어 환불을 요청할 수 없습니다. 예매 내역을 확인해주세요.', kind: 'error' });
      return;
    }

    setConfirmTicket(null);
    setRefundingTicketId(ticket.ticketId);
    try {
      const result = await request<RefundAction>(`/ticket/api/v1/reservation/${reservationId}/refunds`, {
        method: 'POST', body: JSON.stringify({ eventId: detail.reservation.eventId,
          selectedTicketIdList: refundSelection.length ? refundSelection : [ticket.ticketId],
          refundAccount: isBankTransfer ? { bankCompany, accountNumber: accountNumber.trim(), accountHolder: accountHolder.trim() } : null }),
      });
      setRefundAlert({ message: result.message, kind: 'success' });
      setRefundTicket(null);
      setSelectedIds([]);
      setAccountNumber('');
      setAccountHolder('');
      setRetry((value) => value + 1);
      onReservationChanged?.();
    } catch (failure) {
      setRefundAlert({
        message: failure instanceof SessionExpiredError
          ? failure.message
          : failure instanceof ApiRequestError ? failure.message : '환불 처리 도중 문제가 발생했습니다. 잠시 후 다시 시도해주세요.',
        kind: 'error',
      });
      setRetry((value) => value + 1);
    } finally {
      setRefundingTicketId(null);
    }
  }

  async function runApplication(application: RefundApplication, withdraw: boolean) {
    if (isProcessing || finishedRefund(application.status)) return;
    if (withdraw && !window.confirm('아직 실행되지 않은 환불 신청을 철회하시겠습니까? 이미 환불된 경우 완료 결과를 반영합니다.')) return;
    setRefundingTicketId(-1);
    try {
      const action = withdraw || application.status === 'WITHDRAW_REQUESTED' ? 'withdraw' : 'retry';
      const result = await request<RefundAction>(`/ticket/api/v1/reservation/${reservationId}/refunds/${application.paymentRefundProcessId}/${action}`, { method: 'POST' });
      setRefundAlert({ message: result.message, kind: 'success' });
      onReservationChanged?.();
    } catch (failure) {
      setRefundAlert({ message: failure instanceof Error ? failure.message : '환불 결과를 확인하지 못했습니다. 같은 신청으로 다시 시도해주세요.', kind: 'error' });
    } finally {
      setRefundingTicketId(null);
      setRetry((value) => value + 1);
    }
  }

  const selectionLabel = detail?.tickets.filter((ticket) => refundSelection.includes(ticket.ticketId)).map(seatName).join(', ');

  return <dialog ref={dialogRef} className="my-ticket-detail-dialog" aria-labelledby="my-ticket-detail-title"
    onCancel={(event) => {
      event.preventDefault();
      if (isProcessing) return;
      if (refundAlert) setRefundAlert(null);
      else if (confirmCancellation) setConfirmCancellation(false);
      else if (confirmTicket) setConfirmTicket(null);
      else onClose();
    }}>
    <header><div><small>MY TICKET · 예매 번호 {reservationId}</small><h2 id="my-ticket-detail-title">예매 상세</h2></div>
      <button type="button" onClick={onClose} disabled={isProcessing} aria-label="예매 상세 닫기">×</button></header>
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
        <section><div className="my-ticket-seat-heading">
          <h3>좌석 정보 ({detail.tickets.length}매)</h3>
          {(canCancelUnpaid || canRefund) && selectedIds.length > 0 && <button className="my-ticket-refund-button" type="button"
            disabled={isProcessing} onClick={beginCancellation}>{isProcessing ? '처리 중…' : '취소 신청'}</button>}
        </div><div className="my-ticket-table-scroll"><table className="my-ticket-seat-table">
          <thead><tr><th>좌석 등급</th><th>좌석 번호</th><th>구매 금액</th><th>예매/취소</th><th>취소</th></tr></thead>
          <tbody>{detail.tickets.map((ticket) => <tr key={ticket.ticketId}><td>{ticket.grade}석</td><td>{seatName(ticket)}</td><td>{money(ticket.price)}</td><td>{ticketLabels[ticket.status] || ticket.status}</td><td>
            <input type="checkbox" aria-label={`${seatName(ticket)} 취소 선택`} checked={selectedIds.includes(ticket.ticketId)}
              disabled={isProcessing || !(canCancelUnpaid || (canRefund && ticket.status === 'PAID'))}
              onChange={(event) => selectCancellationTicket(ticket.ticketId, event.target.checked)} />
          </td></tr>)}</tbody>
        </table></div>{canCancelUnpaid && <p>입금 전에는 예매한 좌석을 모두 함께 취소할 수 있습니다.</p>}
          {!detail.tickets.length && <p>발급된 티켓이 없습니다.</p>}
          {isGatewayPayment && <section aria-label="환불 신청 이력"><h3>환불 신청 이력</h3>
            {applicationsError && <p role="alert">{applicationsError} <button type="button" onClick={() => setRetry((value) => value + 1)}>이력 다시 확인</button></p>}
            {applications.length === 0 && !applicationsError && <p>환불 신청 이력이 없습니다.</p>}
            <div className="my-ticket-refund-applications">
              {applications.map((application) => {
                const seats = application.selectedTicketIds.split(',').map((id) => {
                  const ticket = detail.tickets.find((item) => item.ticketId === Number(id));
                  return ticket ? seatName(ticket) : '-';
                });
                const bankName = refundBanks.find(([code]) => code === application.refundBankCompany)?.[1]
                  ?? application.refundBankCompany;
                const refundMethod = isBankTransfer
                  ? [bankName, application.refundAccountNumberMasked].filter(Boolean).join(' ') || '-'
                  : '결제한 카드로 취소·환불';

                return <article key={application.paymentRefundProcessId} className="my-ticket-refund-application">
                  <header className="my-ticket-refund-card-header">
                    <div>
                      <h4>신청 #{application.paymentRefundProcessId}</h4>
                      <time dateTime={application.createdAt}>{date(application.createdAt)}</time>
                    </div>
                    <span className="my-ticket-refund-status" data-status={application.status}>
                      {refundStatusLabels[application.status] || application.status}
                    </span>
                  </header>
                  <dl className="my-ticket-refund-card-details">
                    <div><dt>환불 금액</dt><dd className="my-ticket-refund-card-amount">{money(application.refundAmount)}</dd></div>
                    <div><dt>취소 수수료</dt><dd>{money(application.cancellationFeeAmount)}</dd></div>
                    <div><dt>대상 좌석</dt><dd>{seats.join(', ')}</dd></div>
                    <div><dt>환불 수단</dt><dd>{refundMethod}</dd></div>
                  </dl>
                  {!finishedRefund(application.status) && <div className="my-ticket-refund-actions">
                    <button type="button" disabled={isProcessing} onClick={() => void runApplication(application, false)}>{application.status === 'WITHDRAW_REQUESTED' ? '철회 다시 확인' : '다시 시도'}</button>
                    <button type="button" disabled={isProcessing} onClick={() => void runApplication(application, true)}>환불 신청 취소</button>
                  </div>}
                </article>;
              })}
            </div>
          </section>}
          {refundTicket && isBankTransfer && <form className="my-ticket-refund-form" onSubmit={(event) => requestRefund(refundTicket, event)}>
            <div><strong>환불 계좌 정보</strong><span>{selectionLabel || seatName(refundTicket)}</span></div>
            <label>은행<select value={bankCompany} onChange={(event) => setBankCompany(event.target.value)} disabled={isProcessing}>
              <option value="">은행 선택</option>{refundBanks.map(([code, name]) => <option key={code} value={code}>{name}</option>)}
            </select></label>
            <label>계좌번호<input value={accountNumber} onChange={(event) => setAccountNumber(event.target.value)} autoComplete="off" placeholder="숫자만 입력" disabled={isProcessing} /></label>
            <label>예금주<input value={accountHolder} onChange={(event) => setAccountHolder(event.target.value)} autoComplete="name" disabled={isProcessing} /></label>
            <div className="my-ticket-refund-actions"><button type="button" onClick={() => setRefundTicket(null)} disabled={isProcessing}>취소</button><button type="submit" disabled={isProcessing}>{isProcessing ? '처리 중…' : '환불 신청'}</button></div>
          </form>}
          <p className="my-ticket-deadline">예매 당일(한국 시간) 신청한 환불은 취소한 좌석의 예매 수수료도 반환합니다. 다음 날부터는 예매 수수료가 제외됩니다.</p>
          <p className="my-ticket-deadline">공연 취소 마감: {date(detail.reservation.cancelDeadlineAt)}</p>
          <p className="my-ticket-deadline">신규 취소는 매일 23:50:00까지 가능합니다. 기한 내 접수한 미완료 요청은 마감 이후에도 재시도할 수 있습니다.</p>
        </section>
      </>}
    <footer><button type="button" onClick={onClose} disabled={isProcessing}>닫기</button></footer>
    {confirmCancellation && <div className="my-ticket-refund-confirm-backdrop" role="presentation">
      <section className="my-ticket-refund-confirm" role="alertdialog" aria-modal="true" aria-labelledby="cancel-confirm-title" aria-describedby="cancel-confirm-message">
        <div className="my-ticket-refund-confirm-icon" aria-hidden="true">!</div>
        <h2 id="cancel-confirm-title">예매 취소 확인</h2>
        <p id="cancel-confirm-message">입금 대기 중인 예매의 좌석 {detail?.tickets.length}매를 모두 취소하시겠습니까?<br />취소 후에는 발급된 가상계좌로 입금하지 마세요.</p>
        <div className="my-ticket-refund-confirm-actions">
          <button type="button" onClick={() => setConfirmCancellation(false)}>돌아가기</button>
          <button type="button" autoFocus onClick={() => void cancelUnpaidReservation()}>예매 취소하기</button>
        </div>
      </section>
    </div>}
    {confirmTicket && <div className="my-ticket-refund-confirm-backdrop" role="presentation">
      <section className="my-ticket-refund-confirm" role="alertdialog" aria-modal="true" aria-labelledby="refund-confirm-title" aria-describedby="refund-confirm-message">
        <div className="my-ticket-refund-confirm-icon" aria-hidden="true">!</div>
        <h2 id="refund-confirm-title">환불 확인</h2>
        <p id="refund-confirm-message"><strong>{selectionLabel || seatName(confirmTicket)}</strong> 좌석을 환불하시겠습니까?<br />환불 후에는 되돌릴 수 없습니다.</p>
        <div className="my-ticket-refund-confirm-actions">
          <button type="button" onClick={() => { setConfirmTicket(null); if (!isBankTransfer) setRefundTicket(null); }}>취소</button>
          <button type="button" autoFocus onClick={() => void submitRefund(confirmTicket)}>환불하기</button>
        </div>
      </section>
    </div>}
    {refundAlert && <div className="my-ticket-refund-confirm-backdrop" role="presentation">
      <section className="my-ticket-refund-confirm" role="alertdialog" aria-modal="true" aria-labelledby="refund-alert-title" aria-describedby="refund-alert-message">
        <div className={`my-ticket-refund-confirm-icon ${refundAlert.kind}`} aria-hidden="true">{refundAlert.kind === 'success' ? '✓' : '!'}</div>
        <h2 id="refund-alert-title">{refundAlert.kind === 'success' ? '처리 결과' : '안내'}</h2>
        <p id="refund-alert-message">{refundAlert.message}</p>
        <div className="my-ticket-refund-confirm-actions single">
          <button type="button" autoFocus onClick={() => setRefundAlert(null)}>확인</button>
        </div>
      </section>
    </div>}
  </dialog>;
}
