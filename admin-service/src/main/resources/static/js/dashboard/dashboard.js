  let fragmentContext = {};
  const disabledMenus = new Set();

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

  let dashboardEmbedWindowSeq = 0;
  let dashboardEmbedWindowZ = 2500;

  function getDashboardWindowLayer() {
    let layer = document.getElementById('dashboard-window-layer');
    if (!layer) {
      layer = document.createElement('div');
      layer.id = 'dashboard-window-layer';
      layer.className = 'dashboard-window-layer';
      document.body.appendChild(layer);
    }
    return layer;
  }

  function getDashboardWindowTaskbar() {
    let taskbar = document.getElementById('dashboard-window-taskbar');
    if (!taskbar) {
      taskbar = document.createElement('div');
      taskbar.id = 'dashboard-window-taskbar';
      taskbar.className = 'dashboard-window-taskbar';
      document.body.appendChild(taskbar);
    }
    return taskbar;
  }

  function escapeHtml(value) {
    return String(value)
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;');
  }

  function focusDashboardEmbedWindow(win) {
    document.querySelectorAll('.dashboard-embed-window')
      .forEach(item => item.classList.remove('is-focused'));

    win.classList.add('is-focused');
    win.style.zIndex = String(++dashboardEmbedWindowZ);
  }

  function getDashboardWorkspaceRect() {
    const layout = document.querySelector('.dashboard-layout');
    const rect = layout ? layout.getBoundingClientRect() : document.body.getBoundingClientRect();
    return {
      left: Math.max(12, rect.left + 12),
      top: Math.max(12, rect.top + 12),
      width: Math.max(720, rect.width - 24),
      height: Math.max(520, rect.height - 24)
    };
  }

  function placeDashboardEmbedWindow(win, placement) {
    const rect = getDashboardWorkspaceRect();
    const gap = 10;

    if (placement === 'left' || placement === 'right') {
      const width = Math.floor((rect.width - gap) / 2);
      win.style.left = `${placement === 'left' ? rect.left : rect.left + width + gap}px`;
      win.style.top = `${rect.top}px`;
      win.style.width = `${width}px`;
      win.style.height = `${rect.height}px`;
      return;
    }

    if (placement === 'full') {
      win.style.left = `${rect.left}px`;
      win.style.top = `${rect.top}px`;
      win.style.width = `${rect.width}px`;
      win.style.height = `${rect.height}px`;
      return;
    }

    win.style.left = `${rect.left}px`;
    win.style.top = `${rect.top}px`;
    win.style.width = `${Math.floor((rect.width - gap) / 2)}px`;
    win.style.height = `${rect.height}px`;
  }

  function makeDashboardEmbedWindowDraggable(win, handle) {
    let drag = null;

    handle.addEventListener('pointerdown', event => {
      if (event.target.closest('button')) return;

      if (win.classList.contains('is-maximized')) {
        toggleDashboardEmbedWindowMaximize(win);
      }

      const rect = win.getBoundingClientRect();
      drag = {
        pointerId: event.pointerId,
        offsetX: event.clientX - rect.left,
        offsetY: event.clientY - rect.top
      };

      focusDashboardEmbedWindow(win);
      win.classList.add('is-dragging');
      handle.setPointerCapture(event.pointerId);
    });

    handle.addEventListener('pointermove', event => {
      if (!drag || drag.pointerId !== event.pointerId) return;

      const rect = win.getBoundingClientRect();
      const minVisible = 80;
      const minTitleVisible = 34;
      const left = Math.min(
        Math.max(-(rect.width - minVisible), event.clientX - drag.offsetX),
        window.innerWidth - minVisible
      );
      const top = Math.min(
        Math.max(0, event.clientY - drag.offsetY),
        window.innerHeight - minTitleVisible
      );

      win.style.left = `${left}px`;
      win.style.top = `${top}px`;
    });

    handle.addEventListener('pointerup', event => {
      if (!drag || drag.pointerId !== event.pointerId) return;
      drag = null;
      win.classList.remove('is-dragging');
      handle.releasePointerCapture(event.pointerId);
    });
  }

  function makeDashboardEmbedWindowResizable(win, handle) {
    let resize = null;

    handle.addEventListener('pointerdown', event => {
      win.classList.remove('is-maximized');

      const rect = win.getBoundingClientRect();
      resize = {
        pointerId: event.pointerId,
        startX: event.clientX,
        startY: event.clientY,
        startWidth: rect.width,
        startHeight: rect.height
      };

      event.preventDefault();
      focusDashboardEmbedWindow(win);
      win.classList.add('is-resizing');
      handle.setPointerCapture(event.pointerId);
    });

    handle.addEventListener('pointermove', event => {
      if (!resize || resize.pointerId !== event.pointerId) return;

      const minWidth = parseFloat(getComputedStyle(win).minWidth) || 520;
      const minHeight = parseFloat(getComputedStyle(win).minHeight) || 420;
      const width = Math.max(minWidth, resize.startWidth + event.clientX - resize.startX);
      const height = Math.max(minHeight, resize.startHeight + event.clientY - resize.startY);

      win.style.width = `${width}px`;
      win.style.height = `${height}px`;
      scheduleDashboardEmbedLayoutWidthLock(win);
    });

    handle.addEventListener('pointerup', event => {
      if (!resize || resize.pointerId !== event.pointerId) return;
      resize = null;
      win.classList.remove('is-resizing');
      lockDashboardEmbedLayoutWidth(win);
      handle.releasePointerCapture(event.pointerId);
    });
  }

  function lockDashboardEmbedLayoutWidth(win) {
    const frame = win.querySelector('iframe');
    if (!frame) return;

    const applyWidth = () => {
      const width = Math.ceil(frame.getBoundingClientRect().width);
      if (!width) return;
      const nextWidth = Math.max(Number(win.dataset.embedLayoutMinWidth || 0), width);
      win.dataset.embedLayoutMinWidth = String(nextWidth);

      try {
        frame.contentDocument?.documentElement.style.setProperty('--embed-layout-min-width', `${nextWidth}px`);
      } catch (e) {
        console.warn('iframe layout width lock failed:', e);
      }
    };

    if (frame.contentDocument?.readyState === 'complete') {
      applyWidth();
    } else {
      frame.addEventListener('load', applyWidth, { once: true });
    }
  }

  function scheduleDashboardEmbedLayoutWidthLock(win) {
    requestAnimationFrame(() => {
      requestAnimationFrame(() => lockDashboardEmbedLayoutWidth(win));
    });
  }

  function restoreDashboardEmbedWindow(win) {
    const task = document.querySelector(`.dashboard-window-task[data-window-id="${win.id}"]`);
    if (task) task.remove();

    win.classList.remove('is-minimized');
    focusDashboardEmbedWindow(win);
  }

  function minimizeDashboardEmbedWindow(win, title) {
    const taskbar = getDashboardWindowTaskbar();
    const existingTask = taskbar.querySelector(`[data-window-id="${win.id}"]`);
    if (existingTask) {
      win.classList.add('is-minimized');
      return;
    }

    const task = document.createElement('button');
    task.className = 'dashboard-window-task';
    task.type = 'button';
    task.dataset.windowId = win.id;
    task.innerHTML = `<i class="ti ti-window"></i><span>${escapeHtml(title)}</span>`;
    task.addEventListener('click', () => restoreDashboardEmbedWindow(win));
    taskbar.appendChild(task);
    win.classList.add('is-minimized');
  }

  function toggleDashboardEmbedWindowMaximize(win) {
    if (win.classList.contains('is-maximized')) {
      win.style.left = win.dataset.restoreLeft || win.style.left;
      win.style.top = win.dataset.restoreTop || win.style.top;
      win.style.width = win.dataset.restoreWidth || win.style.width;
      win.style.height = win.dataset.restoreHeight || win.style.height;
      win.classList.remove('is-maximized');
      return;
    }

    win.dataset.restoreLeft = win.style.left;
    win.dataset.restoreTop = win.style.top;
    win.dataset.restoreWidth = win.style.width;
    win.dataset.restoreHeight = win.style.height;
    win.classList.add('is-maximized');
    placeDashboardEmbedWindow(win, 'full');
  }

  function openDashboardEmbedWindow(menuName, title) {
    const layer = getDashboardWindowLayer();
    const win = document.createElement('section');
    const windowId = `dashboard-embed-window-${Date.now()}-${dashboardEmbedWindowSeq}`;
    const url = `${base()}/admin/api/${API.VERSION}/view/embed/${menuName}`;
    win.className = 'dashboard-embed-window';
    win.id = windowId;
    win.setAttribute('aria-label', title);
    win.innerHTML = `
      <div class="dashboard-browser-chrome">
        <div class="dashboard-browser-topbar">
          <span class="dashboard-browser-title"><i class="ti ti-circle-dot"></i>${escapeHtml(title)}</span>
          <div class="dashboard-window-controls">
            <button class="dashboard-window-control" type="button" data-minimize="true" title="최소화" aria-label="최소화">
              <i class="ti ti-minus"></i>
            </button>
            <button class="dashboard-window-control" type="button" data-maximize="true" title="최대화" aria-label="최대화">
              <i class="ti ti-square"></i>
            </button>
            <button class="dashboard-window-control close" type="button" data-close="true" title="닫기" aria-label="닫기">
              <i class="ti ti-x"></i>
            </button>
          </div>
        </div>
        <div class="dashboard-browser-addressbar">
          <button class="dashboard-browser-nav" type="button" data-reload="true" title="새로고침" aria-label="새로고침">
            <i class="ti ti-refresh"></i>
          </button>
          <div class="dashboard-browser-url" title="${escapeHtml(url)}">
            <i class="ti ti-lock"></i>
            <span>${escapeHtml(url)}</span>
          </div>
          <div class="dashboard-browser-tools">
            <button class="dashboard-browser-tool" type="button" data-place="left" title="왼쪽 배치" aria-label="왼쪽 배치">
              <i class="ti ti-layout-sidebar-left-collapse"></i>
            </button>
            <button class="dashboard-browser-tool" type="button" data-place="right" title="오른쪽 배치" aria-label="오른쪽 배치">
              <i class="ti ti-layout-sidebar-right-collapse"></i>
            </button>
          </div>
        </div>
      </div>
      <iframe class="dashboard-embed-frame" src="${escapeHtml(url)}" scrolling="yes"></iframe>
      <div class="dashboard-window-resize-handle" title="크기 조절" aria-label="크기 조절"></div>
    `;

    const titlebar = win.querySelector('.dashboard-browser-topbar');
    makeDashboardEmbedWindowDraggable(win, titlebar);
    makeDashboardEmbedWindowResizable(win, win.querySelector('.dashboard-window-resize-handle'));

    win.addEventListener('pointerdown', () => focusDashboardEmbedWindow(win));
    win.querySelector('[data-minimize="true"]').addEventListener('click', () => minimizeDashboardEmbedWindow(win, title));
    win.querySelector('[data-maximize="true"]').addEventListener('click', () => {
      focusDashboardEmbedWindow(win);
      toggleDashboardEmbedWindowMaximize(win);
      if (win.classList.contains('is-maximized')) {
        scheduleDashboardEmbedLayoutWidthLock(win);
      }
    });
    win.querySelector('[data-reload="true"]').addEventListener('click', () => {
      const frame = win.querySelector('iframe');
      if (frame) frame.src = frame.src;
    });
    win.querySelector('[data-close="true"]').addEventListener('click', () => {
      const frame = win.querySelector('iframe');
      if (frame) frame.src = 'about:blank';
      const task = document.querySelector(`.dashboard-window-task[data-window-id="${win.id}"]`);
      if (task) task.remove();
      win.remove();
    });
    win.querySelectorAll('[data-place]').forEach(button => {
      button.addEventListener('click', () => {
        focusDashboardEmbedWindow(win);
        win.classList.remove('is-maximized');
        placeDashboardEmbedWindow(win, button.dataset.place);
        scheduleDashboardEmbedLayoutWidthLock(win);
      });
    });

    layer.appendChild(win);
    placeDashboardEmbedWindow(win);
    win.dataset.restoreLeft = win.style.left;
    win.dataset.restoreTop = win.style.top;
    win.dataset.restoreWidth = win.style.width;
    win.dataset.restoreHeight = win.style.height;
    win.classList.add('is-maximized');
    placeDashboardEmbedWindow(win, 'full');
    win.querySelector('iframe').addEventListener('load', () => scheduleDashboardEmbedLayoutWidthLock(win));
    scheduleDashboardEmbedLayoutWidthLock(win);
    dashboardEmbedWindowSeq += 1;
    focusDashboardEmbedWindow(win);
  }

  window.switchMenu = switchMenu;
  window.toggleMenuGroup = toggleMenuGroup;
  window.openDashboardEmbedWindow = openDashboardEmbedWindow;
  window.switchMenuWithContext = function (menuName, context = {}) {
    const btn = document.querySelector(`.menu-btn[data-menu="${menuName}"]`);
    return switchMenu(menuName, btn, context);
  };
  window.toggleTheme = toggleTheme;
