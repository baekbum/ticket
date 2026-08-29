import { FormEvent, useEffect, useRef, useState } from 'react';
import type { CSSProperties } from 'react';
import './App.css';

type Page = 'home' | 'login' | 'signup' | 'findId' | 'findPassword' | 'eventDetail';
type FindIdMethod = 'phone' | 'email';
type HomeEventTab = 'festival' | 'openSoon' | 'weekly';

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

type TokenResponse = {
  accessToken: string;
  refreshToken: string;
};

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

const categories = ['콘서트', '뮤지컬/연극', '팬클럽/팬미팅', '클래식', '전시/행사', '테마/지역', '랭킹'];
const calendarWeekdays = ['일', '월', '화', '수', '목', '금', '토'];
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
    page === 'eventDetail'
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
  } else if (page !== 'eventDetail') {
    url.searchParams.set('page', page);
    url.searchParams.delete('eventGroupCode');
  } else {
    url.searchParams.set('page', page);
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

function App() {
  const [page, setPage] = useState<Page>(() => getPageFromLocation());
  const [selectedEventGroupCode, setSelectedEventGroupCode] = useState(
    () => new URLSearchParams(window.location.search).get('eventGroupCode') || '',
  );
  const isFullAuthPage =
    page === 'login' || page === 'signup' || page === 'findId' || page === 'findPassword';

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

  return (
    <main className="app-shell">
      {!isFullAuthPage && <Header currentPage={page} onNavigate={navigateToPage} />}
      {page === 'home' && <HomePage onSelectEvent={navigateToEventDetail} />}
      {page === 'eventDetail' && (
        <EventDetailPage eventGroupCode={selectedEventGroupCode} onNavigate={navigateToPage} />
      )}
      {page === 'login' && <LoginPage onNavigate={navigateToPage} />}
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
  onNavigate,
}: {
  currentPage: Page;
  onNavigate: (page: Page) => void;
}) {
  return (
    <header className="site-header">
      <div className="top-menu">
        <button
          className={currentPage === 'login' ? 'active-link' : ''}
          type="button"
          onClick={() => onNavigate('login')}
        >
          로그인
        </button>
        <span aria-hidden="true">|</span>
        <button
          className={currentPage === 'signup' ? 'active-link' : ''}
          type="button"
          onClick={() => onNavigate('signup')}
        >
          회원가입
        </button>
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
          <button type="button" key={category}>
            {category}
          </button>
        ))}
        <button className="my-ticket" type="button">
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

function EventDetailPage({
  eventGroupCode,
  onNavigate,
}: {
  eventGroupCode: string;
  onNavigate: (page: Page) => void;
}) {
  const [eventDetail, setEventDetail] = useState<EventDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);

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

  return (
    <section className="event-detail-page">
      <div className="event-detail-header">
        <p>공연 상세</p>
        <h1>{eventDetail.title}</h1>
      </div>

      <article className="event-booking-panel">
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
            <div>
              <dt>예매가능좌석</dt>
              <dd>{eventDetail.availableSeats.toLocaleString()}석</dd>
            </div>
          </dl>

          <div className="event-schedule-box">
            <strong>공연 회차</strong>
            <ul>
              {eventDetail.schedules.map((schedule) => (
                <li key={schedule.eventId}>
                  <span>{schedule.eventDateTime}</span>
                  <small>{schedule.availableSeats.toLocaleString()}석</small>
                </li>
              ))}
            </ul>
          </div>

          <button
            className={isBookable ? 'booking-action-button' : 'booking-action-button disabled'}
            disabled={!isBookable}
            type="button"
          >
            {eventDetail.bookingMessage}
          </button>
        </div>
      </article>

      <section className="event-description-panel">
        <h2>공연 소개</h2>
        <p>{eventDetail.description}</p>
      </section>
    </section>
  );
}

function ArrowIcon({ direction }: { direction: 'left' | 'right' }) {
  const points = direction === 'left' ? '38 10 14 32 38 54' : '26 10 50 32 26 54';

  return (
    <svg className="carousel-arrow-icon" viewBox="0 0 64 64" aria-hidden="true">
      <polyline points={points} />
    </svg>
  );
}

function LoginPage({ onNavigate }: { onNavigate: (page: Page) => void }) {
  const [loginForm, setLoginForm] = useState<LoginForm>(initialLoginForm);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [message, setMessage] = useState('');

  async function submitLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsSubmitting(true);
    setMessage('');

    try {
      const tokenResponse = await request<TokenResponse>('/client-api/api/v1/auth/login', {
        method: 'POST',
        body: JSON.stringify(loginForm),
      });

      localStorage.setItem('ticksy.accessToken', tokenResponse.accessToken);
      localStorage.setItem('ticksy.refreshToken', tokenResponse.refreshToken);
      setMessage('로그인되었습니다.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '로그인에 실패했습니다.');
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
            <input type="checkbox" />
            <span>로그인 상태 유지</span>
          </label>

          <button className="login-submit-button" disabled={isSubmitting} type="submit">
          {isSubmitting ? '처리 중...' : '로그인'}
          </button>

          {message && <p className="form-message">{message}</p>}
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
  const response = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
    ...options,
  });

  if (!response.ok) {
    const errorText = await response.text();
    let errorMessage = errorText;

    try {
      const errorBody = JSON.parse(errorText) as { message?: string };
      errorMessage = errorBody.message || errorText;
    } catch {
      errorMessage = errorText;
    }

    throw new Error(errorMessage || `요청 실패: ${response.status}`);
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

export default App;


