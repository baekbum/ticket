import { FormEvent, useEffect, useRef, useState } from 'react';
import type { CSSProperties, KeyboardEvent as ReactKeyboardEvent, MouseEvent as ReactMouseEvent } from 'react';
import './App.css';

type Page =
  | 'home'
  | 'login'
  | 'signup'
  | 'findId'
  | 'findPassword'
  | 'eventDetail'
  | 'concertList'
  | 'musicalPlayList'
  | 'fanclubFanmeetingList'
  | 'classicList'
  | 'exhibitionEventList'
  | 'myTicket'
  | 'bookingWindow';
type FindIdMethod = 'phone' | 'email';
type HomeEventTab = 'festival' | 'openSoon' | 'weekly';
type ConcertSort = 'soonest' | 'latest';
type EventGenre = 'CONCERT' | 'MUSICAL_PLAY' | 'FANCLUB_FANMEETING' | 'CLASSIC' | 'EXHIBITION_EVENT';
type MyTicketTab = 'home' | 'reservation' | 'coupon';

type LoginForm = {
  userId: string;
  password: string;
};

type SignupForm = LoginForm & {
  passwordConfirm: string;
  name: string;
  phoneNumber: string;
  email: string;
  birthDate: string;
};

type LoginResponse = {
  success: boolean;
  message: string;
  name?: string;
  accessToken?: string;
  refreshToken?: string;
};

type TokenResponse = {
  accessToken: string;
  refreshToken: string;
};

type QueueEntryResponse = {
  eventId: number;
  status: 'READY' | 'WAITING' | 'SESSION_LIMIT_CONFIRM_REQUIRED' | string;
  rank: number | null;
  waitingCount: number | null;
  token: string;
  expiresInSeconds: number | null;
  estimatedEntryAt: string | null;
  activeTokenExpiresAt: string | null;
};

class ApiRequestError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

class SessionExpiredError extends Error {
  constructor() {
    super('세션이 만료되었습니다. 다시 시도해주세요.');
  }
}

type FindUserIdResponse = {
  maskedUserId: string;
};

type FindPasswordResponse = {
  resetToken: string;
};

type TicketingEvent = {
  eventGroupCode: string;
  artistName: string;
  title: string;
  posterUrl: string;
  eventStartDate: string;
  eventEndDate: string;
};

type EventDetail = {
  eventGroupCode: string;
  artistName: string;
  title: string;
  description: string;
  venue: string;
  venueAddress: string;
  posterUrl: string;
  eventDateRange: string;
  saleStartAt: string;
  saleEndAt: string;
  runningMinutes: number;
  ageLimit: number;
  totalSeats: number;
  availableSeats: number;
  status: 'ON_SALE' | 'SALE_ENDED' | 'SOLD_OUT' | 'CLOSED' | 'CANCELLED';
  maxTicketsPerPerson: number;
  ticketLimitScope: 'PER_EVENT' | 'PER_GROUP';
  bookingStatus: 'ON_SALE' | 'OPEN_SOON' | 'CLOSED';
  bookingMessage: string;
  schedules: EventSchedule[];
};

type EventSchedule = {
  eventId: number;
  eventDateTime: string;
  availableSeats: number;
  status: 'ON_SALE' | 'SALE_ENDED' | 'SOLD_OUT' | 'CLOSED' | 'CANCELLED';
  seatPrices: EventSeatPrice[];
};

type EventSeatPrice = {
  areaName: string;
  grade: 'VIP' | 'R' | 'S' | 'A';
  price: number;
};

type AreaResponse = {
  areaId: number;
  eventId: number;
  eventTitle?: string;
  areaName: string;
  layoutKey: string;
  grade: EventSeatPrice['grade'];
  price: number;
  status: string;
};

type EventLayoutResponse = {
  layoutId: number;
  eventId: number;
  originalFileName: string;
  svgText: string;
};

type SeatResponse = {
  seatId: number;
  zone: string;
  seatRow: number;
  seatCol: number;
  seatName: string;
  grade: EventSeatPrice['grade'];
  price: number;
  status: 'AVAILABLE' | 'RESERVED' | 'LOCKED' | string;
  positionX?: number;
  positionY?: number;
  seatWidth?: number;
  seatHeight?: number;
  rotation?: number;
  eventId: number;
  areaId: number;
  areaName?: string;
};

type SeatInfo = {
  id: number;
  zone: string;
  row: number;
  col: number;
};

type SeatOccupyResponse = {
  orderId: string;
  eventId: number;
  userId: string;
  seats: SeatInfo[];
  expiresAt: string;
};

type CheckoutPrepareResponse = {
  eventId: number;
  orderId: string;
  seats: SeatInfo[];
  idempotencyKey: string;
  prepared: boolean;
  preparedAt: string;
};

type CouponDiscountType = 'FIXED_AMOUNT' | 'PERCENT' | string;

type UserCouponResponse = {
  userCouponId: number;
  coupon: {
    couponId: number;
    name: string;
    code: string;
    discountType: CouponDiscountType;
    discountValue: number;
    maxDiscountAmount: number | null;
    minOrderAmount: number | null;
  };
  status: 'ISSUED' | 'USED' | 'EXPIRED' | string;
  expiresAt: string | null;
};

type CouponAvailabilityResponse = {
  available: boolean;
  discountAmount: number;
  reason: string | null;
};

type PageResponse<T> = {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
};

const initialLoginForm: LoginForm = {
  userId: '',
  password: '',
};

const initialSignupForm: SignupForm = {
  userId: '',
  password: '',
  passwordConfirm: '',
  name: '',
  phoneNumber: '',
  email: '',
  birthDate: '',
};

const initialFindIdForm = {
  name: '',
  phoneNumber: '',
  email: '',
};

const initialFindPasswordForm = {
  userId: '',
  name: '',
  phoneNumber: '',
  email: '',
  password: '',
  passwordConfirm: '',
};

const savedLoginIdCookieName = 'ticksy.savedLoginId';
const categories = ['콘서트', '뮤지컬/연극', '팬클럽/팬미팅', '클래식', '전시/행사', '테마/지역', '랭킹'];
const calendarWeekdays = ['일', '월', '화', '수', '목', '금', '토'];
const recentTicketHistories = [
  {
    id: 1,
    title: '2026 IU CONCERT - HEREH WORLD TOUR ENCORE',
    date: '2026.09.12 18:00',
    venue: 'KSPO DOME',
    status: '예매완료',
  },
  {
    id: 2,
    title: 'Ticksy Live Festa',
    date: '2026.09.05 19:00',
    venue: '서울월드컵공원 평화광장',
    status: '예매완료',
  },
  {
    id: 3,
    title: '한강 재즈 브리즈',
    date: '2026.09.13 18:30',
    venue: '노들섬 라이브하우스',
    status: '취소완료',
  },
];
const recentInquiries = [
  {
    id: 1,
    title: '예매 취소 수수료가 궁금합니다.',
    date: '2026.08.29',
    status: '답변완료',
  },
  {
    id: 2,
    title: '모바일 티켓 입장 가능 여부 문의',
    date: '2026.08.27',
    status: '접수완료',
  },
];
const categoryPageConfigs: Record<
  'concertList' | 'musicalPlayList' | 'fanclubFanmeetingList' | 'classicList' | 'exhibitionEventList',
  {
  label: string;
  genre: EventGenre;
  emptyMessage: string;
  }
> = {
  concertList: {
    label: '콘서트',
    genre: 'CONCERT',
    emptyMessage: '현재 예매 가능한 콘서트가 없습니다.',
  },
  musicalPlayList: {
    label: '뮤지컬/연극',
    genre: 'MUSICAL_PLAY',
    emptyMessage: '현재 예매 가능한 뮤지컬/연극 공연이 없습니다.',
  },
  fanclubFanmeetingList: {
    label: '팬클럽/팬미팅',
    genre: 'FANCLUB_FANMEETING',
    emptyMessage: '현재 예매 가능한 팬클럽/팬미팅 공연이 없습니다.',
  },
  classicList: {
    label: '클래식',
    genre: 'CLASSIC',
    emptyMessage: '현재 예매 가능한 클래식 공연이 없습니다.',
  },
  exhibitionEventList: {
    label: '전시/행사',
    genre: 'EXHIBITION_EVENT',
    emptyMessage: '현재 예매 가능한 전시/행사가 없습니다.',
  },
};
const homeEventTabs: Array<{ key: HomeEventTab; label: string; endpoint: string; emptyMessage: string }> = [
  {
    key: 'festival',
    label: '페스티벌',
    endpoint: '/client-api/api/v1/event/cards/festival',
    emptyMessage: '판매 중인 페스티벌 공연이 없습니다.',
  },
  {
    key: 'openSoon',
    label: '오픈 예정 공연',
    endpoint: '/client-api/api/v1/event/cards/open-soon',
    emptyMessage: '10일 안에 오픈 예정인 공연이 없습니다.',
  },
  {
    key: 'weekly',
    label: '이 주의 추천공연',
    endpoint: '/client-api/api/v1/event/cards/weekly',
    emptyMessage: '2주 안에 진행되는 추천 공연이 없습니다.',
  },
];

function getPageFromLocation(): Page {
  const page = new URLSearchParams(window.location.search).get('page');

  if (
    page === 'login' ||
    page === 'signup' ||
    page === 'findId' ||
    page === 'findPassword' ||
    page === 'eventDetail' ||
    page === 'concertList' ||
    page === 'musicalPlayList' ||
    page === 'fanclubFanmeetingList' ||
    page === 'classicList' ||
    page === 'exhibitionEventList' ||
    page === 'myTicket' ||
    page === 'bookingWindow'
  ) {
    return page;
  }

  return 'home';
}

function getUrlForPage(page: Page) {
  const url = new URL(window.location.href);

  if (page === 'home') {
    url.searchParams.delete('page');
    url.searchParams.delete('eventGroupCode');
    url.searchParams.delete('eventId');
  } else if (page === 'bookingWindow') {
    url.searchParams.set('page', page);
  } else if (page !== 'eventDetail') {
    url.searchParams.set('page', page);
    url.searchParams.delete('eventGroupCode');
    url.searchParams.delete('eventId');
  } else {
    url.searchParams.set('page', page);
    url.searchParams.delete('eventId');
  }

  return `${url.pathname}${url.search}${url.hash}`;
}

function formatDateInput(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}

function getCalendarMonthFromValue(value: string) {
  const [year, month] = value.split('-').map(Number);
  const today = new Date();

  if (!year || !month) {
    return new Date(today.getFullYear(), today.getMonth(), 1);
  }

  return new Date(year, month - 1, 1);
}

function getCalendarDays(monthDate: Date) {
  const year = monthDate.getFullYear();
  const month = monthDate.getMonth();
  const firstDay = new Date(year, month, 1);
  const startDate = new Date(year, month, 1 - firstDay.getDay());

  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(startDate);
    date.setDate(startDate.getDate() + index);

    return {
      date,
      dateText: formatDateInput(date),
      isCurrentMonth: date.getMonth() === month,
    };
  });
}

function getScheduleDateLabel(eventDateTime: string) {
  const match = eventDateTime.match(/(\d{4})년\s*(\d{1,2})월\s*(\d{1,2})일/);

  if (!match) {
    return eventDateTime;
  }

  return `${match[1]}.${match[2].padStart(2, '0')}.${match[3].padStart(2, '0')}`;
}

function getScheduleTimeLabel(eventDateTime: string) {
  const match = eventDateTime.match(/(\d{1,2})시\s*(\d{1,2})분/);

  if (!match) {
    return eventDateTime;
  }

  return `${match[1].padStart(2, '0')}:${match[2].padStart(2, '0')}`;
}

function getSeatGradeLabel(grade: EventSeatPrice['grade']) {
  return `${grade}석`;
}

function formatCouponBenefit(coupon: UserCouponResponse['coupon']) {
  const benefit = coupon.discountType === 'PERCENT'
    ? `${coupon.discountValue}% 할인`
    : `${coupon.discountValue.toLocaleString()}원 할인`;
  const conditions = [
    coupon.maxDiscountAmount ? `최대 ${coupon.maxDiscountAmount.toLocaleString()}원` : '',
    coupon.minOrderAmount ? `${coupon.minOrderAmount.toLocaleString()}원 이상` : '',
  ].filter(Boolean);

  return conditions.length ? `${benefit} · ${conditions.join(' · ')}` : benefit;
}

function formatTicketingEventRange(event: TicketingEvent) {
  if (!event.eventStartDate) {
    return '';
  }

  if (!event.eventEndDate || event.eventStartDate === event.eventEndDate) {
    return event.eventStartDate;
  }

  return `${event.eventStartDate} ~ ${event.eventEndDate}`;
}

function getDistinctSeatPrices(seatPrices: EventSeatPrice[]) {
  const seatPriceMap = seatPrices.reduce<Map<EventSeatPrice['grade'], EventSeatPrice>>((priceMap, seatPrice) => {
    const currentSeatPrice = priceMap.get(seatPrice.grade);

    if (!currentSeatPrice || seatPrice.price < currentSeatPrice.price) {
      priceMap.set(seatPrice.grade, seatPrice);
    }

    return priceMap;
  }, new Map());

  return Array.from(seatPriceMap.values()).sort((firstPrice, secondPrice) =>
    secondPrice.price - firstPrice.price,
  );
}

function normalizeLayoutKey(value: string) {
  return String(value || '')
    .trim()
    .replace(/^area-2f-/, '')
    .replace(/^area-1f-/, '')
    .replace(/^area-vip-/, '')
    .replace(/^area-floor-/, '')
    .replace(/^area-/, '');
}

function getAreaKeyFromElement(element: Element) {
  return element.getAttribute('data-layout-key') || normalizeLayoutKey(element.id);
}

function getAreaDisplayName(area?: AreaResponse | null) {
  if (!area) {
    return '-';
  }

  return `${area.areaName || area.layoutKey || area.areaId} · ${getSeatGradeLabel(area.grade)}`;
}

function getDistinctAreaPrices(areas: AreaResponse[]) {
  const areaPriceMap = areas.reduce<Map<EventSeatPrice['grade'], AreaResponse>>((priceMap, area) => {
    const currentArea = priceMap.get(area.grade);

    if (!currentArea || area.price > currentArea.price) {
      priceMap.set(area.grade, area);
    }

    return priceMap;
  }, new Map());

  return Array.from(areaPriceMap.values()).sort((firstArea, secondArea) => secondArea.price - firstArea.price);
}

function getBookingEventIdFromLocation() {
  return Number(new URLSearchParams(window.location.search).get('eventId')) || 0;
}

function getBookingEventGroupCodeFromLocation() {
  return new URLSearchParams(window.location.search).get('eventGroupCode') || '';
}

function getBookingActiveTokenFromLocation() {
  return new URLSearchParams(window.location.search).get('activeToken') || '';
}

function getWaitingTokenStorageKey(eventId: number) {
  return `ticksy.waitingToken.${eventId}`;
}

function getActiveTokenStorageKey(eventId: number) {
  return `ticksy.activeToken.${eventId}`;
}

function saveWaitingToken(eventId: number, token: string) {
  if (token) {
    sessionStorage.setItem(getWaitingTokenStorageKey(eventId), token);
  }
}

function getSavedWaitingToken(eventId: number) {
  return sessionStorage.getItem(getWaitingTokenStorageKey(eventId)) || '';
}

function removeSavedWaitingToken(eventId: number) {
  sessionStorage.removeItem(getWaitingTokenStorageKey(eventId));
}

function saveActiveToken(eventId: number, token: string) {
  if (token) {
    sessionStorage.setItem(getActiveTokenStorageKey(eventId), token);
  }
}

function removeSavedActiveToken(eventId: number) {
  sessionStorage.removeItem(getActiveTokenStorageKey(eventId));
}

function wait(milliseconds: number) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, milliseconds);
  });
}

function releaseWaitingToken(eventId: number, token: string) {
  releaseQueueToken(eventId, token, 'waiting');
}

function releaseActiveToken(eventId: number, token: string) {
  releaseQueueToken(eventId, token, 'active');
}

function releaseQueueToken(eventId: number, token: string, tokenType: 'waiting' | 'active') {
  const accessToken = sessionStorage.getItem('ticksy.accessToken');

  if (!eventId || !token || !accessToken) {
    return;
  }

  fetch(`/client-api/api/v1/queue/events/${eventId}/leave`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      [tokenType === 'waiting' ? 'X-Waiting-Token' : 'X-Active-Token']: token,
    },
    keepalive: true,
  }).catch(() => undefined);
}

function buildBookingWindowUrl(eventId: number, eventGroupCode: string, activeToken: string) {
  const bookingUrl = new URL(window.location.href);
  bookingUrl.searchParams.set('page', 'bookingWindow');
  bookingUrl.searchParams.set('eventId', String(eventId));
  bookingUrl.searchParams.set('eventGroupCode', eventGroupCode);
  bookingUrl.searchParams.set('activeToken', activeToken);

  return `${bookingUrl.pathname}${bookingUrl.search}${bookingUrl.hash}`;
}

function escapeHtml(value: string) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function writeQueueWindowMessage(
  bookingWindow: Window,
  title: string,
  message: string,
  detail = '',
  animatedTitle = false,
) {
  const safeTitle = escapeHtml(title);
  const safeMessage = escapeHtml(message);
  const safeDetail = escapeHtml(detail);
  const titleDots = animatedTitle
    ? '<span class="waiting-dots" aria-hidden="true"><i>.</i><i>.</i><i>.</i></span>'
    : '';

  bookingWindow.document.open();
  bookingWindow.document.write(`
    <!doctype html>
    <html lang="ko">
      <head>
        <meta charset="UTF-8" />
        <title>Tickey 예매 대기</title>
        <style>
          body {
            align-items: center;
            background: #f5f6f8;
            color: #1f2937;
            display: flex;
            font-family: Arial, sans-serif;
            height: 100vh;
            justify-content: center;
            margin: 0;
          }
          main {
            background: #fff;
            border: 1px solid #e5e7eb;
            border-radius: 18px;
            box-shadow: 0 18px 45px rgba(15, 23, 42, 0.12);
            max-width: 420px;
            padding: 32px;
            text-align: center;
            width: calc(100% - 48px);
          }
          h1 { font-size: 22px; margin: 0 0 12px; }
          p { line-height: 1.6; margin: 0; }
          small { color: #6b7280; display: block; margin-top: 12px; }
          .waiting-dots {
            display: inline-flex;
            gap: 1px;
            margin-left: 2px;
            width: 24px;
          }
          .waiting-dots i {
            animation: waveDot 1.8s ease-in-out infinite;
            display: inline-block;
            font-style: normal;
          }
          .waiting-dots i:nth-child(2) {
            animation-delay: 0.18s;
          }
          .waiting-dots i:nth-child(3) {
            animation-delay: 0.36s;
          }
          @keyframes waveDot {
            0%, 70%, 100% {
              opacity: 0.35;
              transform: translateY(0);
            }
            35% {
              opacity: 1;
              transform: translateY(-5px);
            }
          }
        </style>
      </head>
      <body>
        <main>
          <h1>${safeTitle}${titleDots}</h1>
          <p>${safeMessage}</p>
          ${safeDetail ? `<small>${safeDetail}</small>` : ''}
        </main>
      </body>
    </html>
  `);
  bookingWindow.document.close();
}

function isSeatAvailable(seat: SeatResponse) {
  return String(seat.status).toUpperCase() === 'AVAILABLE';
}

function getSeatKey(seat: SeatResponse) {
  return `${seat.zone}-${seat.seatRow}-${seat.seatCol}`;
}

function getCookie(name: string) {
  const cookieValue = document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith(`${name}=`))
    ?.split('=')
    .slice(1)
    .join('=') || '';

  try {
    return decodeURIComponent(cookieValue);
  } catch {
    return '';
  }
}

function setCookie(name: string, value: string, maxAgeSeconds: number) {
  document.cookie = `${name}=${encodeURIComponent(value)}; path=/; max-age=${maxAgeSeconds}; SameSite=Lax`;
}

function deleteCookie(name: string) {
  document.cookie = `${name}=; path=/; max-age=0; SameSite=Lax`;
}

function App() {
  const [page, setPage] = useState<Page>(() => getPageFromLocation());
  const [selectedEventGroupCode, setSelectedEventGroupCode] = useState(
    () => new URLSearchParams(window.location.search).get('eventGroupCode') || '',
  );
  const [loginUserName, setLoginUserName] = useState(
    () => sessionStorage.getItem('ticksy.userName') || '',
  );
  const isFullAuthPage =
    page === 'login' ||
    page === 'signup' ||
    page === 'findId' ||
    page === 'findPassword' ||
    page === 'bookingWindow';

  useEffect(() => {
    window.history.replaceState({ page: getPageFromLocation() }, '', window.location.href);

    function handlePopState() {
      setPage(getPageFromLocation());
      setSelectedEventGroupCode(new URLSearchParams(window.location.search).get('eventGroupCode') || '');
    }

    window.addEventListener('popstate', handlePopState);

    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  function navigateToPage(nextPage: Page) {
    setPage(nextPage);

    const nextUrl = getUrlForPage(nextPage);
    const currentUrl = `${window.location.pathname}${window.location.search}${window.location.hash}`;

    if (nextUrl === currentUrl) {
      return;
    }

    if (page === 'login' && nextPage === 'home') {
      window.history.replaceState({ page: nextPage }, '', nextUrl);
      return;
    }

    window.history.pushState({ page: nextPage }, '', nextUrl);
  }

  function navigateToEventDetail(eventGroupCode: string) {
    const url = new URL(window.location.href);
    url.searchParams.set('page', 'eventDetail');
    url.searchParams.set('eventGroupCode', eventGroupCode);

    setPage('eventDetail');
    setSelectedEventGroupCode(eventGroupCode);
    window.history.pushState(
      { page: 'eventDetail', eventGroupCode },
      '',
      `${url.pathname}${url.search}${url.hash}`,
    );
  }

  async function logout() {
    const refreshToken = sessionStorage.getItem('ticksy.refreshToken');

    try {
      if (refreshToken) {
        await request<void>('/client-api/api/v1/auth/logout', {
          method: 'POST',
          headers: {
            'Authorization-Refresh': `Bearer ${refreshToken}`,
          },
        });
      }
    } finally {
      clearLoginStorage();
      setLoginUserName('');
      navigateToPage('home');
    }
  }

  return (
    <main className="app-shell">
      {!isFullAuthPage && (
        <Header
          currentPage={page}
          loginUserName={loginUserName}
          onLogout={logout}
          onNavigate={navigateToPage}
        />
      )}
      {page === 'home' && <HomePage onSelectEvent={navigateToEventDetail} />}
      {(page === 'concertList' ||
        page === 'musicalPlayList' ||
        page === 'fanclubFanmeetingList' ||
        page === 'classicList' ||
        page === 'exhibitionEventList') && (
        <CategoryEventListPage
          key={page}
          config={categoryPageConfigs[page]}
          onSelectEvent={navigateToEventDetail}
        />
      )}
      {page === 'eventDetail' && (
        <EventDetailPage
          eventGroupCode={selectedEventGroupCode}
          isLoggedIn={Boolean(loginUserName)}
          onNavigate={navigateToPage}
        />
      )}
      {page === 'myTicket' && <MyTicketPage />}
      {page === 'bookingWindow' && <BookingWindowPage />}
      {page === 'login' && <LoginPage onLoginSuccess={setLoginUserName} onNavigate={navigateToPage} />}
      {page === 'signup' && <SignupPage onNavigate={navigateToPage} />}
      {page === 'findId' && <FindIdPage onNavigate={navigateToPage} />}
      {page === 'findPassword' && <FindPasswordPage onNavigate={navigateToPage} />}
      {!isFullAuthPage && <SiteFooter />}
      {!isFullAuthPage && <TopButton />}
    </main>
  );
}

function Header({
  currentPage,
  loginUserName,
  onLogout,
  onNavigate,
}: {
  currentPage: Page;
  loginUserName: string;
  onLogout: () => void;
  onNavigate: (page: Page) => void;
}) {
  return (
    <header className="site-header">
      <div className="top-menu">
        {loginUserName ? (
          <span className="welcome-message">
            <em>{loginUserName}</em>님 환영합니다
          </span>
        ) : (
          <button
            className={currentPage === 'login' ? 'active-link' : ''}
            type="button"
            onClick={() => onNavigate('login')}
          >
            로그인
          </button>
        )}
        <span aria-hidden="true">|</span>
        {loginUserName ? (
          <button type="button" onClick={onLogout}>
            로그아웃
          </button>
        ) : (
          <button
            className={currentPage === 'signup' ? 'active-link' : ''}
            type="button"
            onClick={() => onNavigate('signup')}
          >
            회원가입
          </button>
        )}
        <span aria-hidden="true">|</span>
        <button type="button">고객센터</button>
        <span aria-hidden="true">|</span>
        <button type="button">이용안내</button>
      </div>

      <div className="brand-row">
        <button className="brand" type="button" onClick={() => onNavigate('home')}>
          <span className="brand-dot" aria-hidden="true" />
          <span>Ticksy</span>
        </button>
        <div className="search-bar">
          <input aria-label="공연 검색" placeholder="공연, 아티스트, 장소를 검색하세요" />
          <button type="button" aria-label="검색">
            ⌕
          </button>
        </div>
      </div>

      <nav className="category-nav" aria-label="공연 카테고리">
        {categories.map((category) => (
          <button
            className={
              (currentPage === 'concertList' && category === '콘서트') ||
              (currentPage === 'musicalPlayList' && category === '뮤지컬/연극') ||
              (currentPage === 'fanclubFanmeetingList' && category === '팬클럽/팬미팅') ||
              (currentPage === 'classicList' && category === '클래식') ||
              (currentPage === 'exhibitionEventList' && category === '전시/행사')
                ? 'active-category'
                : ''
            }
            type="button"
            key={category}
            onClick={() => {
              if (category === '콘서트') {
                onNavigate('concertList');
              } else if (category === '뮤지컬/연극') {
                onNavigate('musicalPlayList');
              } else if (category === '팬클럽/팬미팅') {
                onNavigate('fanclubFanmeetingList');
              } else if (category === '클래식') {
                onNavigate('classicList');
              } else if (category === '전시/행사') {
                onNavigate('exhibitionEventList');
              }
            }}
          >
            {category}
          </button>
        ))}
        <button
          className={currentPage === 'myTicket' ? 'my-ticket active-category' : 'my-ticket'}
          type="button"
          onClick={() => onNavigate(loginUserName ? 'myTicket' : 'login')}
        >
          마이티켓
        </button>
      </nav>
    </header>
  );
}

function HomePage({ onSelectEvent }: { onSelectEvent: (eventGroupCode: string) => void }) {
  const [soonestOnSaleEvents, setSoonestOnSaleEvents] = useState<TicketingEvent[]>([]);
  const [homeEventTab, setHomeEventTab] = useState<HomeEventTab>('festival');
  const [homeTabEvents, setHomeTabEvents] = useState<TicketingEvent[]>([]);
  const [posterStartIndex, setPosterStartIndex] = useState(0);
  const [visiblePosterCount, setVisiblePosterCount] = useState(5);
  const [isPosterSliding, setIsPosterSliding] = useState(false);
  const activePosterCount = Math.min(visiblePosterCount, soonestOnSaleEvents.length);
  const displayPosterCount =
    soonestOnSaleEvents.length > activePosterCount ? activePosterCount + 1 : activePosterCount;
  const visiblePosters =
    soonestOnSaleEvents.length > 0
      ? Array.from({ length: displayPosterCount }, (_, index) => {
          return soonestOnSaleEvents[(posterStartIndex + index) % soonestOnSaleEvents.length];
        })
      : [];
  const posterGapCount = Math.max(activePosterCount - 1, 0);
  const posterRailStyle = {
    '--poster-card-width': `calc((100% - ${posterGapCount * 22}px) / ${activePosterCount || 1})`,
  } as CSSProperties;

  useEffect(() => {
    async function loadSoonestOnSaleEvents() {
      try {
        const events = await request<TicketingEvent[]>('/client-api/api/v1/event/on-sale/soonest', {
          method: 'GET',
        });
        setSoonestOnSaleEvents(events);
        setPosterStartIndex(0);
      } catch {
        setSoonestOnSaleEvents([]);
      }
    }

    loadSoonestOnSaleEvents();
  }, []);

  useEffect(() => {
    async function loadHomeTabEvents() {
      const selectedTab = homeEventTabs.find((tab) => tab.key === homeEventTab);

      if (!selectedTab) {
        setHomeTabEvents([]);
        return;
      }

      try {
        const events = await request<TicketingEvent[]>(selectedTab.endpoint, {
          method: 'GET',
        });
        setHomeTabEvents(events);
      } catch {
        setHomeTabEvents([]);
      }
    }

    loadHomeTabEvents();
  }, [homeEventTab]);

  useEffect(() => {
    function syncVisiblePosterCount() {
      if (window.innerWidth <= 640) {
        setVisiblePosterCount(1);
        return;
      }

      if (window.innerWidth <= 1020) {
        setVisiblePosterCount(2);
        return;
      }

      setVisiblePosterCount(5);
    }

    syncVisiblePosterCount();
    window.addEventListener('resize', syncVisiblePosterCount);

    return () => window.removeEventListener('resize', syncVisiblePosterCount);
  }, []);

  useEffect(() => {
    if (soonestOnSaleEvents.length <= activePosterCount || isPosterSliding) {
      return;
    }

    const rotationTimer = window.setInterval(() => {
      movePosters('next');
    }, 5000);

    return () => window.clearInterval(rotationTimer);
  }, [activePosterCount, isPosterSliding, soonestOnSaleEvents.length]);

  function movePosters(direction: 'prev' | 'next') {
    if (soonestOnSaleEvents.length <= 1 || isPosterSliding) {
      return;
    }

    if (soonestOnSaleEvents.length <= activePosterCount) {
      setPosterStartIndex((currentIndex) => {
        if (direction === 'next') {
          return (currentIndex + 1) % soonestOnSaleEvents.length;
        }

        return (currentIndex - 1 + soonestOnSaleEvents.length) % soonestOnSaleEvents.length;
      });
      return;
    }

    if (direction === 'prev') {
      setPosterStartIndex((currentIndex) => (currentIndex - 1 + soonestOnSaleEvents.length) % soonestOnSaleEvents.length);
      return;
    }

    setIsPosterSliding(true);
    window.setTimeout(() => {
      setPosterStartIndex((currentIndex) => {
      if (direction === 'next') {
        return (currentIndex + 1) % soonestOnSaleEvents.length;
      }

      return (currentIndex - 1 + soonestOnSaleEvents.length) % soonestOnSaleEvents.length;
      });
      setIsPosterSliding(false);
    }, 420);
  }

  function formatEventDateRange(event: TicketingEvent) {
    if (!event.eventStartDate) {
      return '';
    }

    if (!event.eventEndDate || event.eventStartDate === event.eventEndDate) {
      return event.eventStartDate;
    }

    return `${event.eventStartDate} ~ ${event.eventEndDate}`;
  }

  function handlePosterClick(event: TicketingEvent) {
    onSelectEvent(event.eventGroupCode);
  }

  const selectedHomeEventTab = homeEventTabs.find((tab) => tab.key === homeEventTab);

  return (
    <>
      <section className="poster-carousel" aria-label="주요 공연">
        <button
          className="carousel-arrow carousel-arrow-left"
          type="button"
          aria-label="이전 공연 보기"
          onClick={() => movePosters('prev')}
        >
          <ArrowIcon direction="left" />
        </button>
        <div className="poster-viewport">
          <div
            className={`poster-rail${isPosterSliding ? ' poster-rail-sliding' : ''}`}
            style={posterRailStyle}
          >
          {visiblePosters.map((event) => (
            <button
              className="poster-card"
              type="button"
              key={event.eventGroupCode}
              onClick={() => handlePosterClick(event)}
              aria-label={`${event.title} 공연 상세 보기`}
            >
              <div className="poster-art">
                <img src={event.posterUrl} alt={`${event.title} 포스터`} />
              </div>
              <strong>{event.title}</strong>
              <p>{event.artistName}</p>
              <small>{formatEventDateRange(event)}</small>
            </button>
          ))}
          {visiblePosters.length === 0 && (
            <div className="poster-empty">판매 중인 예정 공연이 없습니다.</div>
          )}
          </div>
        </div>
        <button
          className="carousel-arrow carousel-arrow-right"
          type="button"
          aria-label="다음 공연 보기"
          onClick={() => movePosters('next')}
        >
          <ArrowIcon direction="right" />
        </button>
      </section>

      <section className="content-grid">
        <article className="wide-panel">
          <div className="section-tabs">
            {homeEventTabs.map((tab) => (
              <button
                className={homeEventTab === tab.key ? 'active-tab' : ''}
                type="button"
                key={tab.key}
                onClick={() => setHomeEventTab(tab.key)}
              >
                {tab.label}
              </button>
            ))}
          </div>
          <div className="mini-poster-grid">
            {homeTabEvents.map((event, index) => (
              <div className="mini-poster" key={event.eventGroupCode}>
                <button
                  className="mini-poster-image"
                  type="button"
                  onClick={() => handlePosterClick(event)}
                  aria-label={`${event.title} 공연 상세 보기`}
                >
                  <img src={event.posterUrl} alt="" />
                  <span className="mini-poster-rank">{index + 1}</span>
                </button>
                <strong>{event.title}</strong>
                <span className="mini-poster-description">{event.artistName}</span>
              </div>
            ))}
            {homeTabEvents.length === 0 && (
              <p className="mini-poster-empty">
                {selectedHomeEventTab?.emptyMessage || '표시할 공연이 없습니다.'}
              </p>
            )}
          </div>
        </article>

      </section>
    </>
  );
}

function CategoryEventListPage({
  config,
  onSelectEvent,
}: {
  config: {
    label: string;
    genre: EventGenre;
    emptyMessage: string;
  };
  onSelectEvent: (eventGroupCode: string) => void;
}) {
  const eventPageSize = 5;
  const [events, setEvents] = useState<TicketingEvent[]>([]);
  const [eventSort, setEventSort] = useState<ConcertSort>('soonest');
  const [eventPage, setEventPage] = useState(0);
  const [hasMoreEvents, setHasMoreEvents] = useState(true);
  const [isLoading, setIsLoading] = useState(true);
  const eventLoaderRef = useRef<HTMLDivElement | null>(null);

  function changeEventSort(nextSort: ConcertSort) {
    if (nextSort === eventSort) {
      return;
    }

    setEvents([]);
    setEventPage(0);
    setHasMoreEvents(true);
    setEventSort(nextSort);
  }

  useEffect(() => {
    setEvents([]);
    setEventPage(0);
    setHasMoreEvents(true);
  }, [config.genre]);

  useEffect(() => {
    async function loadEvents() {
      setIsLoading(true);

      try {
        const nextEvents = await request<TicketingEvent[]>(
          `/client-api/api/v1/event/cards/genre/${config.genre}?sort=${eventSort}&page=${eventPage}&size=${eventPageSize}`,
          {
            method: 'GET',
          },
        );
        setEvents((currentEvents) => {
          if (eventPage === 0) {
            return nextEvents;
          }

          const eventMap = new Map(currentEvents.map((event) => [event.eventGroupCode, event]));
          nextEvents.forEach((event) => eventMap.set(event.eventGroupCode, event));

          return Array.from(eventMap.values());
        });
        setHasMoreEvents(nextEvents.length === eventPageSize);
      } catch {
        if (eventPage === 0) {
          setEvents([]);
        }
        setHasMoreEvents(false);
      } finally {
        setIsLoading(false);
      }
    }

    loadEvents();
  }, [config.genre, eventPage, eventSort]);

  useEffect(() => {
    const loaderElement = eventLoaderRef.current;

    if (!loaderElement || !hasMoreEvents) {
      return;
    }

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && !isLoading) {
          setEventPage((currentPage) => currentPage + 1);
        }
      },
      { rootMargin: '180px 0px' },
    );

    observer.observe(loaderElement);

    return () => observer.disconnect();
  }, [hasMoreEvents, isLoading]);

  return (
    <section className="category-list-page">
      <div className="category-list-header">
        <h1>{config.label}</h1>
        <div className="category-sort-tabs" aria-label={`${config.label} 정렬`}>
          <button
            className={eventSort === 'soonest' ? 'active-sort' : ''}
            type="button"
            onClick={() => changeEventSort('soonest')}
          >
            공연 임박순
          </button>
          <button
            className={eventSort === 'latest' ? 'active-sort' : ''}
            type="button"
            onClick={() => changeEventSort('latest')}
          >
            최신순
          </button>
        </div>
      </div>

      <div className="concert-list">
        {isLoading && events.length === 0 && <p className="concert-list-empty">{config.label} 목록을 불러오는 중입니다.</p>}
        {!isLoading && events.length === 0 && (
          <p className="concert-list-empty">{config.emptyMessage}</p>
        )}
        {events.map((event) => (
          <button
            className="concert-list-card"
            type="button"
            key={event.eventGroupCode}
            onClick={() => onSelectEvent(event.eventGroupCode)}
          >
            <span className="concert-list-poster">
              <img src={event.posterUrl} alt={`${event.title} 포스터`} />
            </span>
            <span className="concert-list-info">
              <strong>{event.title}</strong>
              <span>{event.artistName}</span>
              <small>{formatTicketingEventRange(event)}</small>
            </span>
          </button>
        ))}
        <div className="concert-list-loader" ref={eventLoaderRef}>
          {isLoading && events.length > 0 && `${config.label} 공연을 더 불러오는 중입니다.`}
        </div>
      </div>
    </section>
  );
}

function MyTicketPage() {
  const [activeMyTicketTab, setActiveMyTicketTab] = useState<MyTicketTab>('home');
  const myTicketTabs: Array<{ key: MyTicketTab; label: string }> = [
    { key: 'home', label: '마이티켓 홈' },
    { key: 'reservation', label: '예매 확인/취소' },
    { key: 'coupon', label: '할인 쿠폰' },
  ];

  return (
    <section className="my-ticket-page">
      <div className="my-ticket-title">
        <h1>마이티켓</h1>
      </div>

      <nav className="my-ticket-tabs" aria-label="마이티켓 메뉴">
        {myTicketTabs.map((tab) => (
          <button
            className={activeMyTicketTab === tab.key ? 'active-my-ticket-tab' : ''}
            type="button"
            key={tab.key}
            onClick={() => setActiveMyTicketTab(tab.key)}
          >
            {tab.label}
          </button>
        ))}
      </nav>

      <section className="my-ticket-section">
        <div className="my-ticket-section-header">
          <h2>최근 예매/취소 내역</h2>
          <button type="button">더보기</button>
        </div>
        <div className="ticket-history-list">
          {recentTicketHistories.slice(0, 3).map((history) => (
            <article className="ticket-history-card" key={history.id}>
              <div>
                <strong>{history.title}</strong>
                <p>{history.date}</p>
                <span>{history.venue}</span>
              </div>
              <em className={history.status === '예매완료' ? 'confirmed' : 'cancelled'}>
                {history.status}
              </em>
            </article>
          ))}
        </div>
      </section>

      <section className="my-ticket-section">
        <div className="my-ticket-section-header">
          <h2>최근 1:1 문의</h2>
          <button type="button">더보기</button>
        </div>
        <div className="inquiry-list">
          {recentInquiries.map((inquiry) => (
            <article className="inquiry-card" key={inquiry.id}>
              <div>
                <strong>{inquiry.title}</strong>
                <p>{inquiry.date}</p>
              </div>
              <em>{inquiry.status}</em>
            </article>
          ))}
        </div>
      </section>
    </section>
  );
}

function EventDetailPage({
  eventGroupCode,
  isLoggedIn,
  onNavigate,
}: {
  eventGroupCode: string;
  isLoggedIn: boolean;
  onNavigate: (page: Page) => void;
}) {
  const [eventDetail, setEventDetail] = useState<EventDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [selectedScheduleDate, setSelectedScheduleDate] = useState('');
  const [selectedScheduleId, setSelectedScheduleId] = useState<number | null>(null);
  const [isScheduleAlertOpen, setIsScheduleAlertOpen] = useState(false);
  const [isLoginRequiredAlertOpen, setIsLoginRequiredAlertOpen] = useState(false);
  const [isQueueEntering, setIsQueueEntering] = useState(false);
  const [queueEntry, setQueueEntry] = useState<QueueEntryResponse | null>(null);
  const [queueErrorMessage, setQueueErrorMessage] = useState('');

  useEffect(() => {
    async function loadEventDetail() {
      if (!eventGroupCode) {
        setEventDetail(null);
        setIsLoading(false);
        return;
      }

      setIsLoading(true);

      try {
        const detail = await request<EventDetail>(
          `/client-api/api/v1/event/select/group/${encodeURIComponent(eventGroupCode)}`,
          {
            method: 'GET',
          },
        );
        setEventDetail(detail);
        setSelectedScheduleDate('');
        setSelectedScheduleId(null);
      } catch {
        setEventDetail(null);
      } finally {
        setIsLoading(false);
      }
    }

    loadEventDetail();
  }, [eventGroupCode]);

  if (isLoading) {
    return <section className="event-detail-state">공연 정보를 불러오는 중입니다.</section>;
  }

  if (!eventDetail) {
    return (
      <section className="event-detail-state">
        <p>공연 정보를 찾을 수 없습니다.</p>
        <button type="button" onClick={() => onNavigate('home')}>
          메인으로 돌아가기
        </button>
      </section>
    );
  }

  const isBookable = eventDetail.bookingStatus === 'ON_SALE';
  const scheduleGroups = eventDetail.schedules.reduce<
    Array<{ dateLabel: string; schedules: EventSchedule[] }>
  >((groups, schedule) => {
    const dateLabel = getScheduleDateLabel(schedule.eventDateTime);
    const existingGroup = groups.find((group) => group.dateLabel === dateLabel);

    if (existingGroup) {
      existingGroup.schedules.push(schedule);
      return groups;
    }

    return [...groups, { dateLabel, schedules: [schedule] }];
  }, []);
  const activeScheduleDate = selectedScheduleDate || scheduleGroups[0]?.dateLabel || '';
  const activeSchedules =
    scheduleGroups.find((group) => group.dateLabel === activeScheduleDate)?.schedules || [];

  function selectScheduleDate(dateLabel: string) {
    if (!isBookable) {
      return;
    }

    const firstSchedule = scheduleGroups
      .find((group) => group.dateLabel === dateLabel)
      ?.schedules.find((schedule) => schedule.status === 'ON_SALE');

    setSelectedScheduleDate(dateLabel);
    setSelectedScheduleId(firstSchedule?.eventId || null);
  }

  function toggleSchedule(schedule: EventSchedule) {
    if (!isBookable || schedule.status !== 'ON_SALE') {
      return;
    }

    setSelectedScheduleDate(getScheduleDateLabel(schedule.eventDateTime));
    setSelectedScheduleId((currentScheduleId) =>
      currentScheduleId === schedule.eventId ? null : schedule.eventId,
    );
  }

  async function clickBookingButton() {
    if (!isBookable) {
      return;
    }

    if (selectedScheduleId === null) {
      setIsScheduleAlertOpen(true);
      return;
    }

    if (!isLoggedIn) {
      setIsLoginRequiredAlertOpen(true);
      return;
    }

    const bookingWindow = window.open(
      '',
      'ticksy-booking',
      'width=1180,height=820,menubar=no,toolbar=no,location=no,status=no,scrollbars=yes,resizable=yes',
    );

    if (!bookingWindow) {
      setQueueErrorMessage('팝업이 차단되었습니다. 브라우저에서 팝업 허용 후 다시 시도해주세요.');
      return;
    }

    writeQueueWindowMessage(bookingWindow, '대기열 등록 중', '예매 대기열에 등록하고 있습니다.');
    setIsQueueEntering(true);
    setQueueEntry(null);
    setQueueErrorMessage('');

    try {
      let queueResponse = await enterBookingQueue(selectedScheduleId);
      if (queueResponse.status === 'SESSION_LIMIT_CONFIRM_REQUIRED') {
        const shouldContinue = window.confirm(
          '이미 이 공연에 접속 중인 예매 세션이 4개입니다. 계속하면 기존 접속 중 하나가 끊길 수 있습니다. 계속하시겠습니까?',
        );

        if (!shouldContinue) {
          bookingWindow.close();
          return;
        }

        queueResponse = await enterBookingQueue(selectedScheduleId, true);
      }

      setQueueEntry(queueResponse);
      saveWaitingToken(selectedScheduleId, queueResponse.token);

      while (queueResponse.status !== 'READY') {
        if (bookingWindow.closed) {
          releaseWaitingToken(selectedScheduleId, queueResponse.token);
          return;
        }

        writeQueueWindowMessage(
          bookingWindow,
          '예매 대기 중',
          `현재 ${queueResponse.rank ?? '-'}번째로 대기 중입니다.`,
          `전체 대기 인원: ${queueResponse.waitingCount ?? '-'}명`,
          true,
        );
        await wait(3000);

        if (bookingWindow.closed) {
          releaseWaitingToken(selectedScheduleId, queueResponse.token);
          return;
        }

        queueResponse = await fetchBookingQueueStatus(selectedScheduleId, queueResponse.token);
        setQueueEntry(queueResponse);
        if (queueResponse.status !== 'READY') {
          saveWaitingToken(selectedScheduleId, queueResponse.token);
        }
      }

      removeSavedWaitingToken(selectedScheduleId);
      saveActiveToken(selectedScheduleId, queueResponse.token);
      writeQueueWindowMessage(bookingWindow, '입장 준비 완료', '좌석 선택 화면으로 이동합니다.');
      bookingWindow.location.replace(buildBookingWindowUrl(selectedScheduleId, eventGroupCode, queueResponse.token));
    } catch (error) {
      if (error instanceof SessionExpiredError) {
        bookingWindow.close();
        alert(error.message);
        return;
      }

      const message = error instanceof Error ? error.message : '대기열 등록 중 오류가 발생했습니다.';
      setQueueErrorMessage(message);
      writeQueueWindowMessage(bookingWindow, '대기열 등록 실패', message);
      removeSavedWaitingToken(selectedScheduleId);
      window.setTimeout(() => bookingWindow.close(), 1500);
    } finally {
      setIsQueueEntering(false);
    }
  }

  async function enterBookingQueue(eventId: number, force = false) {
    const headers: Record<string, string> = {};
    if (force) {
      headers['X-Queue-Force-Enter'] = 'true';
    }

    return request<QueueEntryResponse>(
      `/client-api/api/v1/queue/events/${eventId}/enter`,
      {
        method: 'POST',
        headers: Object.keys(headers).length > 0 ? headers : undefined,
      },
    );
  }

  async function fetchBookingQueueStatus(eventId: number, token: string) {
    return request<QueueEntryResponse>(
      `/client-api/api/v1/queue/events/${eventId}/status`,
      {
        method: 'GET',
        headers: { 'X-Waiting-Token': token },
      },
    );
  }

  return (
    <section className="event-detail-page">
      <div className="event-detail-header">
        <p>공연 상세</p>
        <h1>{eventDetail.title}</h1>
      </div>

      <article className="event-booking-panel">
        <div className="event-booking-top">
          <div className="event-detail-poster">
            <img src={eventDetail.posterUrl} alt={`${eventDetail.title} 포스터`} />
          </div>

          <div className="event-detail-info">
            <h2>{eventDetail.title}</h2>
            <dl>
              <div>
                <dt>아티스트</dt>
                <dd>{eventDetail.artistName}</dd>
              </div>
              <div>
                <dt>공연기간</dt>
                <dd>{eventDetail.eventDateRange}</dd>
              </div>
              <div>
                <dt>공연장</dt>
                <dd>{eventDetail.venue}</dd>
              </div>
              <div>
                <dt>주소</dt>
                <dd>{eventDetail.venueAddress}</dd>
              </div>
              <div>
                <dt>공연시간</dt>
                <dd>{eventDetail.runningMinutes}분</dd>
              </div>
              <div>
                <dt>관람등급</dt>
                <dd>{eventDetail.ageLimit === 0 ? '전체 관람가' : `${eventDetail.ageLimit}세 이상`}</dd>
              </div>
            </dl>
          </div>
        </div>

        <div className="event-schedule-box">
          <div className="event-schedule-picker">
            <div className="event-schedule-column">
              <strong className="event-schedule-column-title">공연 날짜</strong>
              <div className="event-schedule-date-list" aria-label="공연 날짜 선택">
                {scheduleGroups.map((group) => (
                  <button
                    className={activeScheduleDate === group.dateLabel ? 'selected' : ''}
                    disabled={!isBookable}
                    type="button"
                    key={group.dateLabel}
                    onClick={() => selectScheduleDate(group.dateLabel)}
                  >
                    {group.dateLabel}
                  </button>
                ))}
              </div>
            </div>
            <div className="event-schedule-column">
              <strong className="event-schedule-column-title">시간</strong>
              <div className="event-schedule-time-list" aria-label="공연 시간 선택">
                {activeSchedules.map((schedule) => (
                  <button
                    className={selectedScheduleId === schedule.eventId ? 'selected' : ''}
                    disabled={!isBookable || schedule.status !== 'ON_SALE'}
                    type="button"
                    key={schedule.eventId}
                    onClick={() => toggleSchedule(schedule)}
                  >
                    <span>{getScheduleTimeLabel(schedule.eventDateTime)}</span>
                  </button>
                ))}
              </div>
            </div>
            <div className="event-schedule-column">
              <strong className="event-schedule-column-title">좌석 가격 정보</strong>
              <div className="event-seat-price-list">
                {selectedScheduleId === null && (
                  <p>시간을 선택하면 가격 정보가 표시됩니다.</p>
                )}
                {eventDetail.schedules.map((schedule) => {
                  const seatPrices = getDistinctSeatPrices(schedule.seatPrices);
                  const isSelected = schedule.eventId === selectedScheduleId;

                  return (
                    <div
                      className={isSelected ? 'event-seat-price-panel active' : 'event-seat-price-panel'}
                      key={schedule.eventId}
                      hidden={!isSelected}
                    >
                      {seatPrices.length > 0 ? (
                        seatPrices.map((seatPrice) => (
                          <div
                            className="event-seat-price-item"
                            key={`${schedule.eventId}-${seatPrice.grade}`}
                          >
                            <span>{getSeatGradeLabel(seatPrice.grade)}</span>
                            <strong>{seatPrice.price.toLocaleString()}원</strong>
                          </div>
                        ))
                      ) : (
                        <p>좌석 가격 정보가 없습니다.</p>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          </div>
        </div>

        <div className="event-booking-action-row">
          <button
            className={isBookable ? 'booking-action-button' : 'booking-action-button disabled'}
            disabled={!isBookable}
            type="button"
            onClick={clickBookingButton}
          >
            {eventDetail.bookingMessage}
          </button>
        </div>
        {queueErrorMessage && <p className="booking-queue-message error">{queueErrorMessage}</p>}
      </article>

      <section className="event-description-panel">
        <h2>공연 소개</h2>
        <p>{eventDetail.description}</p>
      </section>

      {isScheduleAlertOpen && (
        <div className="signup-alert-backdrop" role="alertdialog" aria-modal="true">
          <div className="signup-alert booking-alert">
            <strong>회차를 선택해 주세요.</strong>
            <p>예매를 진행하려면 먼저 공연 회차를 선택해야 합니다.</p>
            <button type="button" onClick={() => setIsScheduleAlertOpen(false)}>
              확인
            </button>
          </div>
        </div>
      )}

      {isLoginRequiredAlertOpen && (
        <div className="signup-alert-backdrop" role="alertdialog" aria-modal="true">
          <div className="signup-alert booking-alert booking-login-alert">
            <strong>로그인이 필요한 작업입니다.</strong>
            <p>로그인 하시겠습니까?</p>
            <div className="booking-alert-actions">
              <button type="button" onClick={() => setIsLoginRequiredAlertOpen(false)}>
                취소
              </button>
              <button
                type="button"
                onClick={() => {
                  setIsLoginRequiredAlertOpen(false);
                  onNavigate('login');
                }}
              >
                확인
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

function BookingWindowPage() {
  const sideLayoutDragRef = useRef({
    isDragging: false,
    startX: 0,
    startY: 0,
    lastX: 0,
    lastY: 0,
    suppressClick: false,
  });
  const [eventGroupCode] = useState(() => getBookingEventGroupCodeFromLocation());
  const [selectedScheduleId, setSelectedScheduleId] = useState(() => getBookingEventIdFromLocation());
  const [activeToken] = useState(() => getBookingActiveTokenFromLocation());
  const [eventDetail, setEventDetail] = useState<EventDetail | null>(null);
  const [areas, setAreas] = useState<AreaResponse[]>([]);
  const [layoutSvgText, setLayoutSvgText] = useState('');
  const [selectedAreaId, setSelectedAreaId] = useState<number | null>(null);
  const [seats, setSeats] = useState<SeatResponse[]>([]);
  const [selectedSeatIds, setSelectedSeatIds] = useState<number[]>([]);
  const [checkoutStep, setCheckoutStep] = useState<'SEAT' | 'CHECKOUT'>('SEAT');
  const [checkoutPrepare, setCheckoutPrepare] = useState<CheckoutPrepareResponse | null>(null);
  const [userCoupons, setUserCoupons] = useState<UserCouponResponse[]>([]);
  const [selectedUserCouponId, setSelectedUserCouponId] = useState<number | null>(null);
  const [couponDiscountAmount, setCouponDiscountAmount] = useState(0);
  const [couponMessage, setCouponMessage] = useState('');
  const [isCheckoutPreparing, setIsCheckoutPreparing] = useState(false);
  const [checkoutErrorMessage, setCheckoutErrorMessage] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isSeatLoading, setIsSeatLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [seatErrorMessage, setSeatErrorMessage] = useState('');
  const [sideLayoutScale, setSideLayoutScale] = useState(1);
  const [sideLayoutPan, setSideLayoutPan] = useState({ x: 0, y: 0 });

  const selectedArea = areas.find((area) => area.areaId === selectedAreaId) || null;
  const selectedSeats = seats.filter((seat) => selectedSeatIds.includes(seat.seatId));
  const selectedSeatAmount = selectedSeats.reduce((sum, seat) => sum + seat.price, 0);
  const finalPaymentAmount = Math.max(0, selectedSeatAmount - couponDiscountAmount);
  const layoutMarkup = buildBookingLayoutSvg(layoutSvgText, areas, selectedAreaId);
  const areaPrices = getDistinctAreaPrices(areas);
  const sideLayoutStyle = {
    '--layout-scale': sideLayoutScale,
    '--layout-x': `${sideLayoutPan.x}px`,
    '--layout-y': `${sideLayoutPan.y}px`,
  } as CSSProperties;

  useEffect(() => {
    if (selectedScheduleId && activeToken) {
      saveActiveToken(selectedScheduleId, activeToken);
    }
  }, [activeToken, selectedScheduleId]);

  useEffect(() => {
    if (!selectedScheduleId || !activeToken) {
      return undefined;
    }

    function leaveQueue() {
      releaseActiveToken(selectedScheduleId, activeToken);
    }

    window.addEventListener('pagehide', leaveQueue);
    window.addEventListener('beforeunload', leaveQueue);

    return () => {
      window.removeEventListener('pagehide', leaveQueue);
      window.removeEventListener('beforeunload', leaveQueue);
    };
  }, [activeToken, selectedScheduleId]);

  useEffect(() => {
    async function loadEventSchedules() {
      if (!eventGroupCode) {
        return;
      }

      try {
        const detail = await request<EventDetail>(
          `/client-api/api/v1/event/select/group/${encodeURIComponent(eventGroupCode)}`,
          { method: 'GET' },
        );

        setEventDetail(detail);

        if (!selectedScheduleId) {
          const firstOnSaleSchedule = detail.schedules.find((schedule) => schedule.status === 'ON_SALE');
          setSelectedScheduleId(firstOnSaleSchedule?.eventId || detail.schedules[0]?.eventId || 0);
        }
      } catch {
        setEventDetail(null);
      }
    }

    loadEventSchedules();
  }, [eventGroupCode, selectedScheduleId]);

  useEffect(() => {
    async function loadBookingData() {
      if (!selectedScheduleId) {
        setErrorMessage('예매할 공연 회차 정보가 없습니다.');
        setIsLoading(false);
        return;
      }

      setIsLoading(true);
      setSelectedAreaId(null);
      setSeats([]);
      setSelectedSeatIds([]);
      resetCheckoutState();
      setSeatErrorMessage('');

      try {
        const [areaResponse, layoutResponse] = await Promise.all([
          request<PageResponse<AreaResponse>>(
            `/client-api/api/v1/area/select?eventId=${selectedScheduleId}`,
            {
              method: 'GET',
              headers: { 'X-Active-Token': activeToken },
            },
          ),
          request<EventLayoutResponse | undefined>(
            `/client-api/api/v1/area/layout/event/${selectedScheduleId}`,
            {
              method: 'GET',
              headers: { 'X-Active-Token': activeToken },
            },
          ),
        ]);

        setAreas(areaResponse.content || []);
        setLayoutSvgText(layoutResponse?.svgText || '');
        setErrorMessage('');
      } catch (error) {
        if (error instanceof SessionExpiredError) {
          alert(error.message);
          window.close();
          return;
        }

        setErrorMessage(error instanceof Error ? error.message : '좌석 정보를 불러오지 못했습니다.');
      } finally {
        setIsLoading(false);
      }
    }

    loadBookingData();
  }, [selectedScheduleId]);

  function changeSchedule(schedule: EventSchedule) {
    if (schedule.eventId === selectedScheduleId || schedule.status !== 'ON_SALE') {
      return;
    }

    const bookingUrl = new URL(window.location.href);
    bookingUrl.searchParams.set('eventId', String(schedule.eventId));
    window.history.replaceState({ page: 'bookingWindow', eventId: schedule.eventId }, '', `${bookingUrl.pathname}${bookingUrl.search}${bookingUrl.hash}`);
    setSelectedScheduleId(schedule.eventId);
  }

  function showFullLayout() {
    setSelectedAreaId(null);
    setSeats([]);
    setSelectedSeatIds([]);
    resetCheckoutState();
    setSeatErrorMessage('');
  }

  function selectAreaByElement(target: EventTarget | null) {
    const areaElement = target instanceof Element
      ? target.closest<HTMLElement>('[data-area-id]')
      : null;
    const areaId = Number(areaElement?.dataset.areaId);
    const area = areas.find((currentArea) => currentArea.areaId === areaId);

    if (area) {
      selectArea(area);
    }
  }

  function clickLayout(event: ReactMouseEvent<HTMLDivElement>) {
    if (sideLayoutDragRef.current.suppressClick) {
      sideLayoutDragRef.current.suppressClick = false;
      return;
    }

    selectAreaByElement(event.target);
  }

  function pressLayoutKey(event: ReactKeyboardEvent<HTMLDivElement>) {
    if (event.key !== 'Enter' && event.key !== ' ') {
      return;
    }

    event.preventDefault();
    selectAreaByElement(event.target);
  }

  function zoomSideLayout(nextScale: number) {
    setSideLayoutScale(Math.min(2.4, Math.max(0.7, nextScale)));
  }

  function startSideLayoutDrag(event: ReactMouseEvent<HTMLDivElement>) {
    sideLayoutDragRef.current = {
      isDragging: true,
      startX: event.clientX,
      startY: event.clientY,
      lastX: event.clientX,
      lastY: event.clientY,
      suppressClick: false,
    };
  }

  function dragSideLayout(event: ReactMouseEvent<HTMLDivElement>) {
    if (!sideLayoutDragRef.current.isDragging) {
      return;
    }

    const nextX = event.clientX - sideLayoutDragRef.current.lastX;
    const nextY = event.clientY - sideLayoutDragRef.current.lastY;

    const totalX = event.clientX - sideLayoutDragRef.current.startX;
    const totalY = event.clientY - sideLayoutDragRef.current.startY;

    if (Math.abs(totalX) > 8 || Math.abs(totalY) > 8) {
      sideLayoutDragRef.current.suppressClick = true;
    }

    sideLayoutDragRef.current.lastX = event.clientX;
    sideLayoutDragRef.current.lastY = event.clientY;
    setSideLayoutPan((currentPan) => ({
      x: currentPan.x + nextX,
      y: currentPan.y + nextY,
    }));
  }

  function stopSideLayoutDrag() {
    sideLayoutDragRef.current.isDragging = false;
  }

  function cancelSideLayoutDrag() {
    sideLayoutDragRef.current.isDragging = false;
    sideLayoutDragRef.current.suppressClick = false;
  }

  async function selectArea(area: AreaResponse) {
    setSelectedAreaId(area.areaId);
    setSelectedSeatIds([]);
    resetCheckoutState();
    setIsSeatLoading(true);
    setSeatErrorMessage('');

    try {
      const seatResponse = await request<PageResponse<SeatResponse>>(
        `/client-api/api/v1/seat/select?eventId=${selectedScheduleId}&areaId=${area.areaId}`,
        {
          method: 'GET',
          headers: { 'X-Active-Token': activeToken },
        },
      );

      setSeats(seatResponse.content || []);
    } catch (error) {
      if (error instanceof SessionExpiredError) {
        alert(error.message);
        window.close();
        return;
      }

      setSeats([]);
      setSeatErrorMessage(error instanceof Error ? error.message : '좌석 정보를 불러오지 못했습니다.');
    } finally {
      setIsSeatLoading(false);
    }
  }

  function toggleSeat(seat: SeatResponse) {
    if (!isSeatAvailable(seat)) {
      return;
    }

    resetCheckoutState();
    setSelectedSeatIds((currentSeatIds) =>
      currentSeatIds.includes(seat.seatId)
        ? currentSeatIds.filter((seatId) => seatId !== seat.seatId)
        : [...currentSeatIds, seat.seatId],
    );
  }

  function resetCheckoutState() {
    setCheckoutStep('SEAT');
    setCheckoutPrepare(null);
    setUserCoupons([]);
    setSelectedUserCouponId(null);
    setCouponDiscountAmount(0);
    setCouponMessage('');
    setCheckoutErrorMessage('');
  }

  function selectedSeatInfoList() {
    return selectedSeats.map((seat) => ({
      id: seat.seatId,
      zone: seat.zone,
      row: seat.seatRow,
      col: seat.seatCol,
    }));
  }

  async function prepareCheckout() {
    if (!selectedScheduleId || selectedSeatIds.length === 0) {
      return;
    }

    setIsCheckoutPreparing(true);
    setCheckoutErrorMessage('');

    try {
      const seatInfoList = selectedSeatInfoList();
      const occupyResult = await request<SeatOccupyResponse>('/client-api/api/v1/seat/occupy', {
        method: 'POST',
        headers: { 'X-Active-Token': activeToken },
        body: JSON.stringify({
          eventId: selectedScheduleId,
          seats: seatInfoList,
          maxTicketsPerPerson: eventDetail?.maxTicketsPerPerson,
          eventGroupCode,
          ticketLimitScope: eventDetail?.ticketLimitScope,
        }),
      });

      const prepareResult = await request<CheckoutPrepareResponse>('/client-api/api/v1/checkout/prepare', {
        method: 'POST',
        headers: { 'X-Active-Token': activeToken },
        body: JSON.stringify({
          eventId: selectedScheduleId,
          orderId: occupyResult.orderId,
          seats: occupyResult.seats,
        }),
      });

      let coupons: UserCouponResponse[] = [];
      try {
        coupons = await request<UserCouponResponse[]>('/client-api/api/v1/coupon/me', { method: 'GET' });
      } catch {
        coupons = [];
      }

      setCheckoutPrepare(prepareResult);
      setUserCoupons(coupons.filter((coupon) => coupon.status === 'ISSUED'));
      setSelectedUserCouponId(null);
      setCouponDiscountAmount(0);
      setCouponMessage('');
      setCheckoutStep('CHECKOUT');
    } catch (error) {
      if (error instanceof SessionExpiredError) {
        alert(error.message);
        window.close();
        return;
      }

      setCheckoutErrorMessage(error instanceof Error ? error.message : '예매 준비에 실패했습니다.');
    } finally {
      setIsCheckoutPreparing(false);
    }
  }

  async function selectCoupon(userCouponId: number | null) {
    setSelectedUserCouponId(userCouponId);
    setCouponDiscountAmount(0);
    setCouponMessage('');

    if (!userCouponId || selectedSeatAmount <= 0) {
      return;
    }

    try {
      const availability = await request<CouponAvailabilityResponse>('/client-api/api/v1/coupon/available', {
        method: 'POST',
        body: JSON.stringify({
          userCouponId,
          orderAmount: selectedSeatAmount,
        }),
      });

      if (!availability.available) {
        setSelectedUserCouponId(null);
        setCouponMessage(availability.reason || '사용할 수 없는 쿠폰입니다.');
        return;
      }

      setCouponDiscountAmount(availability.discountAmount || 0);
      setCouponMessage(`${(availability.discountAmount || 0).toLocaleString()}원 할인이 적용됩니다.`);
    } catch (error) {
      setSelectedUserCouponId(null);
      setCouponMessage(error instanceof Error ? error.message : '쿠폰 확인에 실패했습니다.');
    }
  }

  function goNextCheckoutStep() {
    alert('결제 정보 입력 단계는 아직 연결되지 않았습니다.');
  }

  return (
    <section className="booking-window-page">
      <header className="booking-window-header">
        <strong>Tickey 티켓 예매</strong>
        <span>{eventDetail?.title || '좌석 선택'}</span>
      </header>

      <main className={`booking-window-body${checkoutStep === 'CHECKOUT' ? ' checkout-mode' : ''}`}>
        <section className="booking-layout-panel">
          {checkoutStep === 'SEAT' && eventDetail && eventDetail.schedules.length > 1 && (
            <div className="booking-schedule-strip">
              <strong>다른 회차 선택</strong>
              <div>
                {eventDetail.schedules.map((schedule) => (
                  <button
                    className={selectedScheduleId === schedule.eventId ? 'selected' : ''}
                    disabled={schedule.status !== 'ON_SALE'}
                    key={schedule.eventId}
                    type="button"
                    onClick={() => changeSchedule(schedule)}
                  >
                    <span>{getScheduleDateLabel(schedule.eventDateTime)}</span>
                    <em>{getScheduleTimeLabel(schedule.eventDateTime)}</em>
                  </button>
                ))}
              </div>
            </div>
          )}

          {checkoutStep === 'SEAT' && (
            <div className="booking-panel-title">
              <h1>{selectedArea ? '좌석 선택' : '구역 선택'}</h1>
              <p>
                {selectedArea
                  ? '오른쪽 작은 배치도에서 다른 구역을 다시 선택할 수 있습니다.'
                  : '원하는 구역을 선택하면 해당 구역의 좌석 배치도가 표시됩니다.'}
              </p>
            </div>
          )}

          {isLoading && <div className="booking-state-box">구역 정보를 불러오는 중입니다.</div>}
          {!isLoading && errorMessage && <div className="booking-state-box error">{errorMessage}</div>}

          {!isLoading && !errorMessage && (
            <>
              {!selectedArea && (
                layoutMarkup ? (
                  <div
                    className="booking-layout-map large"
                    onClick={clickLayout}
                    onKeyDown={pressLayoutKey}
                    dangerouslySetInnerHTML={{ __html: layoutMarkup }}
                  />
                ) : (
                  <BookingAreaButtonGrid
                    areas={areas}
                    selectedAreaId={selectedAreaId}
                    onSelectArea={selectArea}
                  />
                )
              )}

              {selectedArea && checkoutStep === 'SEAT' && (
                <div className="booking-seat-selection-grid">
                  <BookingSeatMap
                    isSeatLoading={isSeatLoading}
                    seatErrorMessage={seatErrorMessage}
                    seats={seats}
                    selectedArea={selectedArea}
                    selectedSeatIds={selectedSeatIds}
                    onToggleSeat={toggleSeat}
                  />
                </div>
              )}

              {checkoutStep === 'CHECKOUT' && (
                <BookingCheckoutPanel
                  coupons={userCoupons}
                  couponMessage={couponMessage}
                  onSelectCoupon={selectCoupon}
                  selectedCouponId={selectedUserCouponId}
                  selectedSeats={selectedSeats}
                />
              )}
            </>
          )}
        </section>

        <aside className="booking-side-panel">
          {checkoutStep === 'SEAT' ? (
            <>
              <section className="booking-side-section booking-side-layout-section">
                <div className="booking-side-title">
                  <strong>구역 배치도</strong>
                  <button type="button" onClick={showFullLayout}>
                    좌석도 전체보기
                  </button>
                </div>

                {layoutMarkup ? (
                  <div className="booking-layout-map side zoomable">
                    <div className="booking-layout-zoom-controls" onMouseDown={(event) => event.stopPropagation()}>
                      <button type="button" onClick={() => zoomSideLayout(sideLayoutScale + 0.2)}>
                        +
                      </button>
                      <button type="button" onClick={() => zoomSideLayout(sideLayoutScale - 0.2)}>
                        −
                      </button>
                    </div>
                    <div
                      className="booking-layout-pan-layer"
                      style={sideLayoutStyle}
                      onClick={clickLayout}
                      onKeyDown={pressLayoutKey}
                      onMouseDown={startSideLayoutDrag}
                      onMouseLeave={cancelSideLayoutDrag}
                      onMouseMove={dragSideLayout}
                      onMouseUp={stopSideLayoutDrag}
                      dangerouslySetInnerHTML={{ __html: layoutMarkup }}
                    />
                  </div>
                ) : (
                  <BookingAreaButtonGrid
                    areas={areas}
                    selectedAreaId={selectedAreaId}
                    onSelectArea={selectArea}
                  />
                )}
              </section>

              <section className="booking-side-section booking-side-price-section">
                <div className="booking-side-title">
                  <strong>좌석 등급/가격</strong>
                </div>

                <div className="booking-price-list">
                  {areaPrices.length > 0 ? (
                    areaPrices.map((area) => (
                      <div className="booking-price-item" key={area.grade}>
                        <span>{getSeatGradeLabel(area.grade)}</span>
                        <strong>{area.price.toLocaleString()}원</strong>
                      </div>
                    ))
                  ) : (
                    <p>가격 정보가 없습니다.</p>
                  )}
                </div>

                <div className="booking-legend">
                  <span><i className="available" />선택 가능</span>
                  <span><i className="selected" />선택 좌석</span>
                  <span><i className="disabled" />선택 불가</span>
                </div>

                {checkoutErrorMessage && <p className="booking-checkout-error">{checkoutErrorMessage}</p>}

                <button
                  className="booking-next-button"
                  type="button"
                  disabled={selectedSeatIds.length === 0 || isCheckoutPreparing}
                  onClick={prepareCheckout}
                >
                  {isCheckoutPreparing ? '예매 준비 중' : '다음 단계'}
                </button>
              </section>
            </>
          ) : (
            <section className="booking-side-section booking-checkout-side-section">
              <BookingCheckoutSummary
                checkoutPrepare={checkoutPrepare}
                couponDiscountAmount={couponDiscountAmount}
                finalPaymentAmount={finalPaymentAmount}
                selectedSeatAmount={selectedSeatAmount}
              />
              <div className="booking-checkout-actions">
                <button className="booking-prev-button" type="button" onClick={resetCheckoutState}>
                  이전
                </button>
                <button className="booking-next-button" type="button" onClick={goNextCheckoutStep}>
                  다음
                </button>
              </div>
            </section>
          )}
        </aside>
      </main>
    </section>
  );
}

function BookingCheckoutPanel({
  coupons,
  couponMessage,
  onSelectCoupon,
  selectedCouponId,
  selectedSeats,
}: {
  coupons: UserCouponResponse[];
  couponMessage: string;
  onSelectCoupon: (userCouponId: number | null) => void;
  selectedCouponId: number | null;
  selectedSeats: SeatResponse[];
}) {
  return (
    <div className="booking-checkout-panel">
      <BookingCheckoutStepper currentStep="PRICE" />

      <div className="booking-checkout-header">
        <h1>가격 / 쿠폰 선택</h1>
      </div>

      <div className="booking-checkout-grid">
        <section className="booking-checkout-card">
          <h2>선택 좌석</h2>
          <div className="booking-selected-seat-list">
            {selectedSeats.map((seat) => (
              <div className="booking-selected-seat-item" key={seat.seatId}>
                <span>{seat.seatName || `${seat.zone} ${seat.seatRow}열 ${seat.seatCol}번`}</span>
                <em>{getSeatGradeLabel(seat.grade)}</em>
                <strong>{seat.price.toLocaleString()}원</strong>
              </div>
            ))}
          </div>
        </section>

        <section className="booking-checkout-card">
          <h2>쿠폰 선택</h2>
          <label className="booking-coupon-option">
            <input
              checked={selectedCouponId === null}
              name="booking-coupon"
              type="radio"
              onChange={() => onSelectCoupon(null)}
            />
            <span>
              <strong>쿠폰 사용 안함</strong>
              <em>할인 없이 결제합니다.</em>
            </span>
          </label>

          {coupons.length > 0 ? (
            coupons.map((userCoupon) => (
              <label className="booking-coupon-option" key={userCoupon.userCouponId}>
                <input
                  checked={selectedCouponId === userCoupon.userCouponId}
                  name="booking-coupon"
                  type="radio"
                  onChange={() => onSelectCoupon(userCoupon.userCouponId)}
                />
                <span>
                  <strong>{userCoupon.coupon.name}</strong>
                  <em>{formatCouponBenefit(userCoupon.coupon)}</em>
                </span>
              </label>
            ))
          ) : (
            <p className="booking-coupon-empty">사용 가능한 쿠폰이 없습니다.</p>
          )}

          {couponMessage && <p className="booking-coupon-message">{couponMessage}</p>}
        </section>
      </div>
    </div>
  );
}

function BookingCheckoutStepper({ currentStep }: { currentStep: 'SEAT' | 'PRICE' | 'PAYMENT' | 'COMPLETE' }) {
  const steps = [
    { key: 'SEAT', label: '좌석선택' },
    { key: 'PRICE', label: '가격/쿠폰선택' },
    { key: 'PAYMENT', label: '결제하기' },
    { key: 'COMPLETE', label: '예매완료' },
  ];
  const currentStepIndex = steps.findIndex((step) => step.key === currentStep);

  return (
    <nav className="booking-checkout-stepper" aria-label="예매 진행 단계">
      {steps.map((step, index) => {
        const isActive = step.key === currentStep;
        const isCompleted = index < currentStepIndex;

        return (
          <div
            className={`booking-checkout-step${isActive ? ' active' : ''}${isCompleted ? ' completed' : ''}`}
            key={step.key}
          >
            <span>{index + 1}</span>
            <strong>{step.label}</strong>
          </div>
        );
      })}
    </nav>
  );
}

function BookingCheckoutSummary({
  checkoutPrepare,
  couponDiscountAmount,
  finalPaymentAmount,
  selectedSeatAmount,
}: {
  checkoutPrepare: CheckoutPrepareResponse | null;
  couponDiscountAmount: number;
  finalPaymentAmount: number;
  selectedSeatAmount: number;
}) {
  return (
    <aside className="booking-payment-summary">
      <div>
        <span>주문번호</span>
        <strong>{checkoutPrepare?.orderId || '-'}</strong>
      </div>
      <div>
        <span>티켓 금액</span>
        <strong>{selectedSeatAmount.toLocaleString()}원</strong>
      </div>
      <div>
        <span>쿠폰 할인</span>
        <strong>-{couponDiscountAmount.toLocaleString()}원</strong>
      </div>
      <div className="total">
        <span>결제 예정 금액</span>
        <strong>{finalPaymentAmount.toLocaleString()}원</strong>
      </div>
    </aside>
  );
}

function BookingAreaButtonGrid({
  areas,
  selectedAreaId,
  onSelectArea,
}: {
  areas: AreaResponse[];
  selectedAreaId: number | null;
  onSelectArea: (area: AreaResponse) => void;
}) {
  return (
    <div className="booking-area-button-grid">
      {areas.map((area) => (
        <button
          className={selectedAreaId === area.areaId ? 'selected' : ''}
          key={area.areaId}
          type="button"
          onClick={() => onSelectArea(area)}
        >
          <strong>{area.areaName}</strong>
          <span>{getSeatGradeLabel(area.grade)}</span>
        </button>
      ))}
    </div>
  );
}

function BookingSeatMap({
  isSeatLoading,
  seatErrorMessage,
  seats,
  selectedArea,
  selectedSeatIds,
  onToggleSeat,
}: {
  isSeatLoading: boolean;
  seatErrorMessage: string;
  seats: SeatResponse[];
  selectedArea: AreaResponse;
  selectedSeatIds: number[];
  onToggleSeat: (seat: SeatResponse) => void;
}) {
  return (
    <div className="booking-seat-panel expanded">
      <div className="booking-seat-panel-title">
        <strong>{selectedArea.areaName} 구역 좌석 배치도</strong>
        <span>좌석을 선택해 주세요.</span>
      </div>

      {isSeatLoading && <div className="booking-state-box compact">좌석을 불러오는 중입니다.</div>}
      {!isSeatLoading && seatErrorMessage && (
        <div className="booking-state-box compact error">{seatErrorMessage}</div>
      )}
      {!isSeatLoading && !seatErrorMessage && seats.length === 0 && (
        <div className="booking-state-box compact">표시할 좌석 정보가 없습니다.</div>
      )}
      {!isSeatLoading && !seatErrorMessage && seats.length > 0 && (
        <BookingSeatSvg
          seats={seats}
          selectedSeatIds={selectedSeatIds}
          onToggleSeat={onToggleSeat}
        />
      )}
    </div>
  );
}

function BookingSeatSvg({
  seats,
  selectedSeatIds,
  onToggleSeat,
}: {
  seats: SeatResponse[];
  selectedSeatIds: number[];
  onToggleSeat: (seat: SeatResponse) => void;
}) {
  const seatYOffset = 10;
  const baseRenderedSeats = seats.map((seat) => {
    const seatWidth = seat.seatWidth ?? 14;
    const seatHeight = seat.seatHeight ?? 14;
    const seatX = seat.positionX ?? ((seat.seatCol || 1) - 1) * 18 + 80;
    const seatY = (seat.positionY ?? ((seat.seatRow || 1) - 1) * 18 + 80) + seatYOffset;

    return {
      seat,
      seatWidth,
      seatHeight,
      seatX,
      seatY,
    };
  });
  const baseMinSeatX = Math.min(...baseRenderedSeats.map(({ seatX }) => seatX));
  const baseMaxSeatX = Math.max(...baseRenderedSeats.map(({ seatX, seatWidth }) => seatX + seatWidth));
  const viewBoxX = 0;
  const viewBoxWidth = Math.max(760, baseMaxSeatX - baseMinSeatX + 120);
  const viewBoxCenterX = viewBoxX + viewBoxWidth / 2;
  const seatXOffset = viewBoxCenterX - (baseMinSeatX + baseMaxSeatX) / 2;
  const renderedSeats = baseRenderedSeats.map((renderedSeat) => ({
    ...renderedSeat,
    seatX: renderedSeat.seatX + seatXOffset,
  }));
  const minSeatX = Math.min(...renderedSeats.map(({ seatX }) => seatX));
  const maxSeatX = Math.max(...renderedSeats.map(({ seatX, seatWidth }) => seatX + seatWidth));
  const minSeatY = Math.min(...renderedSeats.map(({ seatY }) => seatY));
  const maxSeatY = Math.max(...renderedSeats.map(({ seatY, seatHeight }) => seatY + seatHeight));
  const stageWidth = Math.min(300, Math.max(180, maxSeatX - minSeatX));
  const stageHeight = 34;
  const stageGap = 88;
  const stageX = viewBoxCenterX - stageWidth / 2;
  const stageY = Math.max(8, minSeatY - stageHeight - stageGap);
  const viewBoxY = Math.min(0, stageY - 24);
  const viewBoxHeight = Math.max(520, maxSeatY - viewBoxY + 60);

  return (
    <svg
      className="booking-seat-map"
      viewBox={`${viewBoxX} ${viewBoxY} ${viewBoxWidth} ${viewBoxHeight}`}
      role="img"
      aria-label="좌석 배치도"
    >
      <rect className="booking-stage" x={stageX} y={stageY} width={stageWidth} height={stageHeight} rx="17" />
      <text className="booking-stage-text" x={stageX + stageWidth / 2} y={stageY + 22} textAnchor="middle">
        STAGE
      </text>
      {renderedSeats.map(({ seat, seatWidth, seatHeight, seatX, seatY }) => {
        const isSelected = selectedSeatIds.includes(seat.seatId);
        const seatStatusClass = isSeatAvailable(seat) ? 'available' : 'disabled';

        return (
          <rect
            className={`booking-seat ${seatStatusClass}${isSelected ? ' selected' : ''}`}
            key={getSeatKey(seat)}
            x={seatX}
            y={seatY}
            width={seatWidth}
            height={seatHeight}
            rx="3"
            transform={`rotate(${seat.rotation || 0} ${seatX + seatWidth / 2} ${seatY + seatHeight / 2})`}
            onClick={() => onToggleSeat(seat)}
          >
            <title>{seat.seatName}</title>
          </rect>
        );
      })}
    </svg>
  );
}

function buildBookingLayoutSvg(svgText: string, areas: AreaResponse[], selectedAreaId: number | null) {
  if (!svgText) {
    return '';
  }

  const parser = new DOMParser();
  const documentElement = parser.parseFromString(svgText, 'image/svg+xml');
  const svgElement = documentElement.querySelector('svg');

  if (!svgElement) {
    return '';
  }

  const areaByKey = new Map<string, AreaResponse>();

  areas.forEach((area) => {
    areaByKey.set(normalizeLayoutKey(area.layoutKey), area);
    areaByKey.set(normalizeLayoutKey(area.areaName), area);
  });

  svgElement.classList.add('booking-area-svg');
  svgElement.setAttribute('preserveAspectRatio', 'xMidYMid meet');
  svgElement.querySelectorAll('path, rect, polygon').forEach((element) => {
    const areaKey = normalizeLayoutKey(getAreaKeyFromElement(element));
    const area = areaByKey.get(areaKey);

    if (!area) {
      return;
    }

    element.classList.add('booking-area-shape');
    element.setAttribute('data-area-id', String(area.areaId));
    element.setAttribute('tabindex', '0');
    element.setAttribute('role', 'button');
    element.setAttribute('aria-label', `${area.areaName} 구역 선택`);

    if (area.areaId === selectedAreaId) {
      element.classList.add('is-selected');
    }

    const titleElement = documentElement.createElementNS('http://www.w3.org/2000/svg', 'title');
    titleElement.textContent = `${area.areaName} · ${getSeatGradeLabel(area.grade)} · ${area.price.toLocaleString()}원`;
    element.appendChild(titleElement);
  });

  return svgElement.outerHTML;
}

function ArrowIcon({ direction }: { direction: 'left' | 'right' }) {
  const points = direction === 'left' ? '38 10 14 32 38 54' : '26 10 50 32 26 54';

  return (
    <svg className="carousel-arrow-icon" viewBox="0 0 64 64" aria-hidden="true">
      <polyline points={points} />
    </svg>
  );
}

function LoginPage({
  onLoginSuccess,
  onNavigate,
}: {
  onLoginSuccess: (name: string) => void;
  onNavigate: (page: Page) => void;
}) {
  const [loginForm, setLoginForm] = useState<LoginForm>(initialLoginForm);
  const [isLoginIdSaved, setIsLoginIdSaved] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isLoginFailedAlertOpen, setIsLoginFailedAlertOpen] = useState(false);
  const [loginFailedMessage, setLoginFailedMessage] = useState('');

  useEffect(() => {
    const savedLoginId = getCookie(savedLoginIdCookieName);

    if (savedLoginId) {
      setLoginForm((currentForm) => ({ ...currentForm, userId: savedLoginId }));
      setIsLoginIdSaved(true);
    }
  }, []);

  async function submitLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsSubmitting(true);

    try {
      const loginResponse = await request<LoginResponse>('/client-api/api/v1/auth/login', {
        method: 'POST',
        body: JSON.stringify(loginForm),
      });

      if (!loginResponse.success || !loginResponse.accessToken || !loginResponse.refreshToken) {
        setLoginFailedMessage(loginResponse.message || '정보가 올바르지 않습니다.');
        setIsLoginFailedAlertOpen(true);
        return;
      }

      sessionStorage.setItem('ticksy.accessToken', loginResponse.accessToken);
      sessionStorage.setItem('ticksy.refreshToken', loginResponse.refreshToken);
      sessionStorage.setItem('ticksy.userName', loginResponse.name || loginForm.userId);

      if (isLoginIdSaved) {
        setCookie(savedLoginIdCookieName, loginForm.userId, 60 * 60 * 24 * 365);
      } else {
        deleteCookie(savedLoginIdCookieName);
      }

      onLoginSuccess(loginResponse.name || loginForm.userId);
      onNavigate('home');
    } catch (error) {
      setLoginFailedMessage(error instanceof Error ? error.message : '로그인에 실패했습니다.');
      setIsLoginFailedAlertOpen(true);
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <section className="login-page">
      <button className="login-logo" type="button" onClick={() => onNavigate('home')}>
        Tickey
      </button>

      <div className="login-box">
        <form className="login-form" onSubmit={submitLogin}>
          <input
            autoComplete="username"
            placeholder="아이디 입력"
            required
            value={loginForm.userId}
            onChange={(event) => setLoginForm({ ...loginForm, userId: event.target.value })}
          />
          <input
            autoComplete="current-password"
            placeholder="비밀번호 입력"
            required
            type="password"
            value={loginForm.password}
            onChange={(event) => setLoginForm({ ...loginForm, password: event.target.value })}
          />

          <label className="login-remember">
            <input
              checked={isLoginIdSaved}
              type="checkbox"
              onChange={(event) => setIsLoginIdSaved(event.target.checked)}
            />
            <span>ID 저장</span>
          </label>

          <button className="login-submit-button" disabled={isSubmitting} type="submit">
          {isSubmitting ? '로그인 시도 중...' : '로그인'}
          </button>

        </form>

        <div className="login-links">
          <button type="button" onClick={() => onNavigate('findId')}>
            아이디 찾기
          </button>
          <span aria-hidden="true" />
          <button type="button" onClick={() => onNavigate('findPassword')}>
            비밀번호 찾기
          </button>
          <span aria-hidden="true" />
          <button type="button" onClick={() => onNavigate('signup')}>
            회원가입
          </button>
        </div>
      </div>

      <footer className="login-footer">
        <nav aria-label="로그인 페이지 정책">
          <button type="button">이용약관</button>
          <span aria-hidden="true" />
          <button type="button">위치기반서비스이용약관</button>
          <span aria-hidden="true" />
          <button type="button">개인정보처리방침</button>
        </nav>
        <p>문의전화 : 1588-4926 (평일 09:00-18:00, 유료)</p>
        <p>© Tickey Corp.</p>
      </footer>

      {isLoginFailedAlertOpen && (
        <div className="signup-alert-backdrop" role="alertdialog" aria-modal="true">
          <div className="signup-alert login-fail-alert">
            <strong>로그인 실패</strong>
            <p>{loginFailedMessage || '정보가 올바르지 않습니다.'}</p>
            <button type="button" onClick={() => setIsLoginFailedAlertOpen(false)}>
              확인
            </button>
          </div>
        </div>
      )}
    </section>
  );
}

function FindIdPage({ onNavigate }: { onNavigate: (page: Page) => void }) {
  const [findIdMethod, setFindIdMethod] = useState<FindIdMethod>('phone');
  const [findIdForm, setFindIdForm] = useState(initialFindIdForm);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [foundUserId, setFoundUserId] = useState('');
  const [isFindIdFailedAlertOpen, setIsFindIdFailedAlertOpen] = useState(false);

  async function submitFindId(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsSubmitting(true);

    try {
      const response = await request<FindUserIdResponse>(
        `/client-api/api/v1/user/find/id/${findIdMethod}`,
        {
          method: 'POST',
          body: JSON.stringify({
            name: findIdForm.name,
            phoneNumber: findIdMethod === 'phone' ? findIdForm.phoneNumber : undefined,
            email: findIdMethod === 'email' ? findIdForm.email : undefined,
          }),
        },
      );

      setFoundUserId(response.maskedUserId);
    } catch {
      setIsFindIdFailedAlertOpen(true);
    } finally {
      setIsSubmitting(false);
    }
  }

  function changeFindIdMethod(method: FindIdMethod) {
    setFindIdMethod(method);
    setFindIdForm(initialFindIdForm);
    setFoundUserId('');
    setIsFindIdFailedAlertOpen(false);
  }

  return (
    <section className="find-id-page">
      <button className="login-logo" type="button" onClick={() => onNavigate('home')}>
        Tickey
      </button>

      <div className="find-id-box">
        <div className="find-id-heading">
          <h1>아이디 찾기</h1>
          <p>가입 시 등록한 정보로 Tickey 아이디를 확인하세요.</p>
        </div>

        <div className="find-id-tabs" role="tablist" aria-label="아이디 찾기 방법">
          <button
            className={findIdMethod === 'phone' ? 'active' : ''}
            type="button"
            onClick={() => changeFindIdMethod('phone')}
          >
            휴대폰 번호로 인증
          </button>
          <button
            className={findIdMethod === 'email' ? 'active' : ''}
            type="button"
            onClick={() => changeFindIdMethod('email')}
          >
            이메일로 인증
          </button>
        </div>

        <form className="find-id-form" onSubmit={submitFindId}>
          <label className="signup-field">
            <span>
              <em>*</em>이름
            </span>
            <input
              autoComplete="name"
              placeholder="이름 입력"
              required
              value={findIdForm.name}
              onChange={(event) => setFindIdForm({ ...findIdForm, name: event.target.value })}
            />
          </label>

          {findIdMethod === 'phone' ? (
            <label className="signup-field">
              <span>
                <em>*</em>휴대폰 번호
              </span>
              <input
                autoComplete="tel"
                placeholder="010-0000-0000"
                required
                value={findIdForm.phoneNumber}
                onChange={(event) =>
                  setFindIdForm({ ...findIdForm, phoneNumber: event.target.value })
                }
              />
            </label>
          ) : (
            <label className="signup-field">
              <span>
                <em>*</em>이메일
              </span>
              <input
                autoComplete="email"
                placeholder="tickey@example.com"
                required
                type="email"
                value={findIdForm.email}
                onChange={(event) => setFindIdForm({ ...findIdForm, email: event.target.value })}
              />
            </label>
          )}

          <button className="find-id-submit-button" disabled={isSubmitting} type="submit">
            {isSubmitting ? '조회 중...' : '조회하기'}
          </button>
        </form>

        <div className="find-id-links">
          <button type="button" onClick={() => onNavigate('login')}>
            로그인
          </button>
          <span aria-hidden="true" />
          <button type="button" onClick={() => onNavigate('signup')}>
            회원가입
          </button>
        </div>
      </div>

      {foundUserId && (
        <div className="signup-alert-backdrop" role="alertdialog" aria-modal="true">
          <div className="signup-alert">
            <strong>아이디를 찾았습니다.</strong>
            <p>회원님의 아이디는 {foundUserId} 입니다.</p>
            <button type="button" onClick={() => onNavigate('login')}>
              로그인 화면으로 이동
            </button>
          </div>
        </div>
      )}

      {isFindIdFailedAlertOpen && (
        <div className="signup-alert-backdrop" role="alertdialog" aria-modal="true">
          <div className="signup-alert find-id-fail-alert">
            <strong>정보가 일치하지 않습니다.</strong>
            <p>입력한 이름과 인증 정보가 가입 정보와 일치하지 않습니다.</p>
            <button type="button" onClick={() => setIsFindIdFailedAlertOpen(false)}>
              다시 입력하기
            </button>
          </div>
        </div>
      )}
    </section>
  );
}

function FindPasswordPage({ onNavigate }: { onNavigate: (page: Page) => void }) {
  const [findPasswordMethod, setFindPasswordMethod] = useState<FindIdMethod>('phone');
  const [findPasswordForm, setFindPasswordForm] = useState(initialFindPasswordForm);
  const [resetToken, setResetToken] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isResetting, setIsResetting] = useState(false);
  const [isPasswordVisible, setIsPasswordVisible] = useState(false);
  const [isFailAlertOpen, setIsFailAlertOpen] = useState(false);
  const [isSuccessAlertOpen, setIsSuccessAlertOpen] = useState(false);

  async function submitFindPassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsSubmitting(true);

    try {
      const response = await request<FindPasswordResponse>(
        `/client-api/api/v1/user/find/password/${findPasswordMethod}`,
        {
          method: 'POST',
          body: JSON.stringify({
            userId: findPasswordForm.userId,
            name: findPasswordForm.name,
            phoneNumber:
              findPasswordMethod === 'phone' ? findPasswordForm.phoneNumber : undefined,
            email: findPasswordMethod === 'email' ? findPasswordForm.email : undefined,
          }),
        },
      );

      setResetToken(response.resetToken);
    } catch {
      setIsFailAlertOpen(true);
    } finally {
      setIsSubmitting(false);
    }
  }

  async function submitResetPassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (findPasswordForm.password !== findPasswordForm.passwordConfirm) {
      setIsFailAlertOpen(true);
      return;
    }

    setIsResetting(true);

    try {
      await request('/client-api/api/v1/user/reset/password', {
        method: 'POST',
        body: JSON.stringify({
          resetToken,
          password: findPasswordForm.password,
        }),
      });

      setIsSuccessAlertOpen(true);
    } catch {
      setIsFailAlertOpen(true);
    } finally {
      setIsResetting(false);
    }
  }

  function changeFindPasswordMethod(method: FindIdMethod) {
    setFindPasswordMethod(method);
    setFindPasswordForm(initialFindPasswordForm);
    setResetToken('');
    setIsFailAlertOpen(false);
  }

  return (
    <section className="find-id-page">
      <button className="login-logo" type="button" onClick={() => onNavigate('home')}>
        Tickey
      </button>

      <div className="find-id-box">
        <div className="find-id-heading">
          <h1>비밀번호 찾기</h1>
          <p>
            {resetToken
              ? '새 비밀번호를 입력해 주세요.'
              : '가입 시 등록한 정보로 비밀번호를 재설정하세요.'}
          </p>
        </div>

        {!resetToken ? (
          <>
            <div className="find-id-tabs" role="tablist" aria-label="비밀번호 찾기 방법">
              <button
                className={findPasswordMethod === 'phone' ? 'active' : ''}
                type="button"
                onClick={() => changeFindPasswordMethod('phone')}
              >
                휴대폰 번호로 인증
              </button>
              <button
                className={findPasswordMethod === 'email' ? 'active' : ''}
                type="button"
                onClick={() => changeFindPasswordMethod('email')}
              >
                이메일로 인증
              </button>
            </div>

            <form className="find-id-form" onSubmit={submitFindPassword}>
              <label className="signup-field">
                <span>
                  <em>*</em>ID
                </span>
                <input
                  autoComplete="username"
                  placeholder="아이디 입력"
                  required
                  value={findPasswordForm.userId}
                  onChange={(event) =>
                    setFindPasswordForm({ ...findPasswordForm, userId: event.target.value })
                  }
                />
              </label>
              <label className="signup-field">
                <span>
                  <em>*</em>이름
                </span>
                <input
                  autoComplete="name"
                  placeholder="이름 입력"
                  required
                  value={findPasswordForm.name}
                  onChange={(event) =>
                    setFindPasswordForm({ ...findPasswordForm, name: event.target.value })
                  }
                />
              </label>

              {findPasswordMethod === 'phone' ? (
                <label className="signup-field">
                  <span>
                    <em>*</em>휴대폰 번호
                  </span>
                  <input
                    autoComplete="tel"
                    placeholder="010-0000-0000"
                    required
                    value={findPasswordForm.phoneNumber}
                    onChange={(event) =>
                      setFindPasswordForm({
                        ...findPasswordForm,
                        phoneNumber: event.target.value,
                      })
                    }
                  />
                </label>
              ) : (
                <label className="signup-field">
                  <span>
                    <em>*</em>이메일
                  </span>
                  <input
                    autoComplete="email"
                    placeholder="tickey@example.com"
                    required
                    type="email"
                    value={findPasswordForm.email}
                    onChange={(event) =>
                      setFindPasswordForm({ ...findPasswordForm, email: event.target.value })
                    }
                  />
                </label>
              )}

              <button className="find-id-submit-button" disabled={isSubmitting} type="submit">
                {isSubmitting ? '확인 중...' : '확인하기'}
              </button>
            </form>
          </>
        ) : (
          <form className="find-id-form" onSubmit={submitResetPassword}>
            <label className="signup-field">
              <span>
                <em>*</em>새 비밀번호
              </span>
              <div className="signup-password-input">
                <input
                  autoComplete="new-password"
                  minLength={8}
                  placeholder="새 비밀번호 입력"
                  required
                  type={isPasswordVisible ? 'text' : 'password'}
                  value={findPasswordForm.password}
                  onChange={(event) =>
                    setFindPasswordForm({ ...findPasswordForm, password: event.target.value })
                  }
                />
                <button
                  className={isPasswordVisible ? 'visible' : ''}
                  type="button"
                  onClick={() => setIsPasswordVisible((isVisible) => !isVisible)}
                  aria-label={isPasswordVisible ? '비밀번호 숨기기' : '비밀번호 보기'}
                >
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M2.8 12s3.4-5.2 9.2-5.2S21.2 12 21.2 12 17.8 17.2 12 17.2 2.8 12 2.8 12Z" />
                    <circle cx="12" cy="12" r="2.8" />
                    {!isPasswordVisible && <line x1="4.5" y1="19.5" x2="19.5" y2="4.5" />}
                  </svg>
                </button>
              </div>
            </label>
            <label className="signup-field">
              <span>
                <em>*</em>새 비밀번호 확인
              </span>
              <input
                autoComplete="new-password"
                minLength={8}
                placeholder="새 비밀번호 재입력"
                required
                type={isPasswordVisible ? 'text' : 'password'}
                value={findPasswordForm.passwordConfirm}
                onChange={(event) =>
                  setFindPasswordForm({
                    ...findPasswordForm,
                    passwordConfirm: event.target.value,
                  })
                }
              />
            </label>

            <button className="find-id-submit-button" disabled={isResetting} type="submit">
              {isResetting ? '변경 중...' : '비밀번호 변경'}
            </button>
          </form>
        )}

        <div className="find-id-links">
          <button type="button" onClick={() => onNavigate('login')}>
            로그인
          </button>
          <span aria-hidden="true" />
          <button type="button" onClick={() => onNavigate('findId')}>
            아이디 찾기
          </button>
        </div>
      </div>

      {isFailAlertOpen && (
        <div className="signup-alert-backdrop" role="alertdialog" aria-modal="true">
          <div className="signup-alert find-id-fail-alert">
            <strong>정보가 일치하지 않습니다.</strong>
            <p>입력한 정보가 가입 정보와 일치하지 않거나 요청이 만료되었습니다.</p>
            <button type="button" onClick={() => setIsFailAlertOpen(false)}>
              다시 입력하기
            </button>
          </div>
        </div>
      )}

      {isSuccessAlertOpen && (
        <div className="signup-alert-backdrop" role="alertdialog" aria-modal="true">
          <div className="signup-alert">
            <strong>비밀번호가 변경되었습니다.</strong>
            <p>새 비밀번호로 로그인해 주세요.</p>
            <button type="button" onClick={() => onNavigate('login')}>
              로그인 화면으로 이동
            </button>
          </div>
        </div>
      )}
    </section>
  );
}

function SignupPage({ onNavigate }: { onNavigate: (page: Page) => void }) {
  const [signupForm, setSignupForm] = useState<SignupForm>(initialSignupForm);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isCheckingUserId, setIsCheckingUserId] = useState(false);
  const [isUserIdChecked, setIsUserIdChecked] = useState(false);
  const [userIdCheckMessage, setUserIdCheckMessage] = useState('');
  const [isSignupPasswordVisible, setIsSignupPasswordVisible] = useState(false);
  const [isSignupSuccessAlertOpen, setIsSignupSuccessAlertOpen] = useState(false);
  const [message, setMessage] = useState('');
  const [isBirthDateCalendarOpen, setIsBirthDateCalendarOpen] = useState(false);
  const [birthDateCalendarMonth, setBirthDateCalendarMonth] = useState(() =>
    getCalendarMonthFromValue(initialSignupForm.birthDate),
  );
  const birthDateCalendarRef = useRef<HTMLDivElement>(null);
  const birthDateCalendarDays = getCalendarDays(birthDateCalendarMonth);
  const birthDateMonthLabel = `${birthDateCalendarMonth.getFullYear()}.${String(
    birthDateCalendarMonth.getMonth() + 1,
  ).padStart(2, '0')}`;

  useEffect(() => {
    function closeCalendarOnOutsideClick(event: MouseEvent) {
      if (
        birthDateCalendarRef.current &&
        !birthDateCalendarRef.current.contains(event.target as Node)
      ) {
        setIsBirthDateCalendarOpen(false);
      }
    }

    document.addEventListener('mousedown', closeCalendarOnOutsideClick);

    return () => document.removeEventListener('mousedown', closeCalendarOnOutsideClick);
  }, []);

  function updateSignupForm(nextForm: SignupForm) {
    if (nextForm.userId !== signupForm.userId) {
      setIsUserIdChecked(false);
      setUserIdCheckMessage('');
    }

    setSignupForm(nextForm);
  }

  async function checkUserIdDuplication() {
    if (!signupForm.userId.trim()) {
      setUserIdCheckMessage('아이디를 먼저 입력해 주세요.');
      return;
    }

    setIsCheckingUserId(true);
    setUserIdCheckMessage('');

    try {
      await request(`/client-api/api/v1/user/check/duplication/${encodeURIComponent(signupForm.userId)}`, {
        method: 'GET',
      });

      setIsUserIdChecked(true);
      setUserIdCheckMessage('사용 가능한 아이디입니다.');
    } catch (error) {
      setIsUserIdChecked(false);
      setUserIdCheckMessage(error instanceof Error ? error.message : '이미 사용 중인 아이디입니다.');
    } finally {
      setIsCheckingUserId(false);
    }
  }

  function openBirthDatePicker() {
    setBirthDateCalendarMonth(getCalendarMonthFromValue(signupForm.birthDate));
    setIsBirthDateCalendarOpen((isOpen) => !isOpen);
  }

  function moveBirthDateCalendarMonth(monthOffset: number) {
    setBirthDateCalendarMonth(
      (monthDate) => new Date(monthDate.getFullYear(), monthDate.getMonth() + monthOffset, 1),
    );
  }

  function selectBirthDate(date: Date) {
    setSignupForm({ ...signupForm, birthDate: formatDateInput(date) });
    setIsBirthDateCalendarOpen(false);
  }

  async function submitSignup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setMessage('');

    if (!isUserIdChecked) {
      setMessage('아이디 중복체크를 진행해 주세요.');
      return;
    }

    if (signupForm.password !== signupForm.passwordConfirm) {
      setMessage('비밀번호가 일치하지 않습니다.');
      return;
    }

    setIsSubmitting(true);

    try {
      const { passwordConfirm, ...requestBody } = signupForm;

      await request('/client-api/api/v1/user/signup', {
        method: 'POST',
        body: JSON.stringify({
          ...requestBody,
          birthDate: requestBody.birthDate || null,
        }),
      });

      setSignupForm(initialSignupForm);
      setIsUserIdChecked(false);
      setUserIdCheckMessage('');
      setIsSignupPasswordVisible(false);
      setIsSignupSuccessAlertOpen(true);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '회원가입에 실패했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <section className="signup-page">
      <button className="login-logo" type="button" onClick={() => onNavigate('home')}>
        Tickey
      </button>

      <div className="signup-box">
        <div className="signup-heading">
          <h1>회원가입</h1>
          <p>Tickey 예매 서비스를 위한 기본 정보를 입력해 주세요.</p>
        </div>

        <form className="signup-form" onSubmit={submitSignup}>
          <label className="signup-field">
            <span>
              <em>*</em>ID
            </span>
            <div className="signup-id-row">
              <input
                autoComplete="username"
                placeholder="아이디 입력"
                required
                value={signupForm.userId}
                onChange={(event) =>
                  updateSignupForm({ ...signupForm, userId: event.target.value })
                }
              />
              <button disabled={isCheckingUserId} type="button" onClick={checkUserIdDuplication}>
                {isCheckingUserId ? '확인 중' : '중복체크'}
              </button>
            </div>
          </label>
          {userIdCheckMessage && (
            <p className={isUserIdChecked ? 'signup-check-message success' : 'signup-check-message'}>
              {userIdCheckMessage}
            </p>
          )}

          <label className="signup-field">
            <span>
              <em>*</em>Password
            </span>
            <div className="signup-password-input">
              <input
                autoComplete="new-password"
                minLength={8}
                placeholder="비밀번호 입력"
                required
                type={isSignupPasswordVisible ? 'text' : 'password'}
                value={signupForm.password}
                onChange={(event) =>
                  setSignupForm({ ...signupForm, password: event.target.value })
                }
              />
              <button
                className={isSignupPasswordVisible ? 'visible' : ''}
                type="button"
                onClick={() => setIsSignupPasswordVisible((isVisible) => !isVisible)}
                aria-label={isSignupPasswordVisible ? '비밀번호 숨기기' : '비밀번호 보기'}
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M2.8 12s3.4-5.2 9.2-5.2S21.2 12 21.2 12 17.8 17.2 12 17.2 2.8 12 2.8 12Z" />
                  <circle cx="12" cy="12" r="2.8" />
                  {!isSignupPasswordVisible && <line x1="4.5" y1="19.5" x2="19.5" y2="4.5" />}
                </svg>
              </button>
            </div>
          </label>
          <label className="signup-field">
            <span>
              <em>*</em>Password 확인
            </span>
            <input
              autoComplete="new-password"
              minLength={8}
              placeholder="비밀번호 재입력"
              required
              type={isSignupPasswordVisible ? 'text' : 'password'}
              value={signupForm.passwordConfirm}
              onChange={(event) =>
                setSignupForm({ ...signupForm, passwordConfirm: event.target.value })
              }
            />
          </label>

          <label className="signup-field">
            <span>
              <em>*</em>이름
            </span>
            <input
              autoComplete="name"
              placeholder="이름 입력"
              required
              value={signupForm.name}
              onChange={(event) => setSignupForm({ ...signupForm, name: event.target.value })}
            />
          </label>
          <label className="signup-field">
            <span>
              <em>*</em>핸드폰 번호
            </span>
            <input
              autoComplete="tel"
              placeholder="010-0000-0000"
              required
              value={signupForm.phoneNumber}
              onChange={(event) =>
                setSignupForm({ ...signupForm, phoneNumber: event.target.value })
              }
            />
          </label>
          <label className="signup-field">
            <span>
              <em>*</em>이메일
            </span>
            <input
              autoComplete="email"
              placeholder="tickey@example.com"
              required
              type="email"
              value={signupForm.email}
              onChange={(event) => setSignupForm({ ...signupForm, email: event.target.value })}
            />
          </label>
          <label className="signup-field">
            <span>생년월일</span>
            <div className="signup-date-input" ref={birthDateCalendarRef}>
              <input
                inputMode="numeric"
                pattern="\d{4}-\d{2}-\d{2}"
                placeholder="YYYY-MM-DD"
                value={signupForm.birthDate}
                onChange={(event) =>
                  setSignupForm({ ...signupForm, birthDate: event.target.value })
                }
              />
              <button type="button" onClick={openBirthDatePicker} aria-label="생년월일 선택" />
              {isBirthDateCalendarOpen && (
                <div className="signup-calendar" role="dialog" aria-label="생년월일 달력">
                  <div className="signup-calendar-header">
                    <button
                      type="button"
                      onClick={() => moveBirthDateCalendarMonth(-1)}
                      aria-label="이전 달"
                    >
                      ‹
                    </button>
                    <strong>{birthDateMonthLabel}</strong>
                    <button
                      type="button"
                      onClick={() => moveBirthDateCalendarMonth(1)}
                      aria-label="다음 달"
                    >
                      ›
                    </button>
                  </div>
                  <div className="signup-calendar-weekdays">
                    {calendarWeekdays.map((weekday) => (
                      <span key={weekday}>{weekday}</span>
                    ))}
                  </div>
                  <div className="signup-calendar-days">
                    {birthDateCalendarDays.map(({ date, dateText, isCurrentMonth }) => (
                      <button
                        className={[
                          isCurrentMonth ? '' : 'muted',
                          signupForm.birthDate === dateText ? 'selected' : '',
                        ]
                          .filter(Boolean)
                          .join(' ')}
                        type="button"
                        key={dateText}
                        onClick={() => selectBirthDate(date)}
                      >
                        {date.getDate()}
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </label>

          <button className="signup-submit-button" disabled={isSubmitting} type="submit">
            {isSubmitting ? '처리 중...' : '가입하기'}
          </button>
          {message && <p className="form-message">{message}</p>}
        </form>

        <div className="signup-links">
          <span>이미 계정이 있나요?</span>
          <button type="button" onClick={() => onNavigate('login')}>
            로그인
          </button>
        </div>
      </div>

      {isSignupSuccessAlertOpen && (
        <div className="signup-alert-backdrop" role="alertdialog" aria-modal="true">
          <div className="signup-alert">
            <strong>회원가입이 완료되었습니다.</strong>
            <p>로그인 화면으로 이동해서 Tickey 서비스를 이용해 주세요.</p>
            <button type="button" onClick={() => onNavigate('login')}>
              로그인 화면으로 이동
            </button>
          </div>
        </div>
      )}
    </section>
  );
}

function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="footer-inner">
        <nav className="footer-links" aria-label="서비스 정책">
          <button type="button">회사소개</button>
          <button type="button">이용약관</button>
          <button type="button">개인정보처리방침</button>
          <button type="button">청소년보호정책</button>
          <button type="button">티켓판매안내</button>
          <button type="button">고객센터</button>
        </nav>

        <div className="footer-content">
          <div className="footer-company">
            <strong>Tickey</strong>
            <p>
              주식회사 티키 · 대표이사 백범 · 사업자등록번호 214-88-73021 ·
              통신판매업신고 2026-서울강남-04812
            </p>
            <p>
              서울특별시 강남구 테헤란로 427, 12층 · 개인정보보호책임자 이서연 ·
              이메일 help@tickey.example
            </p>
            <p>
              Tickey는 통신판매중개자로서 공연 주최사가 등록한 상품 정보 및 거래에 대한
              책임은 각 판매자에게 있습니다.
            </p>
          </div>

          <div className="footer-contact">
            <span>고객센터</span>
            <strong>1588-4926</strong>
            <p>평일 09:00 - 18:00</p>
            <p>점심 12:30 - 13:30 · 주말/공휴일 휴무</p>
          </div>
        </div>

        <p className="footer-copy">© Tickey Corp. All rights reserved.</p>
      </div>
    </footer>
  );
}

function TopButton() {
  function scrollToTop() {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  return (
    <button className="top-button" type="button" onClick={scrollToTop} aria-label="맨 위로 이동">
      <span className="top-button-chevron" aria-hidden="true" />
      <span>TOP</span>
    </button>
  );
}

async function request<T = unknown>(url: string, options: RequestInit): Promise<T> {
  return requestWithAuthRetry<T>(url, options, true);
}

async function requestWithAuthRetry<T = unknown>(
  url: string,
  options: RequestInit,
  canRetryWithRefresh: boolean,
): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set('Content-Type', headers.get('Content-Type') || 'application/json');

  const accessToken = sessionStorage.getItem('ticksy.accessToken');

  if (accessToken && !headers.has('Authorization') && shouldAttachAccessToken(url)) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }

  const response = await fetch(url, {
    ...options,
    headers: {
      ...Object.fromEntries(headers.entries()),
    },
  });

  if (response.status === 401 && canRetryWithRefresh && !url.includes('/auth/reissue')) {
    const isReissued = await reissueToken();

    if (isReissued) {
      return requestWithAuthRetry<T>(url, options, false);
    }

    clearLoginStorage();
    throw new SessionExpiredError();
  }

  if (!response.ok) {
    const errorText = await response.text();
    let errorMessage = errorText;

    try {
      const errorBody = JSON.parse(errorText) as { message?: string };
      errorMessage = errorBody.message || errorText;
    } catch {
      errorMessage = errorText;
    }

    throw new ApiRequestError(response.status, errorMessage || `요청 실패: ${response.status}`);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const responseText = await response.text();

  if (!responseText) {
    return undefined as T;
  }

  return JSON.parse(responseText) as T;
}

async function reissueToken() {
  const refreshToken = sessionStorage.getItem('ticksy.refreshToken');

  if (!refreshToken) {
    return false;
  }

  try {
    const tokenResponse = await requestWithAuthRetry<TokenResponse>(
      '/client-api/api/v1/auth/reissue',
      {
        method: 'POST',
        headers: {
          'Authorization-Refresh': `Bearer ${refreshToken}`,
        },
      },
      false,
    );

    sessionStorage.setItem('ticksy.accessToken', tokenResponse.accessToken);
    sessionStorage.setItem('ticksy.refreshToken', tokenResponse.refreshToken);

    return true;
  } catch {
    return false;
  }
}

function shouldAttachAccessToken(url: string) {
  return !url.includes('/auth/login')
    && !url.includes('/auth/reissue')
    && !url.includes('/auth/logout');
}

function clearLoginStorage() {
  sessionStorage.removeItem('ticksy.accessToken');
  sessionStorage.removeItem('ticksy.refreshToken');
  sessionStorage.removeItem('ticksy.userName');
  clearQueueTokenStorage();
}

function clearQueueTokenStorage() {
  Object.keys(sessionStorage)
    .filter((key) => key.startsWith('ticksy.waitingToken.') || key.startsWith('ticksy.activeToken.'))
    .forEach((key) => sessionStorage.removeItem(key));
}

export default App;

