import { useEffect, useReducer, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiRequestError, SessionExpiredError } from './authenticatedRequest';
import type { createAuthenticatedRequest } from './authenticatedRequest';
import { PRIMARY_CARD_CODES, PRIMARY_CARD_COMPANIES, OTHER_CARD_COMPANIES, INVALID_CARD_INFORMATION_MESSAGE, cardResult, emptyCardPaymentForm, cardPaymentFormReducer, isCardStepValid, buildCardApprovalRequest, shouldCheckCardApprovalStatus } from './cardPayment';
import type { CardApprovalResponse, CardCompany, CardPaymentStep } from './cardPayment';
import './CardPaymentDialog.css';

type Props = {
  payment: { paymentNo: string; amount: number; expiresAt: string | null; status: string };
  request: ReturnType<typeof createAuthenticatedRequest>;
  onComplete: (result: CardApprovalResponse) => void;
  onClose: () => void;
};

export default function CardPaymentDialog({ payment, request, onComplete, onClose }: Props) {
  const dialog = useRef<HTMLDialogElement>(null);
  const submitting = useRef(false);
  const approvalError = useRef('');
  const attemptKey = `ticksy.card-attempt:${payment.paymentNo}`;
  const [phase, setPhase] = useState<'INPUT' | 'SUBMITTING' | 'PENDING' | 'TERMINAL'>(() =>
    sessionStorage.getItem(attemptKey) || payment.status === 'PAID' ? 'PENDING' : 'INPUT');
  const [form, dispatch] = useReducer(cardPaymentFormReducer, undefined, emptyCardPaymentForm);
  const [step, setStep] = useState<CardPaymentStep>(1);
  const [showPassword, setShowPassword] = useState(false);
  const numberInputs = useRef<Array<HTMLInputElement | null>>([]);
  const cvcInput = useRef<HTMLInputElement>(null);
  const stepHeading = useRef<HTMLHeadingElement>(null);
  const [error, setError] = useState('');
  const [pollCount, setPollCount] = useState(0);
  const busy = phase === 'SUBMITTING' || phase === 'PENDING';
  const completeRef = useRef(onComplete);
  useEffect(() => { completeRef.current = onComplete; }, [onComplete]);

  useEffect(() => {
    dialog.current?.showModal();
    return () => dialog.current?.close();
  }, []);

  // 응답을 잃어도 같은 결제번호의 결과만 조회한다. 카드 정보는 저장하지 않는다.
  useEffect(() => {
    if (phase !== 'PENDING') return;
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 8000);
    let next: number | undefined;
    let disposed = false;
    async function check() {
      try {
        const result = await request<CardApprovalResponse>(
          `/payment-gateway/api/v1/payments/card/${encodeURIComponent(payment.paymentNo)}`,
          { method: 'GET', signal: controller.signal });
        if (disposed) return;
        if (result.paymentNo !== payment.paymentNo) throw new Error('결제 번호를 확인하지 못했습니다.');
        const outcome = cardResult(result);
        if (outcome === 'COMPLETE') {
          sessionStorage.removeItem(attemptKey);
          dispatch({ type: 'reset' });
          setStep(1);
          setShowPassword(false);
          completeRef.current(result);
          return;
        }
        if (outcome === 'TERMINAL') {
          setPhase('TERMINAL');
          setError(result.status?.includes('REFUNDED') ? '이미 환불 처리된 결제입니다. 나의 예매 내역을 확인해주세요.'
            : '결제가 취소되었거나 승인되지 않았습니다. 예매를 다시 시작해주세요.');
          return;
        }
        setError('');
      } catch (failure) {
        if (disposed) return;
        if (failure instanceof ApiRequestError && failure.status === 400) {
          // 승인 이력이 없으면 결제번호를 유지한 채 입력을 다시 받는다.
          sessionStorage.removeItem(attemptKey);
          setPhase('INPUT');
          setError(approvalError.current || '승인 이력이 없습니다. 카드 정보를 확인한 후 다시 결제해주세요.');
          return;
        }
        setError(failure instanceof SessionExpiredError ? failure.message
          : '결제 결과를 확인하지 못했습니다. 연결이 복구되면 자동으로 다시 확인합니다.');
      } finally {
        window.clearTimeout(timeout);
      }
      if (!disposed) next = window.setTimeout(() => setPollCount((count) => count + 1), 3000);
    }
    void check();
    return () => { disposed = true; controller.abort(); window.clearTimeout(timeout); window.clearTimeout(next); };
  }, [phase, pollCount, payment.paymentNo, request, attemptKey]);

  useEffect(() => {
    if (phase === 'INPUT') stepHeading.current?.focus();
  }, [step, phase]);

  function resetInputs() {
    dispatch({ type: 'reset' });
    setStep(1);
    setShowPassword(false);
    setError('');
    approvalError.current = '';
  }

  function focusNumber(index: number) {
    const input = numberInputs.current[index];
    input?.focus();
    input?.setSelectionRange(input.value.length, input.value.length);
  }

  function updateNumber(index: number, value: string) {
    dispatch({ type: 'number', index, value });
    if (value.replace(/\D/g, '').length >= 4) {
      if (index < 3) focusNumber(index + 1);
      else cvcInput.current?.focus();
    }
  }

  function pasteNumber(index: number, value: string) {
    const digits = value.replace(/\D/g, '');
    dispatch({ type: 'pasteNumber', index, value });
    if (!digits) return;
    const start = digits.length >= 16 ? 0 : index;
    const nextIndex = start + Math.floor(Math.min(digits.length, (4 - start) * 4) / 4);
    if (nextIndex > 3) cvcInput.current?.focus();
    else focusNumber(nextIndex);
  }

  async function submitStep(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current || busy || !isCardStepValid(step, form)) return;
    if (step < 3) {
      setStep((step + 1) as CardPaymentStep);
      setError('');
      return;
    }
    submitting.current = true;
    setPhase('SUBMITTING');
    setShowPassword(false);
    setError('');
    approvalError.current = '';
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 15000);
    let approvalRequested = false;
    try {
      const body = JSON.stringify(buildCardApprovalRequest(form, payment));
      sessionStorage.setItem(attemptKey, 'PENDING');
      approvalRequested = true;
      const result = await request<CardApprovalResponse>('/payment-gateway/api/v1/payments/card/approve',
        { method: 'POST', body, signal: controller.signal });
      if (result.paymentNo === payment.paymentNo && cardResult(result) === 'COMPLETE') {
        sessionStorage.removeItem(attemptKey);
        resetInputs();
        onComplete(result);
      } else {
        setPhase('PENDING');
      }
    } catch (failure) {
      if (failure instanceof ApiRequestError && failure.status === 400) {
        // 잘못된 카드 정보는 승인 전에 거부되므로 조회할 승인 이력이 없다.
        sessionStorage.removeItem(attemptKey);
        approvalError.current = INVALID_CARD_INFORMATION_MESSAGE;
        setError(INVALID_CARD_INFORMATION_MESSAGE);
        setPhase('INPUT');
      } else {
        const message = failure instanceof ApiRequestError || failure instanceof SessionExpiredError
          ? failure.message : '결제 요청을 확인하지 못했습니다. 잠시 후 다시 시도해주세요.';
        approvalError.current = message;
        setError(message);
        // 승인 요청 후 응답을 확인하지 못한 경우에만 기존 승인 이력을 조회한다.
        setPhase(approvalRequested && shouldCheckCardApprovalStatus(failure) ? 'PENDING' : 'INPUT');
      }
    } finally {
      window.clearTimeout(timeout);
      submitting.current = false;
    }
  }

  function close() {
    if (busy) return;
    resetInputs();
    sessionStorage.removeItem(attemptKey);
    onClose();
  }
  return <dialog className="card-payment-dialog" ref={dialog} aria-labelledby="card-payment-title"
      onCancel={(event) => { event.preventDefault(); close(); }}>
      <header className="card-payment-heading">
        <div><span className="card-payment-eyebrow">Tickey · 카드 결제</span><h2 id="card-payment-title">신용카드 결제</h2></div>
        <button type="button" className="card-payment-close" aria-label="카드 결제창 닫기" disabled={busy} onClick={close}>×</button>
      </header>
      <div className="card-payment-total"><span>최종 결제 금액</span><strong>{payment.amount.toLocaleString()}<small>원</small></strong></div>
      {phase === 'INPUT' ? <form onSubmit={submitStep}>
        <ol className="card-payment-steps" aria-label="카드 결제 진행 단계">
          {['카드사 선택', '카드 정보', '비밀번호'].map((label, index) => <li key={label}
            className={step === index + 1 ? 'active' : step > index + 1 ? 'completed' : ''}
            aria-current={step === index + 1 ? 'step' : undefined}>
            <span aria-hidden="true">{index + 1}</span>{label}
          </li>)}
        </ol>
        <h3 className="card-payment-step-title" ref={stepHeading} tabIndex={-1}>
          {step === 1 ? '결제할 카드사를 선택해주세요' : step === 2 ? '카드 번호와 CVC를 입력해주세요' : '온라인 결제 비밀번호를 입력해주세요'}
        </h3>
        <div className="card-payment-fields">
          {step === 1 && <>
            <div className="card-company-grid" role="group" aria-label="주요 카드사 선택">
              {PRIMARY_CARD_COMPANIES.map((card) => <button key={card.code} type="button"
                className={`card-company-option${form.company === card.code ? ' selected' : ''}`}
                aria-pressed={form.company === card.code} onClick={() => dispatch({ type: 'company', value: card.code })}>
                <span className="card-company-check" aria-hidden="true">{form.company === card.code ? '✓' : ''}</span>
                {card.name}
              </button>)}
            </div>
            <label>기타 카드사<select value={PRIMARY_CARD_CODES.includes(form.company as CardCompany) ? '' : form.company}
              onChange={(event) => dispatch({ type: 'company', value: event.target.value as CardCompany | '' })}>
              <option value="">기타 카드사 선택</option>
              {OTHER_CARD_COMPANIES.map((card) => <option key={card.code} value={card.code}>{card.name}</option>)}
            </select></label>
          </>}
          {step === 2 && <>
            <fieldset className="card-number-fieldset">
              <legend>카드 번호</legend>
              <div className="card-number-segments">
                {form.cardNumber.map((part, index) => <input key={index}
                  ref={(input) => { numberInputs.current[index] = input; }}
                  aria-label={`카드 번호 ${index + 1}번째 4자리`} aria-describedby="card-number-help"
                  required type={index === 1 || index === 2 ? 'password' : 'text'} inputMode="numeric" autoComplete="off"
                  value={part} pattern="[0-9]{4}" maxLength={4} placeholder={index === 1 || index === 2 ? '••••' : '0000'}
                  onChange={(event) => updateNumber(index, event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === 'Backspace' && !part && index > 0) { event.preventDefault(); focusNumber(index - 1); }
                  }}
                  onPaste={(event) => { event.preventDefault(); pasteNumber(index, event.clipboardData.getData('text')); }} />)}
              </div>
              <p id="card-number-help" className="card-payment-note">카드 번호 16자리를 입력해주세요. 전체 번호를 붙여넣을 수 있습니다.</p>
            </fieldset>
            <label>보안 코드 (CVC)<input ref={cvcInput} required type="password" inputMode="numeric" autoComplete="off"
              value={form.cvc} pattern="[0-9]{3}" maxLength={3} placeholder="카드 뒷면의 숫자 3자리"
              onChange={(event) => dispatch({ type: 'cvc', value: event.target.value })} /></label>
          </>}
          {step === 3 && <>
            <div className="card-online-password-field">
              <label htmlFor="card-online-password">온라인 결제 비밀번호</label>
              <div className="card-online-password-input">
                <input id="card-online-password" required type={showPassword ? 'text' : 'password'} autoComplete="off"
                  value={form.password} maxLength={64} aria-describedby="card-password-help" placeholder="온라인 결제용 비밀번호"
                  onChange={(event) => dispatch({ type: 'password', value: event.target.value })} />
                <button type="button" className="card-password-toggle" aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 표시하기'}
                  aria-pressed={showPassword} onClick={() => setShowPassword((visible) => !visible)}>
                  {showPassword ? '숨김' : '표시'}
                </button>
              </div>
            </div>
            <p id="card-password-help" className="card-payment-note">카드에 등록한 온라인 결제 비밀번호를 입력해주세요. 최대 64자까지 입력할 수 있습니다.</p>
          </>}
        </div>
        {payment.expiresAt && <p className="card-payment-note">결제 기한: {payment.expiresAt.replace('T', ' ')} (한국시간)</p>}
        {error && <p className="card-payment-error" role="alert">{error}</p>}
        <div className="card-payment-navigation">
          {step > 1 && <button className="card-payment-back" type="button" onClick={() => {
            setStep((step - 1) as CardPaymentStep); setShowPassword(false); setError('');
          }}>이전</button>}
          <button className="card-payment-submit" type="submit" disabled={busy || !isCardStepValid(step, form)}>
            {step === 3 ? `${payment.amount.toLocaleString()}원 결제하기` : '다음'}
          </button>
        </div>
        <p className="card-payment-note">입력한 카드 정보는 결제 요청에만 사용됩니다.</p>
      </form> : <div className="card-payment-progress" role="status" aria-live="polite">
        {busy && <span className="card-payment-spinner" aria-hidden="true" />}
        <h3>{phase === 'TERMINAL' ? '결제를 완료할 수 없습니다' : phase === 'SUBMITTING' ? '카드 승인 중입니다' : '결제 결과를 확인하고 있습니다'}</h3>
        <p>{error || (phase === 'TERMINAL' ? '이미 취소되거나 환불된 결제입니다. 예매 내역을 확인해주세요.' : '잠시만 기다려주세요. 확인이 끝나면 예매 완료 화면으로 이동합니다.')}</p>
        {busy && <p className="card-payment-note">중복 결제를 방지하기 위해 새 결제를 시작하지 마세요.</p>}
        {busy && <button className="card-payment-submit" type="button" disabled>{phase === 'SUBMITTING' ? '결제 요청 중…' : '결제 결과 확인 중…'}</button>}
        {phase === 'TERMINAL' && <button className="card-payment-submit" type="button" onClick={close}>창 닫기</button>}
      </div>}
    </dialog>;
}
