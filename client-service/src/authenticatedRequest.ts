import { getApiErrorMessage } from './apiErrorMessage.ts';

export class ApiRequestError extends Error {
  status: number;
  code?: string;
  constructor(status: number, message: string, code?: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export class SessionExpiredError extends Error {
  constructor() {
    super('로그인 정보를 갱신할 수 없습니다. 다시 로그인해주세요.');
  }
}

export class ActiveTokenExpiredError extends Error {
  constructor() {
    super('좌석 선택 시간이 초과되었습니다. 예매를 다시 시작해주세요.');
  }
}

type Tokens = { accessToken: string; refreshToken: string };
type Dependencies = { storage: Storage; fetch: typeof fetch };

export function createAuthenticatedRequest(deps: Dependencies) {
  let refreshPromise: Promise<void> | null = null;

  function expireLogin(): never {
    // 예매 active-token은 로그인 토큰과 수명이 다르므로 유지한다.
    deps.storage.removeItem('ticksy.accessToken');
    deps.storage.removeItem('ticksy.refreshToken');
    throw new SessionExpiredError();
  }

  async function fetchResponse(url: string, options: RequestInit) {
    try {
      return await deps.fetch(url, options);
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') throw error;
      throw new ApiRequestError(0, '서버에 연결하지 못했습니다. 연결 상태를 확인한 후 다시 시도해주세요.');
    }
  }

  async function responseError(response: Response): Promise<never> {
    const text = await response.text();
    let code: string | undefined;
    try {
      const body: unknown = JSON.parse(text);
      if (body && typeof body === 'object' && 'code' in body && typeof body.code === 'string') code = body.code;
    } catch { /* 일반 텍스트 응답 */ }
    if (code === 'ACTIVE_TOKEN_EXPIRED') throw new ActiveTokenExpiredError();
    throw new ApiRequestError(response.status, getApiErrorMessage(response.status, text), code);
  }

  async function refresh() {
    const refreshToken = deps.storage.getItem('ticksy.refreshToken');
    if (!refreshToken) expireLogin();
    const response = await fetchResponse('/auth/api/v1/reissue', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization-Refresh': `Bearer ${refreshToken}` },
    });
    if (response.status === 401 || response.status === 403) expireLogin();
    if (!response.ok) return responseError(response);
    const tokens = await response.json() as Tokens;
    if (!tokens.accessToken || !tokens.refreshToken) {
      throw new ApiRequestError(502, '로그인 정보를 갱신하지 못했습니다. 잠시 후 다시 시도해주세요.');
    }
    deps.storage.setItem('ticksy.accessToken', tokens.accessToken);
    deps.storage.setItem('ticksy.refreshToken', tokens.refreshToken);
  }

  return async function request<T = unknown>(url: string, options: RequestInit): Promise<T> {
    const publicPaths = [
      '/auth/api/v1/login', '/auth/api/v1/reissue', '/auth/api/v1/logout',
      '/user/api/v1/signup', '/user/api/v1/reset/password',
    ];
    const publicPrefixes = [
      '/user/api/v1/find/', '/user/api/v1/check/duplication/',
      '/ticket/api/v1/event/', '/support/api/v1/',
    ];
    const useAuth = !publicPaths.includes(url) && !publicPrefixes.some((path) => url.startsWith(path));
    const sentToken = deps.storage.getItem('ticksy.accessToken');
    const send = (token: string | null) => {
      const headers = new Headers(options.headers);
      headers.set('Content-Type', headers.get('Content-Type') || 'application/json');
      if (useAuth && token) headers.set('Authorization', `Bearer ${token}`);
      return fetchResponse(url, { ...options, headers });
    };
    let response = await send(sentToken);
    if (response.status === 401 && useAuth && sentToken) {
      // 동시에 발생한 401은 refresh를 공유하고, 늦게 도착한 401은 이미 갱신된 토큰을 사용한다.
      if (deps.storage.getItem('ticksy.accessToken') === sentToken) {
        if (!refreshPromise) refreshPromise = refresh().finally(() => { refreshPromise = null; });
        await refreshPromise;
      }
      response = await send(deps.storage.getItem('ticksy.accessToken'));
      if (response.status === 401) expireLogin();
    }
    if (!response.ok) return responseError(response);
    if (response.status === 204) return undefined as T;
    const text = await response.text();
    return text ? JSON.parse(text) as T : undefined as T;
  };
}
