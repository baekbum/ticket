import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import type { createAuthenticatedRequest } from './authenticatedRequest';

type Request = ReturnType<typeof createAuthenticatedRequest>;
type Mode = 'profile' | 'address';
type Profile = { userId: string; name: string; phoneNumber: string; email: string; birthDate: string | null };
type Address = { addressId: number; alias: string | null; recipientName: string; recipientPhone: string; zipCode: string; address: string; detailAddress: string | null; defaultAddress: boolean };
type AddressPage = { content: Address[]; totalPages: number; number: number };
type AddressForm = Omit<Address, 'addressId'>;
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
      setLoading(true);
      setError('');
      try {
        if (mode === 'profile') {
          const data = await request<Profile>('/user/api/v1/select/me', { method: 'GET', signal: controller.signal });
          if (!controller.signal.aborted) setProfile(data);
        } else {
          const data = await request<AddressPage>('/user/api/v1/address/select/me', { method: 'POST', body: JSON.stringify({ page, size: 10 }), signal: controller.signal });
          if (!controller.signal.aborted) { setAddresses(data.content); setTotalPages(data.totalPages); }
        }
      } catch (failure) {
        if (!controller.signal.aborted) setError(errorMessage(failure));
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    void load();
    return () => controller.abort();
  }, [mode, request, page, refresh]);

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
    <dialog ref={dialogRef} className="my-ticket-management" aria-labelledby="my-ticket-management-title"
    onCancel={(event) => { event.preventDefault(); if (!saving) onClose(); }}>
    <div className="my-ticket-management-heading"><h2 id="my-ticket-management-title">{mode === 'profile' ? '기본정보 관리' : '배송지 관리'}</h2><button type="button" onClick={onClose} disabled={saving} aria-label="관리 화면 닫기">×</button></div>
    {error && <p className="my-ticket-management-error" role="alert">{error}</p>}
    {notice && <p className="my-ticket-management-notice" role="status">{notice}</p>}
    {loading ? <p className="my-ticket-state" role="status">정보를 불러오는 중입니다.</p> : mode === 'profile' ? profile && <>
      <form className="my-ticket-management-form" onSubmit={saveProfile}>
        <label>아이디<input value={profile.userId} readOnly /></label>
        <label>이름<input value={profile.name} readOnly /></label>
        <label>휴대전화<input type="tel" value={profile.phoneNumber || ''} onChange={(event) => setProfile({ ...profile, phoneNumber: event.target.value })} /></label>
        <label>이메일<input type="email" value={profile.email || ''} onChange={(event) => setProfile({ ...profile, email: event.target.value })} /></label>
        <label>생년월일<input type="date" value={profile.birthDate || ''} disabled /></label>
        <div className="my-ticket-management-form-actions"><button type="submit" disabled={saving}>{saving ? '저장 중…' : '저장'}</button></div>
      </form>
      <div className="my-ticket-withdraw-action"><button type="button" onClick={() => setWithdrawOpen(true)}>회원 탈퇴</button></div>
      </> : <>
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
