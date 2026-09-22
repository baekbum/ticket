import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import type { createAuthenticatedRequest } from './authenticatedRequest';
import { ApiRequestError } from './authenticatedRequest';

type Request = ReturnType<typeof createAuthenticatedRequest>;
type Step = 'verify' | 'change' | 'done';

function errorMessage(error: unknown) {
  if (error instanceof ApiRequestError && error.code === 'LOGIN_FAILED') {
    return '비밀번호가 일치하지 않습니다.';
  }
  return error instanceof Error ? error.message : '요청을 처리하지 못했습니다. 다시 시도해주세요.';
}

export default function ChangePasswordDialog({ request, onClose, onPasswordChanged }: { request: Request; onClose: () => void; onPasswordChanged: () => void }) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [step, setStep] = useState<Step>('verify');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [newPasswordConfirm, setNewPasswordConfirm] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

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

  async function verifyPassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!currentPassword) {
      setError('현재 비밀번호를 입력해 주세요.');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      await request('/user/api/v1/password/validate/me', {
        method: 'POST', body: JSON.stringify({ password: currentPassword }),
      });
      setStep('change');
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setSubmitting(false);
    }
  }

  async function changePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    if (!newPassword) {
      setError('새 비밀번호를 입력해 주세요.');
      return;
    }
    if (newPassword.length < 8) {
      setError('새 비밀번호는 8자 이상 입력해 주세요.');
      return;
    }
    if (newPassword === currentPassword) {
      setError('새로운 비밀번호는 이전 비밀번호와 같을 수 없습니다.');
      return;
    }
    if (!newPasswordConfirm) {
      setError('새 비밀번호 확인을 입력해 주세요.');
      return;
    }
    if (newPassword !== newPasswordConfirm) {
      setError('새 비밀번호 확인이 일치하지 않습니다.');
      return;
    }

    setSubmitting(true);
    try {
      await request('/user/api/v1/password/change/me', {
        method: 'PUT', body: JSON.stringify({ currentPassword, newPassword, newPasswordConfirm }),
      });
      setCurrentPassword('');
      setNewPassword('');
      setNewPasswordConfirm('');
      setStep('done');
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setSubmitting(false);
    }
  }

  return <dialog ref={dialogRef} className="my-ticket-withdraw-dialog my-ticket-password-dialog" aria-labelledby="my-ticket-password-title"
    onCancel={(event) => { event.preventDefault(); if (!submitting) (step === 'done' ? onPasswordChanged : onClose)(); }}>
    <h2 id="my-ticket-password-title">비밀번호 변경</h2>
    {step === 'verify' && <p>먼저 현재 비밀번호를 확인해 주세요.</p>}
    {step === 'change' && <p>새 비밀번호를 입력해 주세요.</p>}
    {error && <p className="my-ticket-management-error" role="alert">{error}</p>}
    {step === 'verify' && <form noValidate onSubmit={verifyPassword}>
      <label>현재 비밀번호<input autoFocus required type="password" autoComplete="current-password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} /></label>
      <div className="my-ticket-management-form-actions"><button type="button" disabled={submitting} onClick={onClose}>취소</button><button type="submit" disabled={submitting}>{submitting ? '확인 중…' : '확인'}</button></div>
    </form>}
    {step === 'change' && <form noValidate onSubmit={changePassword}>
      <label>새로운 비밀번호<input autoFocus required type="password" autoComplete="new-password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} /></label>
      <label>새로운 비밀번호 확인<input required type="password" autoComplete="new-password" value={newPasswordConfirm} onChange={(event) => setNewPasswordConfirm(event.target.value)} /></label>
      <div className="my-ticket-management-form-actions"><button type="button" disabled={submitting} onClick={onClose}>취소</button><button type="submit" disabled={submitting}>{submitting ? '변경 중…' : '비밀번호 변경'}</button></div>
    </form>}
    {step === 'done' && <><p className="my-ticket-password-success" role="status">비밀번호 변경에 성공했습니다. 다시 로그인해 주세요.</p><div className="my-ticket-management-form-actions"><button type="button" onClick={onPasswordChanged}>홈으로 이동</button></div></>}
  </dialog>;
}
