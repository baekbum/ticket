import { useEffect, useState } from 'react';
import type { createAuthenticatedRequest } from './authenticatedRequest';
import './MyTicketPage.css';
import MyTicketDetailDialog from './MyTicketDetailDialog';

type Request = ReturnType<typeof createAuthenticatedRequest>;
type CouponFilter = 'ALL' | 'AVAILABLE' | 'USED' | 'EXPIRED';
type Reservation = {
  posterUrl: string | null; cancelDeadlineAt: string | null;
  reservationId: number; eventTitle: string; reservedDate: string;
  eventDateTime: string; venue: string; ticketCount: number; status: string;
};
type ReservationPage = {
  content: Reservation[];
  page: { number: number; totalPages: number; totalElements: number };
};
type Coupon = {
  userCouponId: number; status: string; expiresAt: string | null;
  coupon: { name: string; discountType: string; discountValue: number;
    minOrderAmount: number | null; maxDiscountAmount: number | null };
};
const reservationLabels: Record<string, string> = {
  PENDING_PAYMENT: '결제 대기', PAID: '예매 완료', PARTIALLY_CANCELLED: '부분 취소',
  CANCELLED: '취소 완료', EXPIRED: '만료',
};
const couponLabels: Record<string, string> = { ISSUED: '사용 가능', USED: '사용 완료', EXPIRED: '만료', CANCELLED: '취소됨' };
const couponFilters: { value: CouponFilter; label: string }[] = [
  { value: 'ALL', label: '전체' }, { value: 'AVAILABLE', label: '사용 가능' },
  { value: 'USED', label: '사용 완료' }, { value: 'EXPIRED', label: '만료' },
];

function message(error: unknown) {
  return error instanceof Error ? error.message : '내역을 불러오지 못했습니다. 다시 시도해주세요.';
}

function ReservationRow({ item, onOpen }: { item: Reservation; onOpen: () => void }) {
  return <article className="my-ticket-history-row">
    <div className="my-ticket-reserved-date">{item.reservedDate}</div>
    <div className="my-ticket-performance">
      {item.posterUrl ? <img src={item.posterUrl} alt="" loading="lazy" /> : <div className="my-ticket-poster-placeholder" aria-hidden="true">TICKET</div>}
      <div><strong>{item.eventTitle}</strong><p>{item.eventDateTime}</p><span>{item.venue}</span></div>
    </div>
    <dl className="my-ticket-booking-info"><div><dt>예매 번호</dt><dd>{item.reservationId}</dd></div><div><dt>관람일</dt><dd>{item.eventDateTime}</dd></div><div><dt>매수</dt><dd>{item.ticketCount}매</dd></div><div><dt>취소 마감</dt><dd>{item.cancelDeadlineAt?.replace('T', ' ') || '-'}</dd></div></dl>
    <div className="my-ticket-row-status"><strong className={item.status === 'PAID' ? 'confirmed' : ''}>{reservationLabels[item.status] || item.status}</strong><button type="button" onClick={onOpen}>예매 상세 ›</button></div>
  </article>;
}

export default function MyTicketPage({ request }: { request: Request }) {
  const [tab, setTab] = useState<'home' | 'reservation' | 'coupon'>('home');
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState('');
  const [couponFilter, setCouponFilter] = useState<CouponFilter>('AVAILABLE');
  const [reservations, setReservations] = useState<ReservationPage | null>(null);
  const [coupons, setCoupons] = useState<Coupon[]>([]);
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  const [profile, setProfile] = useState<{ name: string; userId: string } | null>(null);
  const [couponCount, setCouponCount] = useState<number | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    void request<{ name: string; userId: string }>('/client-api/api/v1/user/me', { method: 'GET', signal: controller.signal })
      .then((data) => { if (!controller.signal.aborted) setProfile(data); }).catch(() => {});
    void request<Coupon[]>('/client-api/api/v1/coupon/me?filter=AVAILABLE', { method: 'GET', signal: controller.signal })
      .then((data) => { if (!controller.signal.aborted) setCouponCount(data.length); }).catch(() => {});
    return () => controller.abort();
  }, [request]);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    setExpandedId(null);
    async function load() {
      try {
        if (tab === 'coupon') {
          const data = await request<Coupon[]>(`/client-api/api/v1/coupon/me?filter=${couponFilter}`, { method: 'GET', signal: controller.signal });
          if (!controller.signal.aborted) setCoupons(data);
        } else {
          const params = new URLSearchParams({ page: String(tab === 'home' ? 0 : page), size: tab === 'home' ? '3' : '10', sort: 'reservedAt-desc' });
          if (tab === 'reservation' && status) params.set('status', status);
          const data = await request<ReservationPage>(`/ticket/api/v1/reservation/select?${params}`, { method: 'GET', signal: controller.signal });
          if (!controller.signal.aborted) setReservations(data);
        }
      } catch (failure) {
        if (!controller.signal.aborted) setError(message(failure));
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    void load();
    return () => controller.abort();
  }, [request, tab, page, status, couponFilter, retry]);

  function changeTab(next: typeof tab) {
    setTab(next);
    setPage(0);
    setStatus('');
  }

  return <section className="my-ticket-page">
    {expandedId != null && <MyTicketDetailDialog reservationId={expandedId} request={request} onClose={() => setExpandedId(null)} />}
    <div className="my-ticket-title"><h1>마이티켓</h1></div>
    <nav className="my-ticket-tabs" aria-label="마이티켓 메뉴">
      {([{ key: 'home', label: '마이티켓 홈' }, { key: 'reservation', label: '예매/취소 내역' }, { key: 'coupon', label: '할인 쿠폰' }] as const).map((item) =>
        <button type="button" key={item.key} className={tab === item.key ? 'active-my-ticket-tab' : ''}
          aria-current={tab === item.key ? 'page' : undefined} onClick={() => changeTab(item.key)}>{item.label}</button>)}
    </nav>
    {tab === 'home' && <div className="my-ticket-overview">
      <div className="my-ticket-member"><div className="my-ticket-avatar" aria-hidden="true">◎</div><div><small>MY TICKET</small><h2>{profile?.name || profile?.userId || '나의 예매 내역'}</h2><p>예매한 공연과 티켓을 한눈에 확인하세요.</p></div></div>
      <button type="button" onClick={() => changeTab('reservation')}><strong>{loading || error ? '—' : reservations?.page.totalElements ?? 0}</strong><span>예매/취소 내역</span></button>
      <button type="button" onClick={() => changeTab('coupon')}><strong>{couponCount ?? '—'}</strong><span>보유 할인 쿠폰</span></button>
    </div>}
    <section className="my-ticket-section">
      <div className="my-ticket-section-header">
        <h2>{tab === 'coupon' ? '보유 할인 쿠폰' : tab === 'home' ? '최근 예매/취소 내역' : '예매/취소 내역'}</h2>
        {tab === 'home' && <button type="button" onClick={() => changeTab('reservation')}>전체 내역 보기</button>}
      </div>
      {tab === 'reservation' && <label className="my-ticket-filter">예매 상태
        <select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
          <option value="">전체</option>
          {Object.entries(reservationLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </label>}
      {tab === 'coupon' && <label className="my-ticket-filter">쿠폰 상태
        <select value={couponFilter} onChange={(event) => setCouponFilter(event.target.value as CouponFilter)}>
          {couponFilters.map((filter) => <option key={filter.value} value={filter.value}>{filter.label}</option>)}
        </select>
      </label>}
      {loading ? <p className="my-ticket-state" role="status">내역을 불러오는 중입니다.</p>
        : error ? <div className="my-ticket-state" role="alert"><p>{error}</p><button type="button" onClick={() => setRetry((n) => n + 1)}>다시 시도</button></div>
          : tab === 'coupon' ? <div className="ticket-history-list">
            {coupons.length === 0 && <p className="my-ticket-state">해당 조건의 할인 쿠폰이 없습니다.</p>}
            {coupons.map((item) => <article className="ticket-history-card" key={item.userCouponId}>
              <div><strong>{item.coupon.name}</strong>
                <p>{item.coupon.discountValue.toLocaleString()}{item.coupon.discountType === 'PERCENT' ? '%' : '원'} 할인</p>
                <span>최소 주문 금액 {(item.coupon.minOrderAmount ?? 0).toLocaleString()}원</span>
                {item.coupon.maxDiscountAmount != null && <p>최대 {item.coupon.maxDiscountAmount.toLocaleString()}원 할인</p>}
                <p>만료일: {item.expiresAt || '기한 없음'}</p>
              </div><em>{couponLabels[item.status] || item.status}</em>
            </article>)}
          </div> : <>
            <div className="ticket-history-list">
              {!!reservations?.content.length && <div className="my-ticket-history-heading" aria-hidden="true"><span>예매일</span><span>공연정보</span><span>예매정보</span><span>상태</span></div>}
              {!reservations?.content.length && <p className="my-ticket-state">{status ? '해당 상태의 예매 내역이 없습니다.' : '예매 내역이 없습니다.'}</p>}
              {(tab === 'home' ? reservations?.content.slice(0, 3) : reservations?.content)?.map((item) => <ReservationRow key={item.reservationId} item={item} onOpen={() => setExpandedId(item.reservationId)} />)}
            </div>
            {tab === 'reservation' && reservations && reservations.page.totalPages > 0 && <nav className="my-ticket-pagination" aria-label="예매 내역 페이지">
              <button type="button" disabled={page === 0} onClick={() => setPage((n) => n - 1)}>이전</button>
              <span>{page + 1} / {reservations.page.totalPages} · 총 {reservations.page.totalElements}건</span>
              <button type="button" disabled={page + 1 >= reservations.page.totalPages} onClick={() => setPage((n) => n + 1)}>다음</button>
            </nav>}
          </>}
    </section>
  </section>;
}
