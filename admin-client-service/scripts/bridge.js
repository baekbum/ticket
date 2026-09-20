// 기존 관리 화면을 서비스별 API 경로로 연결하는 전환용 어댑터입니다.
window.API = { VERSION: 'v1', LOCAL_PORT: '-1', SERVICE_PORT: '-1', DEV_PORT: '-1' };
window.base = () => '';
window.getAuthHeader = () => {
  const token = localStorage.getItem('admin.accessToken');
  return token ? `Bearer ${token}` : '';
};
window.switchMenu = (menuName, _button, context) => {
  window.parent.postMessage({ type: 'admin:switch-menu', menu: menuName, context }, window.location.origin);
};
window.switchMenuWithContext = (menuName, context) => window.switchMenu(menuName, null, context);
window.openDashboardEmbedWindow = (menuName) => {
  window.parent.postMessage({ type: 'admin:open-embed', menu: menuName }, window.location.origin);
};
window.addEventListener('pointerdown', () => {
  window.parent.postMessage({ type: 'admin:focus-embed' }, window.location.origin);
});
window.addEventListener('DOMContentLoaded', () => {
  document.body.classList.toggle('dark-mode', localStorage.getItem('adminTheme') === 'dark');
});
window.addEventListener('storage', event => {
  if (event.key === 'adminTheme') document.body.classList.toggle('dark-mode', event.newValue === 'dark');
});

function serviceUrl(rawUrl) {
  const url = new URL(String(rawUrl), window.location.origin);
  if (url.origin !== window.location.origin) throw new Error('외부 주소로 관리자 토큰을 보낼 수 없습니다.');
  const path = url.pathname.replace(/^\/admin(?=\/api\/v1\/)/, '');
  const prefix = '/api/v1/';
  if (!path.startsWith(prefix)) return url.pathname + url.search;
  const rest = path.slice(prefix.length);
  let routed;
  if (rest.startsWith('auth/')) routed = `/auth/api/v1/${rest.slice(5)}`;
  else if (rest.startsWith('user/address/')) routed = `/user/api/v1/manage/address/${rest.slice(13)}`;
  else if (rest.startsWith('user/')) routed = `/user/api/v1/manage/${rest.slice(5)}`;
  else if (rest.startsWith('queue/')) routed = `/queue/api/v1/manage/queue/${rest.slice(6)}`;
  else if (rest.startsWith('notice/')) routed = `/support/api/v1/manage/notice/${rest.slice(7)}`;
  else if (rest.startsWith('faq/')) routed = `/support/api/v1/manage/faq/${rest.slice(4)}`;
  else if (rest.startsWith('audit-log/')) routed = `/audit/api/v1/audit-log/${rest.slice(10)}`;
  else if (rest.startsWith('manage/')) routed = `/admin-api/api/v1/${rest}`;
  else {
    const ticketRoots = ['event', 'area', 'seat', 'reservation', 'ticket', 'coupon', 'payment'];
    const root = rest.split('/')[0];
    if (ticketRoots.includes(root)) routed = `/ticket/api/v1/manage/${rest}`;
  }
  if (!routed) throw new Error(`관리 API 경로를 찾지 못했습니다: ${path}`);
  return routed + url.search;
}

let refreshPromise = null;
async function refreshToken() {
  const token = localStorage.getItem('admin.refreshToken');
  if (!token) throw new Error('로그인 정보가 만료되었습니다.');
  const response = await fetch('/auth/api/v1/reissue', {
    method: 'POST', headers: { 'Authorization-Refresh': `Bearer ${token}` },
  });
  if (!response.ok) throw new Error('로그인 정보가 만료되었습니다.');
  const tokens = await response.json();
  localStorage.setItem('admin.accessToken', tokens.accessToken);
  localStorage.setItem('admin.refreshToken', tokens.refreshToken);
  localStorage.setItem('accessToken', tokens.accessToken);
  localStorage.setItem('refreshToken', tokens.refreshToken);
  return tokens.accessToken;
}

window.Fetch = async (rawUrl, options = {}) => {
  const url = serviceUrl(rawUrl);
  const headers = new Headers(options.headers);
  const token = localStorage.getItem('admin.accessToken');
  if (token) headers.set('Authorization', `Bearer ${token}`);
  let body = options.body;
  if (body && typeof body === 'object' && !(body instanceof FormData) && !(body instanceof Blob)) {
    body = JSON.stringify(body);
  }
  if (typeof body === 'string' && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  let response = await fetch(url, { ...options, body, headers });
  if (response.status === 401 && token) {
    try {
      if (localStorage.getItem('admin.accessToken') === token) {
        if (!refreshPromise) refreshPromise = refreshToken().finally(() => { refreshPromise = null; });
        await refreshPromise;
      }
      headers.set('Authorization', `Bearer ${localStorage.getItem('admin.accessToken')}`);
      response = await fetch(url, { ...options, body, headers });
    } catch {
      localStorage.removeItem('admin.accessToken');
      localStorage.removeItem('admin.refreshToken');
      window.top.location.replace('/admin/');
      throw new Error('로그인 정보가 만료되었습니다.');
    }
  }
  return response;
};

window.readErrorResponse = async (response, fallback = '요청 처리 중 오류가 발생했습니다.') => {
  if (!response) return { code: null, message: fallback, details: null };
  try {
    const body = await response.clone().json();
    return { code: body.code || null, message: body.message || fallback, details: body.details || null };
  } catch { return { code: null, message: fallback, details: null }; }
};
window.showResponseError = async (response, fallback) => {
  const error = await window.readErrorResponse(response, fallback);
  window.showToast(error.message, true);
  return error;
};
