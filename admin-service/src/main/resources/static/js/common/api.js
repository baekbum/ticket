
  const API = {
    VERSION: 'v1',
    SERVICE_PORT: '8999',
    DEV_PORT: '8080'
  };

  let loggedInUserRawData = null;
  let isPasswordVerified = false;
  let reissuePromise = null;

  function base() {
    let port = API.SERVICE_PORT;
    return window.location.port === API.DEV_PORT ? `http://localhost:${port}` : '';
  }

  function getAuthHeader() {
    const token = localStorage.getItem('accessToken');
    return token ? `Bearer ${token}` : '';
  }

  function updateAccessTokenCookie(accessToken) {
    document.cookie = `accessToken=${accessToken}; path=/; max-age=3600; SameSite=Strict;`;
  }

  function clearAuthAndMoveToLogin() {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    document.cookie = "accessToken=; path=/; max-age=0;";
    location.href = `${base()}/admin/api/${API.VERSION}/view/login`;
  }

  async function reissueToken() {
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) {
      throw new Error('Refresh token is missing');
    }

    if (!reissuePromise) {
      reissuePromise = fetch(`${base()}/admin/api/${API.VERSION}/auth/reissue`, {
        method: 'POST',
        headers: { 'Authorization-Refresh': `Bearer ${refreshToken}` }
      })
        .then(async res => {
          if (!res.ok) {
            throw new Error('Token reissue failed');
          }

          const tokens = await res.json();
          localStorage.setItem('accessToken', tokens.accessToken);
          localStorage.setItem('refreshToken', tokens.refreshToken);
          updateAccessTokenCookie(tokens.accessToken);
          return tokens.accessToken;
        })
        .finally(() => {
          reissuePromise = null;
        });
    }

    return reissuePromise;
  }

  function shouldSkipAccessToken(url) {
    const requestUrl = String(url || '');
    return requestUrl.includes('/api/v1/view/home')
      || requestUrl.includes('/api/v1/view/fragment/')
      || requestUrl.includes('/api/v1/auth/login')
      || requestUrl.includes('/api/v1/auth/reissue');
  }

  function normalizeOptions(url, options = {}) {
    const normalized = { ...options };
    const headers = { ...(normalized.headers || {}) };
    const token = localStorage.getItem('accessToken');

    if (normalized.body && typeof normalized.body === 'object' && !(normalized.body instanceof FormData)) {
      normalized.body = JSON.stringify(normalized.body);
      if (!headers['Content-Type']) {
        headers['Content-Type'] = 'application/json';
      }
    }

    if (token && !shouldSkipAccessToken(url)) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    normalized.headers = headers;
    return normalized;
  }

  window.readErrorResponse = async function(res, fallbackMessage = '요청 처리 중 오류가 발생했습니다.') {
    if (!res) {
      return { code: null, message: fallbackMessage, details: null };
    }

    try {
      const data = await res.clone().json();
      if (data && (data.message || data.code || data.details)) {
        return {
          code: data.code || null,
          message: data.message || fallbackMessage,
          details: data.details ?? null
        };
      }
    } catch (e) {
      try {
        const text = await res.clone().text();
        if (text) {
          return { code: null, message: text, details: null };
        }
      } catch (ignored) {
      }
    }

    return { code: null, message: fallbackMessage, details: null };
  };

  window.showResponseError = async function(res, fallbackMessage = '요청 처리 중 오류가 발생했습니다.') {
    const error = await window.readErrorResponse(res, fallbackMessage);
    showToast(error.message || fallbackMessage, true);
    return error;
  };

  window.Fetch = async function(url, options = {}) {
    const requestOptions = normalizeOptions(url, options);
    let res = await fetch(url, requestOptions);

    if (res.status === 401 && !shouldSkipAccessToken(url)) {
      try {
        const newAccessToken = await reissueToken();
        const retryOptions = {
          ...requestOptions,
          headers: {
            ...(requestOptions.headers || {}),
            'Authorization': `Bearer ${newAccessToken}`
          }
        };
        res = await fetch(url, retryOptions);
      } catch (e) {
        showToast('인증 세션이 만료되었습니다. 다시 로그인해주세요.', true);
        setTimeout(() => clearAuthAndMoveToLogin(), 1500);
        throw new Error("Unauthorized");
      }
    }

    return res;
  };
