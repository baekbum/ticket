export type AdminUser = { userId: string; name?: string; role: string; email?: string; phoneNumber?: string; birthDate?: string; address?: string };
export type Tokens = { accessToken: string; refreshToken: string };

const accessKey = 'admin.accessToken';
const refreshKey = 'admin.refreshToken';
let refreshPromise: Promise<string> | null = null;

export function getAccessToken() { return localStorage.getItem(accessKey); }
export function clearTokens() {
  localStorage.removeItem(accessKey);
  localStorage.removeItem(refreshKey);
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
}
export function saveTokens(tokens: Tokens) {
  localStorage.setItem(accessKey, tokens.accessToken);
  localStorage.setItem(refreshKey, tokens.refreshToken);
  // 전환 중인 화면 조각의 기존 스크립트도 같은 토큰을 읽습니다.
  localStorage.setItem('accessToken', tokens.accessToken);
  localStorage.setItem('refreshToken', tokens.refreshToken);
}

async function refreshAccessToken() {
  const refreshToken = localStorage.getItem(refreshKey);
  if (!refreshToken) throw new Error('로그인 정보가 만료되었습니다.');
  const response = await fetch('/auth/api/v1/reissue', {
    method: 'POST',
    headers: { 'Authorization-Refresh': `Bearer ${refreshToken}` },
  });
  if (!response.ok) throw new Error('로그인 정보가 만료되었습니다.');
  const tokens = await response.json() as Tokens;
  saveTokens(tokens);
  return tokens.accessToken;
}

export async function adminFetch(url: string, options: RequestInit = {}): Promise<Response> {
  const token = getAccessToken();
  const headers = new Headers(options.headers);
  if (token) headers.set('Authorization', `Bearer ${token}`);
  let response = await fetch(url, { ...options, headers });
  if (response.status === 401 && token) {
    try {
      if (getAccessToken() === token) {
        if (!refreshPromise) refreshPromise = refreshAccessToken().finally(() => { refreshPromise = null; });
        await refreshPromise;
      }
      headers.set('Authorization', `Bearer ${getAccessToken()}`);
      response = await fetch(url, { ...options, headers });
    } catch {
      clearTokens();
      window.location.replace('/admin/');
      throw new Error('로그인 정보가 만료되었습니다.');
    }
  }
  return response;
}

export async function login(userId: string, password: string): Promise<AdminUser> {
  const response = await fetch('/auth/api/v1/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userId, password }),
  });
  if (!response.ok) throw new Error('아이디 또는 비밀번호를 확인해주세요.');
  const tokens = await response.json() as Tokens;
  saveTokens(tokens);
  try {
    return await loadCurrentUser();
  } catch (error) {
    clearTokens();
    throw error;
  }
}

export async function loadCurrentUser(): Promise<AdminUser> {
  const response = await adminFetch('/user/api/v1/manage/select/me');
  if (!response.ok) throw new Error('관리자 정보를 확인하지 못했습니다.');
  const user = await response.json() as AdminUser;
  if (user.role !== 'ROLE_ADMIN') throw new Error('관리자 권한이 없습니다.');
  return user;
}

export async function logout() {
  const refreshToken = localStorage.getItem(refreshKey);
  if (refreshToken) {
    await fetch('/auth/api/v1/logout', {
      method: 'POST',
      headers: { 'Authorization-Refresh': `Bearer ${refreshToken}` },
    }).catch(() => undefined);
  }
  clearTokens();
}
