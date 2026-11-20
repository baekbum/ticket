import { LOGIN_SESSION_STORAGE_KEY, type AuthenticationStorage, type TokenRefreshState } from './authenticatedRequest.ts';

const loginStorageKeys = [LOGIN_SESSION_STORAGE_KEY, 'ticksy.accessToken', 'ticksy.refreshToken', 'ticksy.userName'];

type AuthenticationWindow = Window & { ticksyTokenRefreshState?: TokenRefreshState };

export function startLoginSession(browser: Window) {
  const sessionId = browser.crypto.getRandomValues(new Uint32Array(4)).join('-');
  browser.sessionStorage.setItem(LOGIN_SESSION_STORAGE_KEY, sessionId);
}

export function bindBookingLogin(browser: Window, popup: Window) {
  if (!browser.sessionStorage.getItem(LOGIN_SESSION_STORAGE_KEY)) startLoginSession(browser);

  for (const key of loginStorageKeys) {
    const value = browser.sessionStorage.getItem(key);
    if (value === null) popup.sessionStorage.removeItem(key);
    else popup.sessionStorage.setItem(key, value);
  }
}

function refreshState(browser: AuthenticationWindow) {
  return browser.ticksyTokenRefreshState ??= {};
}

export function createWindowAuthentication(browser: Window, isBookingPage: () => boolean) {
  const windowStorage = browser.sessionStorage;
  const loginSession = isBookingPage() ? windowStorage.getItem(LOGIN_SESSION_STORAGE_KEY) : null;
  const opener: Window | null = loginSession ? browser.opener : null;

  function bookingOpener() {
    if (!isBookingPage() || !opener || opener.closed) return null;

    try {
      if (opener.location.origin === browser.location.origin
          && opener.sessionStorage.getItem(LOGIN_SESSION_STORAGE_KEY) === loginSession) return opener;
    } catch { /* The opener may have navigated to another origin. */ }
    return null;
  }

  const storage: AuthenticationStorage = {
    getItem(key) {
      const owner = bookingOpener();
      if (!owner) return isBookingPage() && opener && !opener.closed ? null : windowStorage.getItem(key);

      const value = owner.sessionStorage.getItem(key);
      if (value === null) windowStorage.removeItem(key);
      else windowStorage.setItem(key, value);
      return value;
    },
    setItem(key, value) {
      bookingOpener()?.sessionStorage.setItem(key, value);
      windowStorage.setItem(key, value);
    },
    removeItem(key) {
      bookingOpener()?.sessionStorage.removeItem(key);
      windowStorage.removeItem(key);
    },
  };

  return { storage, bookingOpener, refreshState: () => refreshState(bookingOpener() ?? browser) };
}
