  let fragmentContext = {};
  const disabledMenus = new Set(['ticket', 'coupon', 'userCoupon']);

  document.addEventListener('DOMContentLoaded', async () => {

      applySavedTheme();

      await loadMyProfileHeader();

      const params = new URLSearchParams(window.location.search);
      const requestedMenu = params.get('menu') || 'user';
      const eventId = params.get('eventId');
      if (eventId) fragmentContext.area = { eventId: parseInt(eventId, 10) };

      const requestedMenuButton = document.querySelector(`.menu-btn[data-menu="${requestedMenu}"]`);
      const defaultMenu = requestedMenuButton && !disabledMenus.has(requestedMenu)
          ? requestedMenuButton
          : document.querySelector('.menu-btn[data-menu="user"]');

      if (defaultMenu) {
          openMenuGroupForButton(defaultMenu);
          await switchMenu(defaultMenu.dataset.menu || 'user', defaultMenu);
      }

  });

  function applySavedTheme() {
    const savedTheme = localStorage.getItem('adminTheme') || 'light';
    document.body.classList.toggle('dark-mode', savedTheme === 'dark');
    syncThemeToggleIcon();
  }

  function syncThemeToggleIcon() {
    const icon = document.getElementById('theme-toggle-icon');
    const button = document.getElementById('theme-toggle-btn');
    const isDarkMode = document.body.classList.contains('dark-mode');

    if (icon) {
      icon.className = isDarkMode ? 'ti ti-sun' : 'ti ti-moon';
    }

    if (button) {
      button.title = isDarkMode ? '일반 모드' : '다크 모드';
      button.setAttribute('aria-label', button.title);
    }
  }

  function toggleTheme() {
    document.body.classList.toggle('dark-mode');
    localStorage.setItem('adminTheme', document.body.classList.contains('dark-mode') ? 'dark' : 'light');
    syncThemeToggleIcon();
  }

  function toggleMenuGroup(toggleButton) {
    const group = toggleButton.closest('.menu-group');
    if (!group) return;

    const isOpen = group.classList.toggle('is-open');
    toggleButton.setAttribute('aria-expanded', String(isOpen));
  }

  function openMenuGroupForButton(btnElement) {
    const group = btnElement?.closest('.menu-group');
    if (!group) return;

    group.classList.add('is-open');
    const toggleButton = group.querySelector('.menu-group-toggle');
    if (toggleButton) {
      toggleButton.setAttribute('aria-expanded', 'true');
    }
  }

  async function loadMyProfileHeader() {
    try {
      const res = await window.Fetch(`${base()}/admin/api/${API.VERSION}/user/select/me`, { method: 'GET' });
      if (res.ok) {
        loggedInUserRawData = await res.json();
        document.getElementById('header-user-name').textContent = loggedInUserRawData.name || '관리자';
      }
    } catch (e) {
      console.error('내 프로필 로드 실패:', e);
    }
  }

  async function switchMenu(menuName, btnElement, context = null) {

    if (disabledMenus.has(menuName)) return;
    if (!btnElement) return;

    if (context) {
      fragmentContext[menuName] = context;
    }

    document.querySelectorAll('.sidebar .menu-btn')
        .forEach(btn => btn.classList.remove('active'));

    btnElement.classList.add('active');
    openMenuGroupForButton(btnElement);

    const contentArea = document.getElementById('content-area');

    try {

      const res = await window.Fetch(
        `${base()}/admin/api/${API.VERSION}/view/fragment/${menuName}`,
        { method: 'GET' }
      );

      if (!res.ok) {
        contentArea.innerHTML = `
          <div style="padding:2rem;color:var(--text-secondary)">
            <p style="font-weight:500;color:var(--text-primary)">
              준비 중인 화면입니다.
            </p>
            <p style="font-size:12px">
              Status : ${res.status}
            </p>
          </div>
        `;
        return;
      }

      const html = await res.text();

      contentArea.innerHTML = html;

      await loadFragmentAssets(contentArea);

      window.dispatchEvent(new CustomEvent('admin:fragment-loaded', {
        detail: {
          menuName,
          context: fragmentContext[menuName] || {}
        }
      }));

    } catch (e) {

      console.error('프래그먼트 로드 실패', e);

      contentArea.innerHTML = `
        <div style="padding:2rem">
          <p style="color:red">
            화면을 불러오는데 실패했습니다.
          </p>
        </div>
      `;
    }
  }

  function appBasePath() {
    if (window.location.port === API.DEV_PORT) {
      return `http://localhost:${API.SERVICE_PORT}/admin`;
    }

    const adminIndex = window.location.pathname.indexOf('/admin');
    if (adminIndex >= 0) {
      return `${window.location.origin}/admin`;
    }

    return window.location.origin;
  }

  function resolveFragmentAssetUrl(rawUrl) {
    if (!rawUrl) return '';

    const thymeleafExpression = rawUrl.match(/^@\{(.+)\}$/);
    const url = thymeleafExpression ? thymeleafExpression[1] : rawUrl;

    if (/^(https?:)?\/\//.test(url)) return url;
    if (url.startsWith('/admin/')) return `${window.location.origin}${url}`;
    if (url.startsWith('/')) return `${appBasePath()}${url}`;

    return url;
  }

  async function loadFragmentAssets(contentArea) {
    document.querySelectorAll('script[data-fragment-script="true"]').forEach(script => script.remove());
    document.querySelectorAll('link[data-fragment-style="true"]').forEach(link => link.remove());

    const links = [...contentArea.querySelectorAll('link[rel="stylesheet"]')];

    for (const oldLink of links) {
      const rawHref = oldLink.getAttribute('href') || oldLink.getAttribute('th:href');
      const href = resolveFragmentAssetUrl(rawHref);

      oldLink.remove();
      if (!href) continue;

      const newLink = document.createElement('link');
      newLink.rel = 'stylesheet';
      newLink.href = href;
      newLink.dataset.fragmentStyle = 'true';
      document.head.appendChild(newLink);
    }

    const scripts = [...contentArea.querySelectorAll('script')];

    for (const oldScript of scripts) {
      const newScript = document.createElement('script');
      const rawSrc = oldScript.getAttribute('src') || oldScript.getAttribute('th:src');
      const src = resolveFragmentAssetUrl(rawSrc);

      newScript.async = false;
      newScript.dataset.fragmentScript = 'true';

      for (const attr of oldScript.attributes) {
        if (attr.name === 'src' || attr.name === 'th:src') continue;
        newScript.setAttribute(attr.name, attr.value);
      }

      oldScript.remove();

      if (src) {
        newScript.src = src;
        document.body.appendChild(newScript);

        await new Promise((resolve, reject) => {
          newScript.onload = resolve;
          newScript.onerror = () => reject(new Error(`Fragment script load failed: ${src}`));
        });
      } else {
        newScript.textContent = oldScript.textContent;
        document.body.appendChild(newScript);
      }
    }
  }

  window.switchMenu = switchMenu;
  window.toggleMenuGroup = toggleMenuGroup;
  window.switchMenuWithContext = function (menuName, context = {}) {
    const btn = document.querySelector(`.menu-btn[data-menu="${menuName}"]`);
    return switchMenu(menuName, btn, context);
  };
  window.toggleTheme = toggleTheme;
