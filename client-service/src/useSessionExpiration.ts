import { useLayoutEffect } from 'react';
import { SESSION_EXPIRED_EVENT } from './authenticatedRequest.ts';

/** 하위 화면의 첫 요청보다 먼저 구독해서 인증 만료에 따른 화면 전환을 놓치지 않는다. */
export function useSessionExpiration(onExpired: (message: string) => void) {
  useLayoutEffect(() => {
    const handleExpiration = (event: Event) => {
      const message = (event as CustomEvent<unknown>).detail;
      if (typeof message === 'string') onExpired(message);
    };

    window.addEventListener(SESSION_EXPIRED_EVENT, handleExpiration);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, handleExpiration);
  }, [onExpired]);
}
