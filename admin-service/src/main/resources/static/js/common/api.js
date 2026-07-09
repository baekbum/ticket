
  const API = {
    VERSION: 'v1',
    SERVICE_PORT: '8999',
    DEV_PORT: '8080'
  };

  let loggedInUserRawData = null;
  let isPasswordVerified = false;

  function base() {
    let port = API.SERVICE_PORT;
    return window.location.port === API.DEV_PORT ? `http://localhost:${port}` : '';
  }

  function getAuthHeader() {
    const token = localStorage.getItem('accessToken');
    return token ? `Bearer ${token}` : '';
  }

  window.Fetch = async function(url, options = {}) {
    const token = localStorage.getItem('accessToken');
    const defaultHeaders = {};
    const requestUrl = String(url || '');
    const isPublicViewRequest = requestUrl.includes('/api/v1/view/home')
      || requestUrl.includes('/api/v1/view/fragment/');

    if (token && !isPublicViewRequest) defaultHeaders['Authorization'] = `Bearer ${token}`;

    if (options.body && typeof options.body === 'object' && !(options.body instanceof FormData)) {
      options.body = JSON.stringify(options.body);
      if (!options.headers || !options.headers['Content-Type']) {
        defaultHeaders['Content-Type'] = 'application/json';
      }
    }

    options.headers = { ...defaultHeaders, ...options.headers };
    const res = await fetch(url, options);

    if (res.status === 401) {
      showToast('인증 세션이 만료되었습니다. 다시 로그인해주세요.', true);
      setTimeout(() => logout(), 1500);
      throw new Error("Unauthorized");
    }

    return res;
  };

  
