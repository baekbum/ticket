const API = {
    VERSION: 'v1',
    LOCAL_PORT: '8999',
    DEV_PORT: '8080'
  };

  const BASE_URL = window.location.port === API.LOCAL_PORT ? `http://localhost:${API.LOCAL_PORT}/admin` : '';
  const PATH = { API_VERSION: `api/${API.VERSION}` };
  const AUTH_URL = `${BASE_URL}/${PATH.API_VERSION}/auth`;
  const VIEW_URL = `${BASE_URL}/${PATH.API_VERSION}/view`;

  function togglePw(inputId, iconId) {
    const inp = document.getElementById(inputId);
    const ic = document.getElementById(iconId);
    if (inp.type === 'password') {
      inp.type = 'text';
      ic.className = 'ti ti-eye-off input-icon';
    } else {
      inp.type = 'password';
      ic.className = 'ti ti-eye input-icon';
    }
  }

  function showToast(msg, err) {
    const t = document.getElementById('toast');
    t.textContent = msg;
    t.style.background = err ? '#A32D2D' : '#3C3489';
    t.classList.add('show');
    setTimeout(() => t.classList.remove('show'), 2800);
  }

  function handleLoginKeydown(e) {
    if (e.key === 'Enter') {
      e.preventDefault();
      login();
    }
  }

  async function login() {
    const userId = document.getElementById('l-id').value.trim();
    const password = document.getElementById('l-pw').value;

    if (!userId || !password) {
      showToast('아이디와 비밀번호를 모두 입력해주세요.', true);
      return;
    }

    try {
      const res = await fetch(`${AUTH_URL}/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId, password })
      });

      if (res.ok) {
        const d = await res.json();
        localStorage.setItem('accessToken', d.accessToken);
        localStorage.setItem('refreshToken', d.refreshToken);
        document.cookie = `accessToken=${d.accessToken}; path=/; max-age=3600; SameSite=Strict;`;
        showToast(`${userId}님, 환영합니다!`);
        setTimeout(() => { window.location.href = `${VIEW_URL}/home`; }, 1000);
      } else {
        const e = await res.json().catch(() => ({}));
        showToast(e.message || '아이디 또는 비밀번호가 일치하지 않습니다.', true);
      }
    } catch (e) {
      showToast('서버와 통신 중 오류가 발생했습니다.', true);
    }
  }