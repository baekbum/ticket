import { FormEvent, ReactNode, useEffect, useState } from 'react';
import type { CSSProperties } from 'react';
import './App.css';

type Page = 'home' | 'login' | 'signup';

type LoginForm = {
  userId: string;
  password: string;
};

type SignupForm = LoginForm & {
  name: string;
  phoneNumber: string;
  email: string;
  birthDate: string;
  address: string;
};

type TokenResponse = {
  accessToken: string;
  refreshToken: string;
};

type TicketingEvent = {
  eventGroupCode: string;
  artistName: string;
  title: string;
  posterUrl: string;
  eventStartDate: string;
  eventEndDate: string;
};

const initialLoginForm: LoginForm = {
  userId: '',
  password: '',
};

const initialSignupForm: SignupForm = {
  userId: '',
  password: '',
  name: '',
  phoneNumber: '',
  email: '',
  birthDate: '',
  address: '',
};

const categories = ['콘서트', '뮤지컬/연극', '팬클럽/팬미팅', '클래식', '전시/행사', '테마/지역', '랭킹'];

function getPageFromLocation(): Page {
  const page = new URLSearchParams(window.location.search).get('page');

  if (page === 'login' || page === 'signup') {
    return page;
  }

  return 'home';
}

function getUrlForPage(page: Page) {
  const url = new URL(window.location.href);

  if (page === 'home') {
    url.searchParams.delete('page');
  } else {
    url.searchParams.set('page', page);
  }

  return `${url.pathname}${url.search}${url.hash}`;
}

function App() {
  const [page, setPage] = useState<Page>(() => getPageFromLocation());

  useEffect(() => {
    window.history.replaceState({ page: getPageFromLocation() }, '', window.location.href);

    function handlePopState() {
      setPage(getPageFromLocation());
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

  return (
    <main className="app-shell">
      {page !== 'login' && <Header currentPage={page} onNavigate={navigateToPage} />}
      {page === 'home' && <HomePage onNavigate={navigateToPage} />}
      {page === 'login' && <LoginPage onNavigate={navigateToPage} />}
      {page === 'signup' && <SignupPage onNavigate={navigateToPage} />}
      {page !== 'login' && <SiteFooter />}
      {page !== 'login' && <TopButton />}
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

function HomePage({ onNavigate }: { onNavigate: (page: Page) => void }) {
  const [soonestOnSaleEvents, setSoonestOnSaleEvents] = useState<TicketingEvent[]>([]);
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
    window.location.hash = `event-${encodeURIComponent(event.eventGroupCode)}`;
  }

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
            <button className="active-tab" type="button">
              페스티벌
            </button>
            <button type="button">오픈 예정 공연</button>
            <button type="button">이 주의 추천공연</button>
          </div>
          <div className="mini-poster-grid">
            {soonestOnSaleEvents.slice(0, 4).map((event, index) => (
              <div className="mini-poster" key={event.eventGroupCode}>
                <div>
                  <img src={event.posterUrl} alt="" />
                  <span className="mini-poster-rank">{index + 1}</span>
                </div>
                <strong>{event.title}</strong>
                <span className="mini-poster-description">{event.artistName}</span>
              </div>
            ))}
            {soonestOnSaleEvents.length === 0 && (
              <p className="mini-poster-empty">판매 중인 예정 공연이 없습니다.</p>
            )}
          </div>
        </article>

        <aside className="side-panel">
          <h2>빠른 예매</h2>
          <p>로그인하면 예매 내역과 관심 공연을 바로 확인할 수 있습니다.</p>
          <button type="button" onClick={() => onNavigate('login')}>
            로그인하고 시작하기
          </button>
        </aside>
      </section>
    </>
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
          <button type="button">아이디 찾기</button>
          <span aria-hidden="true" />
          <button type="button">비밀번호 찾기</button>
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

function SignupPage({ onNavigate }: { onNavigate: (page: Page) => void }) {
  const [signupForm, setSignupForm] = useState<SignupForm>(initialSignupForm);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [message, setMessage] = useState('');

  async function submitSignup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsSubmitting(true);
    setMessage('');

    try {
      await request('/client-api/api/v1/user/signup', {
        method: 'POST',
        body: JSON.stringify({
          ...signupForm,
          birthDate: signupForm.birthDate || null,
        }),
      });

      setSignupForm(initialSignupForm);
      setMessage('회원가입이 완료되었습니다. 로그인해 주세요.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '회원가입에 실패했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <AuthLayout
      title="회원가입"
      description="공연 예매에 필요한 기본 정보를 입력하세요."
      footer={
        <>
          이미 계정이 있나요?
          <button type="button" onClick={() => onNavigate('login')}>
            로그인
          </button>
        </>
      }
    >
      <form className="auth-form" onSubmit={submitSignup}>
        <div className="field-row">
          <label>
            아이디
            <input
              autoComplete="username"
              required
              value={signupForm.userId}
              onChange={(event) => setSignupForm({ ...signupForm, userId: event.target.value })}
            />
          </label>
          <label>
            이름
            <input
              autoComplete="name"
              required
              value={signupForm.name}
              onChange={(event) => setSignupForm({ ...signupForm, name: event.target.value })}
            />
          </label>
        </div>
        <label>
          비밀번호
          <input
            autoComplete="new-password"
            minLength={8}
            required
            type="password"
            value={signupForm.password}
            onChange={(event) => setSignupForm({ ...signupForm, password: event.target.value })}
          />
        </label>
        <div className="field-row">
          <label>
            휴대폰 번호
            <input
              autoComplete="tel"
              required
              value={signupForm.phoneNumber}
              onChange={(event) =>
                setSignupForm({ ...signupForm, phoneNumber: event.target.value })
              }
            />
          </label>
          <label>
            이메일
            <input
              autoComplete="email"
              required
              type="email"
              value={signupForm.email}
              onChange={(event) => setSignupForm({ ...signupForm, email: event.target.value })}
            />
          </label>
        </div>
        <div className="field-row">
          <label>
            생년월일
            <input
              type="date"
              value={signupForm.birthDate}
              onChange={(event) => setSignupForm({ ...signupForm, birthDate: event.target.value })}
            />
          </label>
          <label>
            주소
            <input
              autoComplete="street-address"
              value={signupForm.address}
              onChange={(event) => setSignupForm({ ...signupForm, address: event.target.value })}
            />
          </label>
        </div>
        <button className="submit-button" disabled={isSubmitting} type="submit">
          {isSubmitting ? '처리 중...' : '회원가입'}
        </button>
        {message && <p className="form-message">{message}</p>}
      </form>
    </AuthLayout>
  );
}

function AuthLayout({
  title,
  description,
  children,
  footer,
}: {
  title: string;
  description: string;
  children: ReactNode;
  footer: ReactNode;
}) {
  return (
    <section className="auth-page">
      <div className="auth-copy">
        <p className="section-kicker">Ticksy Account</p>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      <div className="auth-card">
        {children}
        <div className="auth-footer">{footer}</div>
      </div>
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
    throw new Error(errorText || `요청 실패: ${response.status}`);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export default App;


