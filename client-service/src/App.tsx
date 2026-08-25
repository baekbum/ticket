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
  title: string;
  subtitle: string;
  date: string;
  posterUrl: string;
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

const heroEvents: TicketingEvent[] = [
  {
    title: 'Ticksy Live Festa',
    subtitle: '여름밤 야외 페스티벌',
    date: '2026.09.05',
    posterUrl: '/ticket/uploads/events/posters/26_08/5538216b-3edb-45de-90fe-5daab2bb8e53.png',
  },
  {
    title: '서울 오케스트라 갈라',
    subtitle: '클래식 대표 레퍼토리',
    date: '2026.09.11',
    posterUrl: '/ticket/uploads/events/posters/26_08/e7ee6313-d421-492a-b85c-ff82cad4c705.png',
  },
  {
    title: '팬미팅 스페셜 데이',
    subtitle: '아티스트와 만나는 하루',
    date: '2026.09.20',
    posterUrl: '/ticket/uploads/events/posters/26_08/36af27d1-24c4-4e8d-82e1-271fc0813a5d.png',
  },
  {
    title: '아트 뮤지엄 나이트',
    subtitle: '전시와 공연의 만남',
    date: '2026.10.03',
    posterUrl: '/ticket/uploads/events/posters/26_08/6d90e499-383f-4f9a-8fc9-bf02da28c55c.png',
  },
  {
    title: '락 온 더 스테이지',
    subtitle: '강렬한 밴드 라이브',
    date: '2026.10.10',
    posterUrl: '/ticket/uploads/events/posters/26_08/f11c36e3-9b14-46d9-81af-32115b58c820.png',
  },
  {
    title: '달빛 오페라 하우스',
    subtitle: '달빛 아래 펼쳐지는 감성 뮤지컬',
    date: '2026.09.07',
    posterUrl: '/ticket/uploads/events/posters/26_08/470a73ab-e05b-4241-9402-becfb3b166ad.png',
  },
  {
    title: '한강 재즈 브리즈',
    subtitle: '도심 야경과 함께하는 재즈 라이브',
    date: '2026.09.13',
    posterUrl: '/ticket/uploads/events/posters/26_08/c3df9b69-fc21-4ee6-a506-85b0a0cc2cc3.png',
  },
  {
    title: '네온 스타라이트',
    subtitle: '빛과 퍼포먼스가 만나는 팝 콘서트',
    date: '2026.09.18',
    posterUrl: '/ticket/uploads/events/posters/26_08/e7df47ec-c5c4-4ab5-bb20-18cf1fbd664a.png',
  },
  {
    title: '비밀의 서재',
    subtitle: '오래된 서재에서 시작되는 미스터리',
    date: '2026.09.22',
    posterUrl: '/ticket/uploads/events/posters/26_08/8543cb08-1e95-4a3d-83dc-b94795d5077a.png',
  },
  {
    title: '숲속 가족 음악회',
    subtitle: '아이와 함께 즐기는 가족 공연',
    date: '2026.09.27',
    posterUrl: '/ticket/uploads/events/posters/26_08/4d0228ed-3f49-4a93-9e9e-c973892a2829.png',
  },
  {
    title: '블루 웨이브 댄스',
    subtitle: '물결처럼 흐르는 컨템포러리 댄스',
    date: '2026.10.04',
    posterUrl: '/ticket/uploads/events/posters/26_08/c5a5e8ef-64ff-4cfa-84d1-1442f44ed388.png',
  },
  {
    title: '인디 루프 클럽',
    subtitle: '홍대 감성 인디 밴드 릴레이',
    date: '2026.10.09',
    posterUrl: '/ticket/uploads/events/posters/26_08/2ebf0552-8903-44ec-8190-2ffc46e4d4b2.png',
  },
  {
    title: '미라클 일루전 쇼',
    subtitle: '마술과 미디어 아트의 결합',
    date: '2026.10.12',
    posterUrl: '/ticket/uploads/events/posters/26_08/acfa0164-136e-4b98-b1b8-b8c0dae8b848.png',
  },
  {
    title: '골든 트롯 스테이지',
    subtitle: '화려한 무대의 트롯 라이브',
    date: '2026.10.18',
    posterUrl: '/ticket/uploads/events/posters/26_08/606d9c40-e10a-4ed4-b424-e916528c8928.png',
  },
  {
    title: '서울 일렉트로 밤',
    subtitle: '레이저와 비트의 일렉트로닉 나이트',
    date: '2026.10.24',
    posterUrl: '/ticket/uploads/events/posters/26_08/a21db955-fe31-49b1-9d12-ce3d0de7aed4.png',
  },
  {
    title: '빛의 정원 전시',
    subtitle: '빛과 공간을 체험하는 미디어 전시',
    date: '2026.10.31',
    posterUrl: '/ticket/uploads/events/posters/26_08/5c9b1293-2e91-4cad-83f9-c6deeb9b47ea.png',
  },
  {
    title: '가을 발라드 편지',
    subtitle: '가을밤 피아노와 보컬의 감성 무대',
    date: '2026.11.01',
    posterUrl: '/ticket/uploads/events/posters/26_08/0ef18535-3e57-4f97-9580-fefefb3a854c.png',
  },
  {
    title: '국악 달마당',
    subtitle: '전통 악기와 현대 무대의 만남',
    date: '2026.11.07',
    posterUrl: '/ticket/uploads/events/posters/26_08/3d1b79f6-664a-4567-a08c-349b2d302dad.png',
  },
  {
    title: '스탠드업 웃음 공장',
    subtitle: '개성 있는 코미디언들의 라이브 쇼',
    date: '2026.11.14',
    posterUrl: '/ticket/uploads/events/posters/26_08/24f84d82-d7bc-47d7-a2a5-55b62b209339.png',
  },
  {
    title: '우주 탐험대',
    subtitle: '아이들이 떠나는 우주 가족 뮤지컬',
    date: '2026.11.21',
    posterUrl: '/ticket/uploads/events/posters/26_08/5aa80d1c-b491-49b8-8650-337037a9292a.png',
  },
];

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
  const [posterStartIndex, setPosterStartIndex] = useState(0);
  const [visiblePosterCount, setVisiblePosterCount] = useState(5);
  const visiblePosters = Array.from({ length: visiblePosterCount }, (_, index) => {
    return heroEvents[(posterStartIndex + index) % heroEvents.length];
  });

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
      if (direction === 'next') {
        return (currentIndex + 1) % heroEvents.length;
      }

      return (currentIndex - 1 + heroEvents.length) % heroEvents.length;
    });
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
          ‹
        </button>
        <div className="poster-rail">
          {visiblePosters.map((event, index) => (
            <article className="poster-card" key={`${event.title}-${index}`}>
              <div className="poster-art">
                <img src={event.posterUrl} alt={`${event.title} 포스터`} />
              </div>
              <strong>{event.title}</strong>
              <p>{event.subtitle}</p>
              <small>{event.date}</small>
            </article>
          ))}
        </div>
        <button
          className="carousel-arrow carousel-arrow-right"
          type="button"
          aria-label="다음 공연 보기"
          onClick={() => movePosters('next')}
        >
          ›
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
            {heroEvents.slice(0, 4).map((event, index) => (
              <div className="mini-poster" key={event.title}>
                <div>
                  <img src={event.posterUrl} alt="" />
                  <span className="mini-poster-rank">{index + 1}</span>
                </div>
                <strong>{event.title}</strong>
                <span className="mini-poster-description">{event.subtitle}</span>
              </div>
            ))}
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
