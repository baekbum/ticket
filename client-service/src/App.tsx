import { FormEvent, ReactNode, useEffect, useState } from 'react';
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

function App() {
  const [page, setPage] = useState<Page>('home');

  return (
    <main className="app-shell">
      <Header currentPage={page} onNavigate={setPage} />
      {page === 'home' && <HomePage onNavigate={setPage} />}
      {page === 'login' && <LoginPage onNavigate={setPage} />}
      {page === 'signup' && <SignupPage onNavigate={setPage} />}
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
        <div className="ad-banner">
          <strong>Ticksy Pick</strong>
          <span>이번 주 오픈 공연 한눈에 보기</span>
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
  const visiblePosters =
    soonestOnSaleEvents.length > 0
      ? Array.from({ length: Math.min(visiblePosterCount, soonestOnSaleEvents.length) }, (_, index) => {
          return soonestOnSaleEvents[(posterStartIndex + index) % soonestOnSaleEvents.length];
        })
      : [];

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

  function movePosters(direction: 'prev' | 'next') {
    setPosterStartIndex((currentIndex) => {
      if (soonestOnSaleEvents.length === 0) {
        return 0;
      }

      if (direction === 'next') {
        return (currentIndex + 1) % soonestOnSaleEvents.length;
      }

      return (currentIndex - 1 + soonestOnSaleEvents.length) % soonestOnSaleEvents.length;
    });
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

  return (
    <>
      <section className="poster-carousel" aria-label="주요 공연">
        <button
          className="carousel-arrow carousel-arrow-left"
          type="button"
          aria-label="이전 공연 보기"
          onClick={() => movePosters('prev')}
        >
          <span aria-hidden="true">‹</span>
        </button>
        <div className="poster-rail">
          {visiblePosters.map((event) => (
            <article className="poster-card" key={event.eventGroupCode}>
              <div className="poster-art">
                <img src={event.posterUrl} alt={`${event.title} 포스터`} />
              </div>
              <strong>{event.title}</strong>
              <p>{event.artistName}</p>
              <small>{formatEventDateRange(event)}</small>
            </article>
          ))}
          {visiblePosters.length === 0 && (
            <div className="poster-empty">판매 중인 예정 공연이 없습니다.</div>
          )}
        </div>
        <button
          className="carousel-arrow carousel-arrow-right"
          type="button"
          aria-label="다음 공연 보기"
          onClick={() => movePosters('next')}
        >
          <span aria-hidden="true">›</span>
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
    <AuthLayout
      title="로그인"
      description="Ticksy 계정으로 예매와 마이티켓 서비스를 이용하세요."
      footer={
        <>
          아직 계정이 없나요?
          <button type="button" onClick={() => onNavigate('signup')}>
            회원가입
          </button>
        </>
      }
    >
      <form className="auth-form" onSubmit={submitLogin}>
        <label>
          아이디
          <input
            autoComplete="username"
            required
            value={loginForm.userId}
            onChange={(event) => setLoginForm({ ...loginForm, userId: event.target.value })}
          />
        </label>
        <label>
          비밀번호
          <input
            autoComplete="current-password"
            required
            type="password"
            value={loginForm.password}
            onChange={(event) => setLoginForm({ ...loginForm, password: event.target.value })}
          />
        </label>
        <button className="submit-button" disabled={isSubmitting} type="submit">
          {isSubmitting ? '처리 중...' : '로그인'}
        </button>
        {message && <p className="form-message">{message}</p>}
      </form>
    </AuthLayout>
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

