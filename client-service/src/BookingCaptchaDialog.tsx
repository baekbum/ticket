import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ActiveTokenExpiredError, ApiRequestError, SessionExpiredError } from './authenticatedRequest';
import './BookingCaptchaDialog.css';

export type BookingCaptchaChallenge = {
  challengeId: string;
  imageBase64: string;
  expiresAt: string;
};

type Props = {
  initialChallenge: BookingCaptchaChallenge;
  eventId: number;
  activeToken: string;
  request: <T>(url: string, options: RequestInit) => Promise<T>;
  onPassed: () => void;
  onCancel: () => void;
  onSessionExpired: (error: SessionExpiredError | ActiveTokenExpiredError) => void;
};

function remainingSeconds(expiresAt: string) {
  return Math.max(0, Math.ceil((Date.parse(expiresAt) - Date.now()) / 1000));
}

export default function BookingCaptchaDialog({ initialChallenge, eventId, activeToken, request, onPassed, onCancel, onSessionExpired }: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const answerRef = useRef<HTMLInputElement>(null);
  const pendingRequest = useRef<AbortController | null>(null);
  const [challenge, setChallenge] = useState(initialChallenge);
  const [seconds, setSeconds] = useState(() => remainingSeconds(initialChallenge.expiresAt));
  const [answer, setAnswer] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [needsCurrentChallenge, setNeedsCurrentChallenge] = useState(false);
  const [attemptLimitReached, setAttemptLimitReached] = useState(false);

  useEffect(() => {
    const dialog = dialogRef.current;
    dialog?.showModal();
    return () => {
      pendingRequest.current?.abort();
      dialog?.close();
    };
  }, []);

  useEffect(() => {
    const timer = window.setInterval(() => setSeconds(remainingSeconds(challenge.expiresAt)), 1000);
    return () => window.clearInterval(timer);
  }, [challenge.expiresAt]);

  async function perform(action: 'verify' | 'refresh') {
    if (pendingRequest.current || attemptLimitReached) return;
    const controller = new AbortController();
    pendingRequest.current = controller;
    setBusy(true);
    setErrorMessage('');
    const endpoint = `/ticket/api/v1/captcha/events/${eventId}/challenge`;
    const options: RequestInit = { method: 'POST', signal: controller.signal, headers: { 'X-Active-Token': activeToken } };
    try {
      if (action === 'verify') {
        const result = await request<{ passed: boolean }>(`${endpoint}/verify`, {
          ...options,
          body: JSON.stringify({ challengeId: challenge.challengeId, answer }),
        });
        if (controller.signal.aborted) return;
        if (result.passed) {
          onPassed();
          return;
        }
        setErrorMessage('보안문자가 일치하지 않습니다. 다시 입력해 주세요.');
      } else {
        let next: BookingCaptchaChallenge | undefined;
        if (needsCurrentChallenge || seconds === 0) {
          next = await request<BookingCaptchaChallenge | undefined>(endpoint, options);
        } else {
          try {
            next = await request<BookingCaptchaChallenge | undefined>(`${endpoint}/refresh`, options);
          } catch (error) {
            if (!(error instanceof ApiRequestError) || error.code !== 'CAPTCHA_CHALLENGE_EXPIRED') throw error;
            next = await request<BookingCaptchaChallenge | undefined>(endpoint, options);
          }
        }
        if (controller.signal.aborted) return;
        if (!next) {
          onPassed();
          return;
        }
        setChallenge(next);
        setSeconds(remainingSeconds(next.expiresAt));
        setNeedsCurrentChallenge(false);
      }
      setAnswer('');
      answerRef.current?.focus();
    } catch (error) {
      if (controller.signal.aborted) return;
      if (error instanceof SessionExpiredError || error instanceof ActiveTokenExpiredError) {
        onSessionExpired(error);
        return;
      }
      if (error instanceof ApiRequestError) {
        if (error.code === 'CAPTCHA_CHALLENGE_EXPIRED' || error.code === 'CAPTCHA_CHALLENGE_CHANGED') {
          setNeedsCurrentChallenge(true);
        }
        if (error.code === 'CAPTCHA_ATTEMPT_LIMIT_REACHED') setAttemptLimitReached(true);
      }
      setErrorMessage(error instanceof ApiRequestError ? error.message : '보안문자를 확인할 수 없습니다. 다시 시도해 주세요.');
    } finally {
      pendingRequest.current = null;
      if (!controller.signal.aborted) setBusy(false);
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (answer.length === 6 && seconds > 0 && !needsCurrentChallenge) void perform('verify');
  }

  const time = `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;

  return (
    <dialog ref={dialogRef} className="booking-captcha-dialog" aria-labelledby="booking-captcha-title" aria-describedby="booking-captcha-description" onCancel={(event) => event.preventDefault()}>
      <header>
        <h2 id="booking-captcha-title">보안문자 입력</h2>
        <p id="booking-captcha-description">이미지에 보이는 문자를 입력하면 좌석 배치도가 열립니다.</p>
      </header>
      <form className="booking-captcha-form" onSubmit={submit}>
        <div className="booking-captcha-sample">
          <img src={`data:image/png;base64,${challenge.imageBase64}`} width="210" height="72" alt="예매 보안문자 이미지" />
          <div className="booking-captcha-sample-actions">
            <button className="booking-captcha-secondary" type="button" disabled={busy || attemptLimitReached} onClick={() => { void perform('refresh'); }}>↻ 새 이미지</button>
            <span>유효 시간 <time dateTime={`PT${seconds}S`}>{time}</time></span>
          </div>
        </div>
        <label htmlFor="booking-captcha-answer">보안문자
          <input ref={answerRef} id="booking-captcha-answer" type="text" placeholder="문자 6자리 입력" value={answer}
            onChange={(event) => setAnswer(event.target.value.toUpperCase().slice(0, 6))} maxLength={6}
            autoComplete="off" autoCapitalize="characters" spellCheck={false} autoFocus required disabled={attemptLimitReached} />
        </label>
        {(errorMessage || seconds === 0) && <p className="booking-captcha-error" role="alert">{errorMessage || '보안문자가 만료됐습니다. 새 이미지를 받아 주세요.'}</p>}
        <footer>
          <button className="booking-captcha-secondary" type="button" onClick={onCancel}>예매 취소</button>
          <button className="booking-next-button" type="submit" disabled={busy || attemptLimitReached || needsCurrentChallenge || seconds === 0 || answer.length !== 6}>확인</button>
        </footer>
      </form>
    </dialog>
  );
}
