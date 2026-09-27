import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import type { createAuthenticatedRequest } from './authenticatedRequest';

type Request = ReturnType<typeof createAuthenticatedRequest>;
type Mode = 'profile' | 'address';
type Profile = { userId: string; name: string; phoneNumber: string; email: string; birthDate: string | null };
type Address = { addressId: number; alias: string | null; recipientName: string; recipientPhone: string; zipCode: string; address: string; detailAddress: string | null; defaultAddress: boolean };
type AddressPage = { content: Address[]; totalPages: number; number: number };
type AddressForm = Omit<Address, 'addressId'>;
type ProfileView = 'menu' | 'profile' | 'loginHistory';
type LoginLog = {
  id: number; result: 'SUCCESS' | 'FAILURE'; authMethod: string;
  failureReason: string | null; ipAddress: string | null; userAgent: string | null; occurredAt: string;
};
type LoginLogPage = {
  content: LoginLog[];
  page: { number: number; totalPages: number; totalElements: number };
};
const emptyAddress: AddressForm = { alias: '', recipientName: '', recipientPhone: '', zipCode: '', address: '', detailAddress: '', defaultAddress: false };

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : '요청을 처리하지 못했습니다. 다시 시도해주세요.';
}

function WithdrawDialog({ request, onClose, onWithdrawn }: { request: Request; onClose: () => void; onWithdrawn: () => void }) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    const dialog = dialogRef.current;
    const previousFocus = document.activeElement;
    dialog?.showModal();
    return () => {
      dialog?.close();
      if (previousFocus instanceof HTMLElement) previousFocus.focus();
    };
  }, []);

  async function withdraw(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      await request('/user/api/v1/withdraw/me', { method: 'POST', body: JSON.stringify({ password }) });
      onWithdrawn();
    } catch (failure) {
      setError(errorMessage(failure));
      setSubmitting(false);
    }
  }

  return <dialog ref={dialogRef} className="my-ticket-withdraw-dialog" aria-labelledby="my-ticket-withdraw-title"
    onCancel={(event) => { event.preventDefault(); if (!submitting) onClose(); }}>
    <h2 id="my-ticket-withdraw-title">회원 탈퇴</h2>
    <p>탈퇴하면 계정에 다시 로그인할 수 없습니다. 계속하려면 비밀번호를 입력해 주세요.</p>
    {error && <p className="my-ticket-management-error" role="alert">{error}</p>}
    <form onSubmit={withdraw}>
      <label>비밀번호<input autoFocus required type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} /></label>
      <div className="my-ticket-management-form-actions"><button type="button" disabled={submitting} onClick={onClose}>취소</button><button type="submit" disabled={submitting}>{submitting ? '확인 중…' : '탈퇴하기'}</button></div>
    </form>
  </dialog>;
}

export default function AccountManagement({ mode, request, onClose, onWithdrawn }: { mode: Mode; request: Request; onClose: () => void; onWithdrawn: () => void }) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [profile, setProfile] = useState<Profile | null>(null);
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [addressForm, setAddressForm] = useState<AddressForm>(emptyAddress);
  const [showAddressForm, setShowAddressForm] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [refresh, setRefresh] = useState(0);
  const [withdrawOpen, setWithdrawOpen] = useState(false);
  const [profileView, setProfileView] = useState<ProfileView>('menu');
  const [loginLogs, setLoginLogs] = useState<LoginLog[]>([]);
  const [loginLogPage, setLoginLogPage] = useState(0);
  const [loginLogTotalPages, setLoginLogTotalPages] = useState(0);
  const [loginLogTotalElements, setLoginLogTotalElements] = useState(0);
  const [loginLogPeriodDays, setLoginLogPeriodDays] = useState(30);
  const [loginLogLoadingMore, setLoginLogLoadingMore] = useState(false);
  const loginLogScrollRef = useRef<HTMLDivElement>(null);
  const loginLogSentinelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    const previousFocus = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    dialog?.showModal();
    document.body.style.overflow = 'hidden';
    return () => {
      dialog?.close();
      document.body.style.overflow = previousOverflow;
      if (previousFocus instanceof HTMLElement) previousFocus.focus();
    };
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    async function load() {
      if (mode === 'profile' && profileView === 'menu') {
        setLoading(false);
        return;
      }
      const loadingMore = mode === 'profile' && profileView === 'loginHistory' && loginLogPage > 0;
      if (loadingMore) setLoginLogLoadingMore(true);
      else setLoading(true);
      setError('');
      try {
        if (mode === 'profile' && profileView === 'profile') {
          const data = await request<Profile>('/user/api/v1/select/me', { method: 'GET', signal: controller.signal });
          if (!controller.signal.aborted) setProfile(data);
        } else if (mode === 'profile') {
          const data = await request<LoginLogPage>(`/audit/api/v1/login-log/me?page=${loginLogPage}&size=5&periodDays=${loginLogPeriodDays}`, { method: 'GET', signal: controller.signal });
          if (!controller.signal.aborted) {
            const nextLogs = data.content || [];
            setLoginLogs((current) => loginLogPage === 0
              ? nextLogs
              : [...current, ...nextLogs.filter((next) => !current.some((item) => item.id === next.id))]);
            setLoginLogTotalPages(data.page?.totalPages || 0);
            setLoginLogTotalElements(data.page?.totalElements || 0);
          }
        } else {
          const data = await request<AddressPage>('/user/api/v1/address/select/me', { method: 'POST', body: JSON.stringify({ page, size: 10 }), signal: controller.signal });
          if (!controller.signal.aborted) { setAddresses(data.content); setTotalPages(data.totalPages); }
        }
      } catch (failure) {
        if (!controller.signal.aborted) setError(errorMessage(failure));
      } finally {
        if (!controller.signal.aborted) {
          if (loadingMore) setLoginLogLoadingMore(false);
          else setLoading(false);
        }
      }
    }
    void load();
    return () => controller.abort();
  }, [mode, profileView, request, page, refresh, loginLogPage, loginLogPeriodDays]);

  useEffect(() => {
    const sentinel = loginLogSentinelRef.current;
    if (profileView !== 'loginHistory' || !sentinel || loading || loginLogLoadingMore || error || loginLogPage + 1 >= loginLogTotalPages) return;

    const observer = new IntersectionObserver((entries) => {
      if (entries[0]?.isIntersecting) {
        observer.disconnect();
        setLoginLogPage((current) => current + 1);
      }
    }, { root: loginLogScrollRef.current, rootMargin: '120px 0px' });
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [profileView, loading, loginLogLoadingMore, error, loginLogPage, loginLogTotalPages]);

  function moveProfileView(next: ProfileView) {
    setError('');
    setNotice('');
    setProfileView(next);
    dialogRef.current?.scrollTo({ top: 0 });
    if (next === 'loginHistory') {
      setLoginLogs([]);
      setLoginLogPage(0);
      setLoginLogTotalPages(0);
      setLoginLogTotalElements(0);
    }
  }

  function managementTitle() {
    if (mode === 'address') return '배송지 관리';
    if (profileView === 'profile') return '기본정보 확인';
    if (profileView === 'loginHistory') return '로그인 기록 확인';
    return '내 정보 관리';
  }

  function describeDevice(userAgent: string | null) {
    if (!userAgent) return '알 수 없는 기기';
    const browser = userAgent.includes('Edg/') ? 'Edge'
      : userAgent.includes('Chrome/') ? 'Chrome'
        : userAgent.includes('Firefox/') ? 'Firefox'
          : userAgent.includes('Safari/') ? 'Safari' : '기타 브라우저';
    const os = userAgent.includes('Windows') ? 'Windows'
      : userAgent.includes('Android') ? 'Android'
        : /iPhone|iPad/.test(userAgent) ? 'iOS'
          : userAgent.includes('Mac OS') ? 'macOS'
            : userAgent.includes('Linux') ? 'Linux' : '기타 OS';
    return `${os} · ${browser}`;
  }

  function formatLoginDate(value: string) {
    return value ? value.replace('T', ' ').slice(0, 19) : '-';
  }

  async function saveProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!profile) return;
    setSaving(true); setError(''); setNotice('');
    try {
      const updated = await request<Profile>('/user/api/v1/update/me', { method: 'PUT', body: JSON.stringify({ phoneNumber: profile.phoneNumber.trim(), email: profile.email.trim() }) });
      setProfile(updated);
      setNotice('기본정보를 저장했습니다.');
    } catch (failure) { setError(errorMessage(failure)); }
    finally { setSaving(false); }
  }

  async function saveAddress(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true); setError(''); setNotice('');
    try {
      const body = JSON.stringify({ ...addressForm, alias: addressForm.alias?.trim(), recipientName: addressForm.recipientName.trim(), recipientPhone: addressForm.recipientPhone.trim(), zipCode: addressForm.zipCode.trim(), address: addressForm.address.trim(), detailAddress: addressForm.detailAddress?.trim() });
      if (editingId == null) await request('/user/api/v1/address/insert/me', { method: 'POST', body });
      else await request(`/user/api/v1/address/update/me/${editingId}`, { method: 'PUT', body });
      setShowAddressForm(false); setEditingId(null); setAddressForm(emptyAddress);
      setNotice('배송지를 저장했습니다.'); setRefresh((value) => value + 1);
    } catch (failure) { setError(errorMessage(failure)); }
    finally { setSaving(false); }
  }

  async function deleteAddress(addressId: number) {
    if (!window.confirm('이 배송지를 삭제하시겠습니까?')) return;
    setSaving(true); setError(''); setNotice('');
    try {
      await request(`/user/api/v1/address/delete/me/${addressId}`, { method: 'DELETE' });
      setNotice('배송지를 삭제했습니다.');
      if (addresses.length === 1 && page > 0) setPage(page - 1);
      else setRefresh((value) => value + 1);
    } catch (failure) { setError(errorMessage(failure)); }
    finally { setSaving(false); }
  }

  return <>{withdrawOpen && <WithdrawDialog request={request} onClose={() => setWithdrawOpen(false)} onWithdrawn={onWithdrawn} />}
    <dialog ref={dialogRef} className={`my-ticket-management${mode === 'profile' && profileView === 'loginHistory' ? ' my-ticket-management-login-history' : ''}`} aria-labelledby="my-ticket-management-title"
    onCancel={(event) => { event.preventDefault(); if (!saving) onClose(); }}>
    <div className="my-ticket-management-heading">
      <div className="my-ticket-management-heading-main">
        {mode === 'profile' && profileView !== 'menu' && <button type="button" className="my-ticket-management-back" onClick={() => moveProfileView('menu')} aria-label="내 정보 관리 목록으로 돌아가기">←</button>}
        <h2 id="my-ticket-management-title">{managementTitle()}</h2>
      </div>
      <button type="button" onClick={onClose} disabled={saving} aria-label="관리 화면 닫기">×</button>
    </div>
    {error && <p className="my-ticket-management-error" role="alert">{error}</p>}
    {notice && <p className="my-ticket-management-notice" role="status">{notice}</p>}
    {loading ? <p className="my-ticket-state" role="status">정보를 불러오는 중입니다.</p> : mode === 'profile' && profileView === 'menu' ? <div className="my-ticket-management-menu">
      <button type="button" className="my-ticket-management-card" onClick={() => moveProfileView('profile')}>
        <span className="my-ticket-management-card-icon" aria-hidden="true">👤</span>
        <span><strong>기본정보 확인</strong><small>연락처와 이메일 등 회원 정보를 확인하고 수정합니다.</small></span>
        <b aria-hidden="true">›</b>
      </button>
      <button type="button" className="my-ticket-management-card" onClick={() => moveProfileView('loginHistory')}>
        <span className="my-ticket-management-card-icon" aria-hidden="true">🔐</span>
        <span><strong>로그인 기록 확인</strong><small>최근 로그인 결과와 접속 기기, IP 주소를 확인합니다.</small></span>
        <b aria-hidden="true">›</b>
      </button>
    </div> : mode === 'profile' && profileView === 'profile' ? profile && <div className="my-ticket-profile-view">
      <form className="my-ticket-management-form" onSubmit={saveProfile}>
        <label>아이디<input value={profile.userId} readOnly /></label>
        <label>이름<input value={profile.name} readOnly /></label>
        <label>휴대전화<input type="tel" value={profile.phoneNumber || ''} onChange={(event) => setProfile({ ...profile, phoneNumber: event.target.value })} /></label>
        <label>이메일<input type="email" value={profile.email || ''} onChange={(event) => setProfile({ ...profile, email: event.target.value })} /></label>
        <label>생년월일<input type="date" value={profile.birthDate || ''} disabled /></label>
        <div className="my-ticket-management-form-actions"><button type="submit" disabled={saving}>{saving ? '저장 중…' : '저장'}</button></div>
      </form>
      <div className="my-ticket-withdraw-action"><button type="button" onClick={() => setWithdrawOpen(true)}>회원 탈퇴</button></div>
      </div> : mode === 'profile' ? <div className="my-ticket-login-history">
        <div className="my-ticket-login-toolbar">
          <label>조회 기간<select value={loginLogPeriodDays} onChange={(event) => {
            setLoginLogs([]);
            setLoginLogPage(0);
            setLoginLogTotalPages(0);
            setLoginLogTotalElements(0);
            setLoginLogPeriodDays(Number(event.target.value));
            loginLogScrollRef.current?.scrollTo({ top: 0 });
          }}><option value={30}>최근 1개월</option><option value={90}>최근 3개월</option><option value={180}>최근 6개월</option><option value={365}>최근 1년</option></select></label>
          <strong>{loginLogTotalElements.toLocaleString()}건</strong>
        </div>
        <div ref={loginLogScrollRef} className="my-ticket-login-scroll">
          {loginLogs.length === 0 ? <p className="my-ticket-state">조회된 로그인 기록이 없습니다.</p> : <div className="my-ticket-login-list">
            {loginLogs.map((item) => <article key={item.id} className="my-ticket-login-item">
              <span className={`my-ticket-login-result ${item.result === 'SUCCESS' ? 'success' : 'failure'}`}>{item.result === 'SUCCESS' ? '성공' : '실패'}</span>
              <div className="my-ticket-login-primary"><strong>{formatLoginDate(item.occurredAt)}</strong><span>{describeDevice(item.userAgent)}</span></div>
              <dl><div><dt>IP</dt><dd>{item.ipAddress || '-'}</dd></div><div><dt>인증</dt><dd>{item.authMethod || '-'}</dd></div>{item.failureReason && <div><dt>사유</dt><dd>{item.failureReason}</dd></div>}</dl>
            </article>)}
          </div>}
          <div ref={loginLogSentinelRef} className="my-ticket-login-sentinel" aria-hidden="true" />
          {loginLogLoadingMore && <p className="my-ticket-login-loading" role="status">기록을 더 불러오는 중입니다.</p>}
        </div>
        <div className="my-ticket-management-list-action"><button type="button" onClick={() => moveProfileView('menu')}>목록으로</button></div>
      </div> : <>
        <div className="my-ticket-address-toolbar"><button type="button" onClick={() => { setEditingId(null); setAddressForm(emptyAddress); setShowAddressForm(true); setError(''); }}>+ 배송지 추가</button></div>
        {addresses.length === 0 && <p className="my-ticket-state">등록된 배송지가 없습니다.</p>}
        <div className="my-ticket-address-list">{addresses.map((item) => <article key={item.addressId}>
          <div><strong>{item.alias || '배송지'} {item.defaultAddress && <em>기본 배송지</em>}</strong><p>{item.recipientName} · {item.recipientPhone}</p><p>({item.zipCode}) {item.address} {item.detailAddress}</p></div>
          <div className="my-ticket-address-actions"><button type="button" onClick={() => { setEditingId(item.addressId); setAddressForm({ alias: item.alias || '', recipientName: item.recipientName, recipientPhone: item.recipientPhone, zipCode: item.zipCode, address: item.address, detailAddress: item.detailAddress || '', defaultAddress: item.defaultAddress }); setShowAddressForm(true); setError(''); }}>수정</button><button type="button" disabled={saving} onClick={() => void deleteAddress(item.addressId)}>삭제</button></div>
        </article>)}</div>
        {totalPages > 1 && <nav className="my-ticket-pagination" aria-label="배송지 페이지"><button type="button" disabled={page === 0} onClick={() => setPage(page - 1)}>이전</button><span>{page + 1} / {totalPages}</span><button type="button" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>다음</button></nav>}
        {showAddressForm && <form className="my-ticket-management-form my-ticket-address-form" onSubmit={saveAddress}>
          <h3>{editingId == null ? '배송지 추가' : '배송지 수정'}</h3>
          <label>배송지 이름<input value={addressForm.alias || ''} onChange={(event) => setAddressForm({ ...addressForm, alias: event.target.value })} placeholder="예: 집" /></label>
          <label>받는 분<input required value={addressForm.recipientName} onChange={(event) => setAddressForm({ ...addressForm, recipientName: event.target.value })} /></label>
          <label>연락처<input required type="tel" value={addressForm.recipientPhone} onChange={(event) => setAddressForm({ ...addressForm, recipientPhone: event.target.value })} /></label>
          <label>우편번호<input required value={addressForm.zipCode} onChange={(event) => setAddressForm({ ...addressForm, zipCode: event.target.value })} /></label>
          <label>주소<input required value={addressForm.address} onChange={(event) => setAddressForm({ ...addressForm, address: event.target.value })} /></label>
          <label>상세주소<input value={addressForm.detailAddress || ''} onChange={(event) => setAddressForm({ ...addressForm, detailAddress: event.target.value })} /></label>
          <label className="my-ticket-default-check"><input type="checkbox" checked={addressForm.defaultAddress} onChange={(event) => setAddressForm({ ...addressForm, defaultAddress: event.target.checked })} />기본 배송지로 설정</label>
          <div className="my-ticket-management-form-actions"><button type="button" onClick={() => setShowAddressForm(false)}>취소</button><button type="submit" disabled={saving}>{saving ? '저장 중…' : '저장'}</button></div>
        </form>}
      </>}
  </dialog></>;
}
