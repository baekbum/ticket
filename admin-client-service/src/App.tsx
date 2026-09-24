import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties, type FormEvent, type PointerEvent as ReactPointerEvent } from 'react';
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
    { key: 'inquiry', label: '1:1 문의', icon: 'messages' },
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
const embedMenus = new Map([
  ['seatRedis', 'Seat Redis'],
  ['queueRedis', 'Queue Redis'],
  ['seatReservationTest', '좌석 선점 테스트'],
  ['queueEnterTest', '대기열 진입 테스트'],
  ['dltPublishTest', 'DLT 테스트 메시지 발행'],
  ['dltSlackTest', 'DLT Slack 알림 테스트'],
  ['failureMonitoring', '장애 요약'],
]);
const rememberedIdKey = 'adminRememberedUserId';
const resizeDirections = ['n', 'e', 's', 'w', 'ne', 'se', 'sw', 'nw'] as const;
type ResizeDirection = typeof resizeDirections[number];
type EmbeddedWindow = { id: number; menu: string; title: string; version: number; maximized: boolean; minimized: boolean; zIndex: number };

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

function Login({ onLogin, dark, onToggleTheme }: { onLogin: (user: AdminUser) => void; dark: boolean; onToggleTheme: () => void }) {
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
    <button className="login-theme-toggle" type="button" onClick={onToggleTheme} aria-label={dark ? '라이트 모드로 전환' : '다크 모드로 전환'} aria-pressed={dark}>
      <Icon name={dark ? 'sun' : 'moon'} /> {dark ? '라이트 모드' : '다크 모드'}
    </button>
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

function Dashboard({ user, onLogout, dark, onToggleTheme }: { user: AdminUser; onLogout: () => void; dark: boolean; onToggleTheme: () => void }) {
  const initial = new URLSearchParams(window.location.search).get('menu') || 'user';
  const [menu, setMenu] = useState(allowedMenus.has(initial) ? initial : 'user');
  const [openGroups, setOpenGroups] = useState(() => new Set([
    menuGroups.find(group => group.items.some(item => item.key === menu))?.label || '사용자',
  ]));
  const [embeds, setEmbeds] = useState<EmbeddedWindow[]>([]);
  const mainFrameRef = useRef<HTMLIFrameElement>(null);
  const embedFrameRefs = useRef(new Map<number, HTMLIFrameElement>());
  const nextEmbedId = useRef(1);
  const nextEmbedZIndex = useRef(1);
  const embedDragRef = useRef<{ pointerId: number; popup: HTMLElement; startX: number; startY: number; left: number; top: number } | null>(null);
  const embedResizeRef = useRef<{ pointerId: number; popup: HTMLElement; direction: ResizeDirection; startX: number; startY: number; width: number; height: number; left: number; top: number } | null>(null);
  const [menuContext, setMenuContext] = useState<Record<string, unknown>>(initialMenuContext);
  const [profile, setProfile] = useState(false);
  const [currentUser, setCurrentUser] = useState(user);

  useEffect(() => {
    const listener = (event: MessageEvent) => {
      if (event.origin !== window.location.origin) return;
      const sourceEmbedId = [...embedFrameRefs.current.entries()].find(([, frame]) => event.source === frame.contentWindow)?.[0];
      if (event.source !== mainFrameRef.current?.contentWindow && sourceEmbedId === undefined) return;
      if (event.data?.type === 'admin:open-embed') {
        const title = embedMenus.get(event.data.menu);
        if (title) setEmbeds(previous => [...previous, { id: nextEmbedId.current++, menu: event.data.menu, title, version: 0, maximized: true, minimized: false, zIndex: nextEmbedZIndex.current++ }]);
      } else if (event.data?.type === 'admin:focus-embed' && sourceEmbedId !== undefined) {
        focusEmbed(sourceEmbedId);
      } else if (event.data?.type === 'admin:switch-menu' && allowedMenus.has(event.data.menu)) {
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

  function focusEmbed(id: number) {
    const zIndex = nextEmbedZIndex.current++;
    setEmbeds(previous => previous.map(item => item.id === id ? { ...item, minimized: false, zIndex } : item));
  }

  function updateEmbed(id: number, change: (item: EmbeddedWindow) => EmbeddedWindow) {
    setEmbeds(previous => previous.map(item => item.id === id ? change(item) : item));
  }

  function startEmbedDrag(event: ReactPointerEvent<HTMLDivElement>) {
    const popup = event.currentTarget.parentElement;
    if (popup?.classList.contains('maximized') || (event.target as HTMLElement).closest('button')) return;
    const layer = popup?.parentElement;
    if (!popup || !layer) return;
    const popupRect = popup.getBoundingClientRect();
    const layerRect = layer.getBoundingClientRect();
    embedDragRef.current = {
      pointerId: event.pointerId,
      popup,
      startX: event.clientX,
      startY: event.clientY,
      left: popupRect.left - layerRect.left,
      top: popupRect.top - layerRect.top,
    };
    popup.classList.add('dragging');
    event.currentTarget.setPointerCapture(event.pointerId);
  }

  function moveEmbedDrag(event: ReactPointerEvent<HTMLDivElement>) {
    const drag = embedDragRef.current;
    const popup = drag?.popup;
    const layer = popup?.parentElement;
    if (!drag || drag.pointerId !== event.pointerId || !popup || !layer) return;
    const layerRect = layer.getBoundingClientRect();
    const popupRect = popup.getBoundingClientRect();
    const left = Math.min(Math.max(0, drag.left + event.clientX - drag.startX), Math.max(0, layerRect.width - popupRect.width));
    const top = Math.min(Math.max(0, drag.top + event.clientY - drag.startY), Math.max(0, layerRect.height - popupRect.height));
    popup.style.setProperty('--embed-left', `${left}px`);
    popup.style.setProperty('--embed-top', `${top}px`);
  }

  function endEmbedDrag(event: ReactPointerEvent<HTMLDivElement>) {
    if (embedDragRef.current?.pointerId !== event.pointerId) return;
    embedDragRef.current.popup.classList.remove('dragging');
    embedDragRef.current = null;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
  }

  function startEmbedResize(event: ReactPointerEvent<HTMLDivElement>, direction: ResizeDirection) {
    const popup = event.currentTarget.parentElement;
    if (popup?.classList.contains('maximized')) return;
    const layer = popup?.parentElement;
    if (!popup || !layer) return;
    const popupRect = popup.getBoundingClientRect();
    const layerRect = layer.getBoundingClientRect();
    embedResizeRef.current = {
      pointerId: event.pointerId,
      popup,
      direction,
      startX: event.clientX,
      startY: event.clientY,
      width: popupRect.width,
      height: popupRect.height,
      left: popupRect.left - layerRect.left,
      top: popupRect.top - layerRect.top,
    };
    popup.classList.add('resizing');
    event.currentTarget.setPointerCapture(event.pointerId);
    event.preventDefault();
  }

  function moveEmbedResize(event: ReactPointerEvent<HTMLDivElement>) {
    const resize = embedResizeRef.current;
    const popup = resize?.popup;
    const layer = popup?.parentElement;
    if (!resize || resize.pointerId !== event.pointerId || !popup || !layer) return;
    const layerRect = layer.getBoundingClientRect();
    const minWidth = Math.min(320, resize.width);
    const minHeight = Math.min(240, resize.height);
    let left = resize.left;
    let top = resize.top;
    let right = resize.left + resize.width;
    let bottom = resize.top + resize.height;
    const deltaX = event.clientX - resize.startX;
    const deltaY = event.clientY - resize.startY;
    if (resize.direction.includes('w')) left = Math.max(0, Math.min(resize.left + deltaX, right - minWidth));
    if (resize.direction.includes('e')) right = Math.min(layerRect.width, Math.max(resize.left + resize.width + deltaX, left + minWidth));
    if (resize.direction.includes('n')) top = Math.max(0, Math.min(resize.top + deltaY, bottom - minHeight));
    if (resize.direction.includes('s')) bottom = Math.min(layerRect.height, Math.max(resize.top + resize.height + deltaY, top + minHeight));
    popup.style.setProperty('--embed-left', `${left}px`);
    popup.style.setProperty('--embed-top', `${top}px`);
    popup.style.setProperty('--embed-width', `${right - left}px`);
    popup.style.setProperty('--embed-height', `${bottom - top}px`);
  }

  function endEmbedResize(event: ReactPointerEvent<HTMLDivElement>) {
    if (embedResizeRef.current?.pointerId !== event.pointerId) return;
    embedResizeRef.current.popup.classList.remove('resizing');
    embedResizeRef.current = null;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
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

  const frontEmbedZIndex = Math.max(0, ...embeds.filter(item => !item.minimized).map(item => item.zIndex));

  return <div className="admin-shell">
    <header className="admin-header">
      <div className="brand"><span className="brand-mark"><Icon name="ticket" /></span> Ticket <small>Admin Console</small></div>
      <div className="header-actions"><button onClick={() => setProfile(true)}><Icon name="user" /> {currentUser.name || currentUser.userId}</button><button onClick={onToggleTheme} aria-label={dark ? '라이트 모드로 전환' : '다크 모드로 전환'} aria-pressed={dark}><Icon name={dark ? 'sun' : 'moon'} /></button><button onClick={onLogout}><Icon name="logout" /> 로그아웃</button></div>
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
      <main className="admin-main">
        <iframe ref={mainFrameRef} key={`${menu}:${JSON.stringify(menuContext)}`} title={menuGroups.flatMap(group => group.items).find(item => item.key === menu)?.label || menu} src={`/admin/legacy/fragments/${menu}.html?${new URLSearchParams({ context: JSON.stringify(menuContext) })}`} />
        {embeds.length > 0 && <div className="embed-layer">
          {embeds.map(embed => {
            const offset = ((embed.id - 1) % 5) * 36;
            const style = {
              zIndex: embed.zIndex,
              '--embed-start-left': `${12 + offset}px`,
              '--embed-start-top': `${12 + offset}px`,
              '--embed-start-height': `calc(100% - ${24 + offset}px)`,
            } as CSSProperties;
            return <section key={embed.id} style={style} className={`embed-window${embed.maximized ? ' maximized' : ''}${embed.minimized ? ' minimized' : ''}`} role="dialog" aria-label={embed.title} aria-hidden={embed.minimized} onPointerDown={() => focusEmbed(embed.id)}>
              <div className="embed-toolbar" onPointerDown={startEmbedDrag} onPointerMove={moveEmbedDrag} onPointerUp={endEmbedDrag} onPointerCancel={endEmbedDrag}>
                <strong><Icon name="database" /> {embed.title}</strong>
                <div>
                  <button type="button" onClick={() => updateEmbed(embed.id, current => ({ ...current, version: current.version + 1 }))} aria-label="팝업 새로고침" title="새로고침"><Icon name="refresh" /></button>
                  <button type="button" onClick={() => updateEmbed(embed.id, current => ({ ...current, minimized: true }))} aria-label="팝업 최소화" title="최소화"><Icon name="minus" /></button>
                  <button type="button" onClick={() => updateEmbed(embed.id, current => ({ ...current, maximized: !current.maximized }))} aria-label={embed.maximized ? '팝업 크기 복원' : '팝업 최대화'} title={embed.maximized ? '크기 복원' : '최대화'}><Icon name={embed.maximized ? 'arrows-minimize' : 'arrows-maximize'} /></button>
                  <button type="button" onClick={() => setEmbeds(previous => previous.filter(item => item.id !== embed.id))} aria-label="팝업 닫기" title="닫기"><Icon name="x" /></button>
                </div>
              </div>
              <iframe ref={frame => { if (frame) embedFrameRefs.current.set(embed.id, frame); else embedFrameRefs.current.delete(embed.id); }} key={`${embed.menu}:${embed.version}`} title={embed.title} src={`/admin/legacy/fragments/${embed.menu}.html`} />
              {resizeDirections.map(direction => <div key={direction} className={`embed-resize-handle ${direction}`} aria-hidden="true" title="드래그하여 창 크기 조절" onPointerDown={event => startEmbedResize(event, direction)} onPointerMove={moveEmbedResize} onPointerUp={endEmbedResize} onPointerCancel={endEmbedResize} />)}
            </section>;
          })}
        </div>}
        {(embeds.length > 1 || embeds.some(item => item.minimized)) && <div className="embed-switcher" role="group" aria-label="열린 팝업">
          {embeds.map(embed => <button key={embed.id} type="button" className={embed.minimized ? 'minimized' : ''} aria-label={`${embed.title} ${embed.minimized ? '복원' : '선택'}`} aria-pressed={!embed.minimized && embed.zIndex === frontEmbedZIndex} onClick={() => focusEmbed(embed.id)}>{embed.title}</button>)}
        </div>}
      </main>
    </div>
    {profile && <Profile user={currentUser} onClose={() => setProfile(false)} onUpdate={setCurrentUser} />}
  </div>;
}

export function App() {
  const [user, setUser] = useState<AdminUser | null>(null);
  const [loading, setLoading] = useState(Boolean(getAccessToken()));
  const [dark, setDark] = useState(() => {
    try { return localStorage.getItem('adminTheme') === 'dark'; }
    catch { return false; }
  });

  useLayoutEffect(() => {
    document.body.classList.toggle('dark-mode', dark);
    try { localStorage.setItem('adminTheme', dark ? 'dark' : 'light'); }
    catch { /* 저장소를 사용할 수 없어도 테마 전환은 계속합니다. */ }
  }, [dark]);

  useEffect(() => {
    if (!getAccessToken()) return;
    loadCurrentUser().then(setUser).catch(clearTokens).finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="loading-page">관리자 정보를 확인하고 있습니다…</div>;
  if (!user) return <Login onLogin={setUser} dark={dark} onToggleTheme={() => setDark(value => !value)} />;
  return <Dashboard user={user} dark={dark} onToggleTheme={() => setDark(value => !value)} onLogout={() => { void logout().finally(() => setUser(null)); }} />;
}
