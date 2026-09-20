import { useEffect, useState, type FormEvent } from 'react';
import { adminFetch, clearTokens, getAccessToken, loadCurrentUser, login, logout, type AdminUser } from './api';

type Menu = { key: string; label: string; icon: string };
const menuGroups: { label: string; icon: string; items: Menu[] }[] = [
  { label: '사용자', icon: 'users', items: [{ key: 'user', label: '사용자 관리', icon: 'user-cog' }] },
  { label: '공연', icon: 'calendar-event', items: [
    { key: 'event', label: '이벤트 관리', icon: 'calendar-event' },
    { key: 'area', label: '구역/좌석 관리', icon: 'armchair' },
  ] },
  { label: '예매', icon: 'calendar-stats', items: [
    { key: 'reservation', label: '예매 관리', icon: 'calendar-stats' },
    { key: 'reservationDelivery', label: '배송 관리', icon: 'truck-delivery' },
    { key: 'paymentRefundProcess', label: '환불 처리 현황', icon: 'refresh-alert' },
  ] },
  { label: '쿠폰', icon: 'discount-2', items: [
    { key: 'coupon', label: '쿠폰 관리', icon: 'discount-check' },
    { key: 'userCoupon', label: '사용자 쿠폰', icon: 'ticket-off' },
  ] },
  { label: '고객지원', icon: 'help-circle', items: [
    { key: 'notice', label: '공지사항', icon: 'speakerphone' },
    { key: 'faq', label: 'FAQ', icon: 'message-question' },
  ] },
  { label: '감사/운영', icon: 'shield-search', items: [
    { key: 'auditLog', label: '감사 로그', icon: 'history' },
    { key: 'monitoring', label: '모니터링', icon: 'chart-line' },
  ] },
  { label: 'Redis', icon: 'database', items: [
    { key: 'redisHub', label: 'Redis 관리', icon: 'database' },
    { key: 'seatCacheSyncFailures', label: 'Redis 보정 이력', icon: 'refresh-alert' },
  ] },
  { label: 'DLQ', icon: 'alert-triangle', items: [
    { key: 'kafkaDlq', label: 'DLQ 관리', icon: 'inbox' },
    { key: 'kafkaDlqHistory', label: 'DLQ 처리 이력', icon: 'list-details' },
  ] },
  { label: '개발/테스트', icon: 'flask', items: [{ key: 'testHub', label: '테스트', icon: 'flask' }] },
];
const allowedMenus = new Set(menuGroups.flatMap(group => group.items.map(item => item.key)));
const rememberedIdKey = 'adminRememberedUserId';

function Icon({ name }: { name: string }) { return <i className={`ti ti-${name}`} aria-hidden="true" />; }

function getRememberedId(): string {
  try { return localStorage.getItem(rememberedIdKey) || ''; }
  catch { return ''; }
}

function initialMenuContext(): Record<string, unknown> {
  const query = new URLSearchParams(window.location.search);
  const encoded = query.get('context');
  if (encoded) {
    try {
      const context = JSON.parse(encoded) as unknown;
      if (context && typeof context === 'object' && !Array.isArray(context)) return context as Record<string, unknown>;
    } catch { /* 잘못된 화면 주소의 조건은 무시합니다. */ }
  }
  const eventId = query.get('eventId');
  return eventId ? { eventId } : {};
}

function Login({ onLogin }: { onLogin: (user: AdminUser) => void }) {
  const [userId, setUserId] = useState(getRememberedId);
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberId, setRememberId] = useState(() => Boolean(getRememberedId()));
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      const trimmedUserId = userId.trim();
      const user = await login(trimmedUserId, password);
      try {
        if (rememberId) localStorage.setItem(rememberedIdKey, trimmedUserId);
        else localStorage.removeItem(rememberedIdKey);
      } catch { /* 저장소를 사용할 수 없어도 로그인은 계속합니다. */ }
      onLogin(user);
    }
    catch (reason) { setError(reason instanceof Error ? reason.message : '로그인에 실패했습니다.'); }
    finally { setBusy(false); }
  }

  return <div className="login-page">
    <form className="login-card" onSubmit={submit}>
      <div className="brand"><span className="brand-mark"><Icon name="ticket" /></span> Ticket <small>Admin</small></div>
      <p className="eyebrow">SECURE ADMIN PORTAL</p>
      <h1>관리자 로그인</h1>
      <p className="muted">관리 콘솔에 접속하려면 계정 정보를 입력하세요.</p>
      <label className="field-label" htmlFor="admin-login-user-id">아이디</label>
      <input id="admin-login-user-id" autoComplete="username" value={userId} onChange={event => setUserId(event.target.value)} required />
      <label className="field-label" htmlFor="admin-login-password">비밀번호</label>
      <div className="password-field">
        <input id="admin-login-password" type={showPassword ? 'text' : 'password'} autoComplete="current-password" value={password} onChange={event => setPassword(event.target.value)} required />
        <button type="button" onClick={() => setShowPassword(value => !value)} aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'} aria-pressed={showPassword}>{showPassword ? '숨기기' : '보기'}</button>
      </div>
      <label className="remember-id"><input type="checkbox" checked={rememberId} onChange={event => {
        setRememberId(event.target.checked);
        if (!event.target.checked) {
          try { localStorage.removeItem(rememberedIdKey); }
          catch { /* 저장소를 사용할 수 없어도 체크 해제는 계속합니다. */ }
        }
      }} />아이디 기억</label>
      {error && <p className="form-error" role="alert">{error}</p>}
      <button className="primary-button" disabled={busy} type="submit">{busy ? '확인 중…' : '로그인하기'}</button>
    </form>
  </div>;
}

function Profile({ user, onClose, onUpdate }: { user: AdminUser; onClose: () => void; onUpdate: (user: AdminUser) => void }) {
  const [verified, setVerified] = useState(false);
  const [password, setPassword] = useState('');
  const [edit, setEdit] = useState(false);
  const [form, setForm] = useState({
    userId: user.userId, name: user.name || '', email: user.email || '',
    phoneNumber: user.phoneNumber || '', birthDate: user.birthDate || '', address: user.address || '',
  });
  const [message, setMessage] = useState('');

  async function verify() {
    try {
      const response = await adminFetch('/user/api/v1/manage/validate/info', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId: user.userId, password }),
      });
      setVerified(response.ok);
      setMessage(response.ok ? '비밀번호가 확인되었습니다.' : '비밀번호를 확인해주세요.');
    } catch { setMessage('비밀번호 확인 요청에 실패했습니다.'); }
  }

  async function save() {
    try {
      const response = await adminFetch('/user/api/v1/manage/update/me', {
        method: 'PUT', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: form.email, phoneNumber: form.phoneNumber, birthDate: form.birthDate || null, address: form.address }),
      });
      if (!response.ok) { setMessage('정보 수정에 실패했습니다.'); return; }
      onUpdate(await response.json() as AdminUser);
      onClose();
    } catch { setMessage('정보 수정 요청에 실패했습니다.'); }
  }

  return <div className="dialog-backdrop" role="presentation" onClick={onClose}>
    <section className="profile-dialog" role="dialog" aria-modal="true" aria-label="내 정보" onClick={event => event.stopPropagation()}>
      <div className="dialog-heading"><h2>내 정보</h2><button onClick={onClose} aria-label="닫기"><Icon name="x" /></button></div>
      {(['userId', 'name', 'email', 'phoneNumber', 'birthDate', 'address'] as const).map(key =>
        <label key={key}>{({ userId: '아이디', name: '이름', email: '이메일', phoneNumber: '휴대폰 번호', birthDate: '생년월일', address: '주소' })[key]}
          <input type={key === 'birthDate' ? 'date' : key === 'email' ? 'email' : 'text'} disabled={!edit || key === 'userId' || key === 'name'} value={form[key]} onChange={event => setForm({ ...form, [key]: event.target.value })} />
        </label>)}
      {!verified && <div className="verify-row"><input type="password" placeholder="현재 비밀번호" value={password} onChange={event => setPassword(event.target.value)} /><button onClick={verify}>비밀번호 확인</button></div>}
      {message && <p className="muted" role="status">{message}</p>}
      <div className="dialog-actions">{verified && <button onClick={() => edit ? void save() : setEdit(true)}>{edit ? '저장' : '정보 수정'}</button>}<button onClick={onClose}>닫기</button></div>
    </section>
  </div>;
}

function Dashboard({ user, onLogout }: { user: AdminUser; onLogout: () => void }) {
  const initial = new URLSearchParams(window.location.search).get('menu') || 'user';
  const [menu, setMenu] = useState(allowedMenus.has(initial) ? initial : 'user');
  const [openGroups, setOpenGroups] = useState(() => new Set([
    menuGroups.find(group => group.items.some(item => item.key === menu))?.label || '사용자',
  ]));
  const [menuContext, setMenuContext] = useState<Record<string, unknown>>(initialMenuContext);
  const [profile, setProfile] = useState(false);
  const [currentUser, setCurrentUser] = useState(user);
  const [dark, setDark] = useState(localStorage.getItem('adminTheme') === 'dark');

  useEffect(() => {
    const listener = (event: MessageEvent) => {
      if (event.origin !== window.location.origin || event.data?.type !== 'admin:switch-menu') return;
      if (allowedMenus.has(event.data.menu)) {
        setMenuContext(event.data.context && typeof event.data.context === 'object' ? event.data.context : {});
        setMenu(event.data.menu);
      }
    };
    window.addEventListener('message', listener);
    return () => window.removeEventListener('message', listener);
  }, []);

  useEffect(() => {
    const group = menuGroups.find(item => item.items.some(child => child.key === menu));
    if (!group) return;
    setOpenGroups(previous => {
      if (previous.has(group.label)) return previous;
      const next = new Set(previous);
      next.add(group.label);
      return next;
    });
  }, [menu]);

  function toggleGroup(label: string) {
    setOpenGroups(previous => {
      const next = new Set(previous);
      if (next.has(label)) next.delete(label);
      else next.add(label);
      return next;
    });
  }

  useEffect(() => {
    const url = new URL(window.location.href);
    url.searchParams.set('menu', menu);
    if (typeof menuContext.eventId === 'string') url.searchParams.set('eventId', menuContext.eventId);
    else url.searchParams.delete('eventId');
    if (Object.keys(menuContext).length) url.searchParams.set('context', JSON.stringify(menuContext));
    else url.searchParams.delete('context');
    window.history.replaceState(null, '', url);
  }, [menu, menuContext]);

  useEffect(() => { document.body.classList.toggle('dark-mode', dark); localStorage.setItem('adminTheme', dark ? 'dark' : 'light'); }, [dark]);

  return <div className="admin-shell">
    <header className="admin-header">
      <div className="brand"><span className="brand-mark"><Icon name="ticket" /></span> Ticket <small>Admin Console</small></div>
      <div className="header-actions"><button onClick={() => setProfile(true)}><Icon name="user" /> {currentUser.name || currentUser.userId}</button><button onClick={() => setDark(!dark)} aria-label="테마 변경"><Icon name={dark ? 'sun' : 'moon'} /></button><button onClick={onLogout}><Icon name="logout" /> 로그아웃</button></div>
    </header>
    <div className="admin-layout">
      <nav className="admin-sidebar" aria-label="관리 메뉴">
        {menuGroups.map((group, index) => {
          const isOpen = openGroups.has(group.label);
          const itemsId = `admin-menu-group-${index}`;
          return <section key={group.label}>
            <button type="button" className="group-toggle" aria-expanded={isOpen} aria-controls={itemsId} onClick={() => toggleGroup(group.label)}>
              <span><Icon name={group.icon} /> {group.label}</span><Icon name="chevron-down" />
            </button>
            <div id={itemsId} className="group-items" hidden={!isOpen}>{group.items.map(item =>
              <button type="button" key={item.key} className={`menu-item${menu === item.key ? ' selected' : ''}`} aria-current={menu === item.key ? 'page' : undefined} onClick={() => { setMenuContext({}); setMenu(item.key); }}><Icon name={item.icon} /> {item.label}</button>
            )}</div>
          </section>;
        })}
      </nav>
      <main className="admin-main"><iframe key={`${menu}:${JSON.stringify(menuContext)}`} title={menuGroups.flatMap(group => group.items).find(item => item.key === menu)?.label || menu} src={`/admin/legacy/fragments/${menu}.html?${new URLSearchParams({ context: JSON.stringify(menuContext) })}`} /></main>
    </div>
    {profile && <Profile user={currentUser} onClose={() => setProfile(false)} onUpdate={setCurrentUser} />}
  </div>;
}

export function App() {
  const [user, setUser] = useState<AdminUser | null>(null);
  const [loading, setLoading] = useState(Boolean(getAccessToken()));

  useEffect(() => {
    if (!getAccessToken()) return;
    loadCurrentUser().then(setUser).catch(clearTokens).finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="loading-page">관리자 정보를 확인하고 있습니다…</div>;
  if (!user) return <Login onLogin={setUser} />;
  return <Dashboard user={user} onLogout={() => { void logout().finally(() => setUser(null)); }} />;
}
