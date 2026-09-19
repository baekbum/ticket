(function () {
  const NOTICE_URL = `${base()}/admin/api/${API.VERSION}/notice`;
  const JSON_HEADERS = { 'Content-Type': 'application/json' };

  let currentNotices = [];
  let currentNotice = null;
  let serverTotalPages = 1;

  const categoryLabels = {
    GENERAL: '일반',
    SERVICE: '서비스',
    EVENT: '이벤트',
    MAINTENANCE: '점검'
  };
  const statusLabels = {
    DRAFT: '초안',
    PUBLISHED: '게시',
    HIDDEN: '숨김',
    ARCHIVED: '보관'
  };

  function byId(id) {
    return document.getElementById(id);
  }

  function valueOf(id) {
    return byId(id)?.value?.trim() || '';
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  function formatDateTime(value) {
    if (!value) return '-';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return escapeHtml(value);
    return new Intl.DateTimeFormat('ko-KR', {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', hour12: false
    }).format(date);
  }

  function statusBadge(status) {
    const cssClass = String(status || '').toLowerCase();
    return `<span class="notice-badge ${escapeHtml(cssClass)}">${escapeHtml(statusLabels[status] || status || '-')}</span>`;
  }

  function buildSearchUrl(page) {
    const size = Number(byId('pagination-size')?.value || 10);
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const status = valueOf('notice-search-status');
    const category = valueOf('notice-search-category');
    const keyword = valueOf('notice-search-keyword');
    if (status) params.set('status', status);
    if (category) params.set('category', category);
    if (keyword) params.set('keyword', keyword);
    return `${NOTICE_URL}/select?${params.toString()}`;
  }

  function renderNotices(page, pageSize) {
    const tbody = byId('notice-table-body');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (currentNotices.length === 0) {
      tbody.innerHTML = '<tr><td colspan="9" style="padding:2rem;text-align:center;color:var(--notice-muted)">조회된 공지사항이 없습니다.</td></tr>';
      return;
    }

    currentNotices.forEach((notice, index) => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td style="text-align:center;color:var(--notice-muted)">${page * pageSize + index + 1}</td>
        <td><strong>${escapeHtml(notice.noticeId)}</strong></td>
        <td>${notice.pinned ? '<span class="notice-pin"><i class="ti ti-pin-filled"></i> 중요</span>' : '-'}</td>
        <td>${escapeHtml(categoryLabels[notice.category] || notice.category || '-')}</td>
        <td title="${escapeHtml(notice.title)}"><strong>${escapeHtml(notice.title || '-')}</strong></td>
        <td>${statusBadge(notice.status)}</td>
        <td style="text-align:right">${Number(notice.viewCount || 0).toLocaleString()}</td>
        <td>${formatDateTime(notice.publishedAt)}</td>
        <td class="actions" onclick="event.stopPropagation()">
          <button class="notice-btn small outline" type="button" onclick="openNoticeModalForView(${Number(notice.noticeId)})">상세</button>
          <button class="notice-btn small outline" type="button" onclick="openNoticeModalForUpdate(${Number(notice.noticeId)})">수정</button>
        </td>`;
      tr.addEventListener('click', () => openNoticeModalForView(notice.noticeId));
      tbody.appendChild(tr);
    });
  }

  window.loadNoticeList = async function (page = 0) {
    try {
      const response = await Fetch(buildSearchUrl(page), { method: 'GET' });
      if (!response.ok) {
        await showResponseError(response, '공지사항 목록 조회에 실패했습니다.');
        return;
      }

      const paged = await response.json();
      currentNotices = paged.content || [];
      const metadata = paged.page || {};
      serverTotalPages = Math.max(Number(metadata.totalPages || 1), 1);
      const pageSize = Number(metadata.size || byId('pagination-size')?.value || 10);

      byId('pagination-current').value = Number(metadata.number ?? page) + 1;
      byId('pagination-total').textContent = serverTotalPages;
      byId('pagination-total-count').textContent = Number(metadata.totalElements || 0).toLocaleString();
      renderNotices(Number(metadata.number ?? page), pageSize);
    } catch (error) {
      console.error('Notice list load failed', error);
      showToast('공지사항 목록을 불러오는 중 통신 오류가 발생했습니다.', true);
    }
  };

  window.triggerNoticeSearch = function () {
    loadNoticeList(0);
  };

  window.resetNoticeSearch = function () {
    byId('notice-search-status').value = '';
    byId('notice-search-category').value = '';
    byId('notice-search-keyword').value = '';
    loadNoticeList(0);
  };

  function setFormDisabled(disabled) {
    ['notice-title', 'notice-category', 'notice-status', 'notice-pinned', 'notice-content']
      .forEach(id => { if (byId(id)) byId(id).disabled = disabled; });
  }

  function setCreationStatusOptions(creationMode) {
    const select = byId('notice-status');
    ['HIDDEN', 'ARCHIVED'].forEach(value => {
      const option = select?.querySelector(`option[value="${value}"]`);
      if (option) option.disabled = creationMode;
    });
  }

  function fillNoticeForm(notice) {
    byId('notice-target-id').value = notice.noticeId ?? '';
    byId('notice-title').value = notice.title ?? '';
    byId('notice-category').value = notice.category || 'GENERAL';
    byId('notice-status').value = notice.status || 'DRAFT';
    byId('notice-pinned').checked = Boolean(notice.pinned);
    byId('notice-content').value = notice.content ?? '';
    byId('notice-meta-author').textContent = notice.authorId || '-';
    byId('notice-meta-views').textContent = Number(notice.viewCount || 0).toLocaleString();
    byId('notice-meta-published').textContent = formatDateTime(notice.publishedAt);
  }

  function configureModal(mode) {
    const isCreate = mode === 'CREATE';
    const isView = mode === 'VIEW';
    byId('notice-modal-mode').value = mode;
    byId('notice-modal-title').textContent = isCreate ? '공지사항 등록' : isView ? '공지사항 상세' : '공지사항 수정';
    byId('notice-modal-subtitle').textContent = isCreate
      ? '새 공지의 내용과 최초 게시 상태를 설정합니다.'
      : isView ? '등록된 공지사항의 상세 내용을 확인합니다.' : '공지 내용과 게시 상태를 수정합니다.';
    byId('notice-save-btn').hidden = isView;
    byId('notice-edit-btn').hidden = !isView;
    byId('notice-delete-btn').hidden = isCreate || currentNotice?.status === 'ARCHIVED';
    byId('notice-meta').hidden = isCreate;
    setCreationStatusOptions(isCreate);
    setFormDisabled(isView);
  }

  window.openNoticeModalForCreate = function () {
    currentNotice = null;
    fillNoticeForm({ category: 'GENERAL', status: 'DRAFT', pinned: false, viewCount: 0 });
    configureModal('CREATE');
    byId('notice-modal').style.display = 'flex';
    byId('notice-title').focus();
  };

  async function fetchNotice(noticeId) {
    const response = await Fetch(`${NOTICE_URL}/select/id/${noticeId}`, { method: 'GET' });
    if (!response.ok) {
      await showResponseError(response, '공지사항 조회에 실패했습니다.');
      return null;
    }
    return response.json();
  }

  async function openExistingNotice(noticeId, mode) {
    try {
      currentNotice = await fetchNotice(noticeId);
      if (!currentNotice) return;
      fillNoticeForm(currentNotice);
      configureModal(mode);
      byId('notice-modal').style.display = 'flex';
    } catch (error) {
      console.error('Notice detail load failed', error);
      showToast('공지사항을 불러오는 중 통신 오류가 발생했습니다.', true);
    }
  }

  window.openNoticeModalForView = noticeId => openExistingNotice(noticeId, 'VIEW');
  window.openNoticeModalForUpdate = noticeId => openExistingNotice(noticeId, 'UPDATE');

  window.switchNoticeModalToEdit = function () {
    configureModal('UPDATE');
    byId('notice-title').focus();
  };

  window.closeNoticeModal = function () {
    byId('notice-modal').style.display = 'none';
    currentNotice = null;
  };

  function readPayload() {
    const status = valueOf('notice-status') || 'DRAFT';
    const payload = {
      title: valueOf('notice-title'),
      content: valueOf('notice-content'),
      category: valueOf('notice-category'),
      pinned: Boolean(byId('notice-pinned')?.checked)
    };
    if (valueOf('notice-modal-mode') === 'CREATE') payload.published = status === 'PUBLISHED';
    else payload.status = status;
    return payload;
  }

  function validatePayload(payload) {
    if (!payload.title) return '공지 제목을 입력해주세요.';
    if (payload.title.length > 200) return '공지 제목은 200자 이하여야 합니다.';
    if (!payload.content) return '공지 내용을 입력해주세요.';
    if (!payload.category) return '공지 분류를 선택해주세요.';
    return null;
  }

  window.submitNoticeForm = async function () {
    const mode = valueOf('notice-modal-mode');
    const payload = readPayload();
    const validationMessage = validatePayload(payload);
    if (validationMessage) {
      showToast(validationMessage, true);
      return;
    }

    const noticeId = valueOf('notice-target-id');
    const updating = mode === 'UPDATE';
    const url = updating ? `${NOTICE_URL}/update/id/${noticeId}` : `${NOTICE_URL}/insert`;
    try {
      const response = await Fetch(url, {
        method: updating ? 'PUT' : 'POST',
        headers: JSON_HEADERS,
        body: JSON.stringify(payload)
      });
      if (!response.ok) {
        await showResponseError(response, updating ? '공지사항 수정에 실패했습니다.' : '공지사항 등록에 실패했습니다.');
        return;
      }
      showToast(updating ? '공지사항을 수정했습니다.' : '공지사항을 등록했습니다.');
      closeNoticeModal();
      loadNoticeList(Math.max(Number(byId('pagination-current').value || 1) - 1, 0));
    } catch (error) {
      console.error('Notice save failed', error);
      showToast('공지사항 저장 중 통신 오류가 발생했습니다.', true);
    }
  };

  window.deleteCurrentNotice = async function () {
    const noticeId = valueOf('notice-target-id');
    if (!noticeId || !window.confirm('이 공지사항을 보관 처리하시겠습니까? 사용자 화면에서는 더 이상 노출되지 않습니다.')) return;

    try {
      const response = await Fetch(`${NOTICE_URL}/delete/id/${noticeId}`, { method: 'DELETE' });
      if (!response.ok) {
        await showResponseError(response, '공지사항 보관 처리에 실패했습니다.');
        return;
      }
      showToast('공지사항을 보관 처리했습니다.');
      closeNoticeModal();
      loadNoticeList(Math.max(Number(byId('pagination-current').value || 1) - 1, 0));
    } catch (error) {
      console.error('Notice delete failed', error);
      showToast('공지사항 보관 처리 중 통신 오류가 발생했습니다.', true);
    }
  };

  window.Pagination.register({
    load: window.loadNoticeList,
    getTotalPages: () => serverTotalPages
  });
  loadNoticeList(0);
})();
