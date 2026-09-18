(function () {
  const FAQ_URL = `${base()}/admin/api/${API.VERSION}/faq`;
  const JSON_HEADERS = { 'Content-Type': 'application/json' };

  let currentFaqs = [];
  let currentFaq = null;
  let serverTotalPages = 1;

  const categoryLabels = {
    BOOKING: '예매',
    PAYMENT: '결제',
    REFUND: '취소/환불',
    TICKET: '티켓',
    ACCOUNT: '계정',
    ETC: '기타'
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
    return `<span class="faq-badge ${escapeHtml(cssClass)}">${escapeHtml(statusLabels[status] || status || '-')}</span>`;
  }

  function buildSearchUrl(page) {
    const size = Number(byId('pagination-size')?.value || 10);
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const status = valueOf('faq-search-status');
    const category = valueOf('faq-search-category');
    const keyword = valueOf('faq-search-keyword');
    if (status) params.set('status', status);
    if (category) params.set('category', category);
    if (keyword) params.set('keyword', keyword);
    return `${FAQ_URL}/select?${params.toString()}`;
  }

  function renderFaqs(page, pageSize) {
    const tbody = byId('faq-table-body');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (currentFaqs.length === 0) {
      tbody.innerHTML = '<tr><td colspan="8" style="padding:2rem;text-align:center;color:var(--faq-muted)">조회된 FAQ가 없습니다.</td></tr>';
      return;
    }

    currentFaqs.forEach((faq, index) => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td style="text-align:center;color:var(--faq-muted)">${page * pageSize + index + 1}</td>
        <td><strong>${escapeHtml(faq.faqId)}</strong></td>
        <td style="text-align:right">${Number(faq.displayOrder || 0).toLocaleString()}</td>
        <td>${escapeHtml(categoryLabels[faq.category] || faq.category || '-')}</td>
        <td title="${escapeHtml(faq.question)}"><strong>${escapeHtml(faq.question || '-')}</strong></td>
        <td>${statusBadge(faq.status)}</td>
        <td>${formatDateTime(faq.updatedAt)}</td>
        <td class="actions" onclick="event.stopPropagation()">
          <button class="faq-btn small outline" type="button" onclick="openFaqModalForView(${Number(faq.faqId)})">상세</button>
          <button class="faq-btn small outline" type="button" onclick="openFaqModalForUpdate(${Number(faq.faqId)})">수정</button>
        </td>`;
      tr.addEventListener('click', () => openFaqModalForView(faq.faqId));
      tbody.appendChild(tr);
    });
  }

  window.loadFaqList = async function (page = 0) {
    try {
      const response = await Fetch(buildSearchUrl(page), { method: 'GET' });
      if (!response.ok) {
        await showResponseError(response, 'FAQ 목록 조회에 실패했습니다.');
        return;
      }

      const paged = await response.json();
      currentFaqs = paged.content || [];
      const metadata = paged.page || {};
      serverTotalPages = Math.max(Number(metadata.totalPages || 1), 1);
      const pageSize = Number(metadata.size || byId('pagination-size')?.value || 10);

      byId('pagination-current').value = Number(metadata.number ?? page) + 1;
      byId('pagination-total').textContent = serverTotalPages;
      byId('pagination-total-count').textContent = Number(metadata.totalElements || 0).toLocaleString();
      renderFaqs(Number(metadata.number ?? page), pageSize);
    } catch (error) {
      console.error('FAQ list load failed', error);
      showToast('FAQ 목록을 불러오는 중 통신 오류가 발생했습니다.', true);
    }
  };

  window.triggerFaqSearch = function () {
    loadFaqList(0);
  };

  window.resetFaqSearch = function () {
    byId('faq-search-status').value = '';
    byId('faq-search-category').value = '';
    byId('faq-search-keyword').value = '';
    loadFaqList(0);
  };

  function setFormDisabled(disabled) {
    ['faq-question', 'faq-status', 'faq-category', 'faq-display-order', 'faq-answer']
      .forEach(id => { if (byId(id)) byId(id).disabled = disabled; });
  }

  function setCreationStatusOptions(creationMode) {
    const select = byId('faq-status');
    ['HIDDEN', 'ARCHIVED'].forEach(value => {
      const option = select?.querySelector(`option[value="${value}"]`);
      if (option) option.disabled = creationMode;
    });
  }

  function fillFaqForm(faq) {
    byId('faq-target-id').value = faq.faqId ?? '';
    byId('faq-question').value = faq.question ?? '';
    byId('faq-category').value = faq.category || 'BOOKING';
    byId('faq-status').value = faq.status || 'DRAFT';
    byId('faq-display-order').value = Number(faq.displayOrder || 0);
    byId('faq-answer').value = faq.answer ?? '';
    byId('faq-meta-author').textContent = faq.authorId || '-';
    byId('faq-meta-created').textContent = formatDateTime(faq.createdAt);
    byId('faq-meta-updated').textContent = formatDateTime(faq.updatedAt);
  }

  function configureModal(mode) {
    const isCreate = mode === 'CREATE';
    const isView = mode === 'VIEW';
    byId('faq-modal-mode').value = mode;
    byId('faq-modal-title').textContent = isCreate ? 'FAQ 등록' : isView ? 'FAQ 상세' : 'FAQ 수정';
    byId('faq-modal-subtitle').textContent = isCreate
      ? '새 질문과 답변, 최초 게시 상태를 설정합니다.'
      : isView ? '등록된 FAQ의 상세 내용을 확인합니다.' : '질문과 답변, 게시 상태를 수정합니다.';
    byId('faq-save-btn').hidden = isView;
    byId('faq-edit-btn').hidden = !isView;
    byId('faq-delete-btn').hidden = isCreate || currentFaq?.status === 'ARCHIVED';
    byId('faq-meta').hidden = isCreate;
    setCreationStatusOptions(isCreate);
    setFormDisabled(isView);
  }

  window.openFaqModalForCreate = function () {
    currentFaq = null;
    fillFaqForm({ category: 'BOOKING', status: 'DRAFT', displayOrder: 0 });
    configureModal('CREATE');
    byId('faq-modal').style.display = 'flex';
    byId('faq-question').focus();
  };

  async function fetchFaq(faqId) {
    const response = await Fetch(`${FAQ_URL}/select/id/${faqId}`, { method: 'GET' });
    if (!response.ok) {
      await showResponseError(response, 'FAQ 조회에 실패했습니다.');
      return null;
    }
    return response.json();
  }

  async function openExistingFaq(faqId, mode) {
    try {
      currentFaq = await fetchFaq(faqId);
      if (!currentFaq) return;
      fillFaqForm(currentFaq);
      configureModal(mode);
      byId('faq-modal').style.display = 'flex';
    } catch (error) {
      console.error('FAQ detail load failed', error);
      showToast('FAQ를 불러오는 중 통신 오류가 발생했습니다.', true);
    }
  }

  window.openFaqModalForView = faqId => openExistingFaq(faqId, 'VIEW');
  window.openFaqModalForUpdate = faqId => openExistingFaq(faqId, 'UPDATE');

  window.switchFaqModalToEdit = function () {
    configureModal('UPDATE');
    byId('faq-question').focus();
  };

  window.closeFaqModal = function () {
    byId('faq-modal').style.display = 'none';
    currentFaq = null;
  };

  function readPayload() {
    const status = valueOf('faq-status') || 'DRAFT';
    const payload = {
      question: valueOf('faq-question'),
      answer: valueOf('faq-answer'),
      category: valueOf('faq-category'),
      displayOrder: Number(valueOf('faq-display-order'))
    };
    if (valueOf('faq-modal-mode') === 'CREATE') payload.published = status === 'PUBLISHED';
    else payload.status = status;
    return payload;
  }

  function validatePayload(payload) {
    if (!payload.question) return 'FAQ 질문을 입력해주세요.';
    if (payload.question.length > 300) return 'FAQ 질문은 300자 이하여야 합니다.';
    if (!payload.answer) return 'FAQ 답변을 입력해주세요.';
    if (!payload.category) return 'FAQ 분류를 선택해주세요.';
    if (!Number.isInteger(payload.displayOrder) || payload.displayOrder < 0 || payload.displayOrder > 9999) {
      return '노출 순서는 0~9999 사이의 정수여야 합니다.';
    }
    return null;
  }

  window.submitFaqForm = async function () {
    const mode = valueOf('faq-modal-mode');
    const payload = readPayload();
    const validationMessage = validatePayload(payload);
    if (validationMessage) {
      showToast(validationMessage, true);
      return;
    }

    const faqId = valueOf('faq-target-id');
    const updating = mode === 'UPDATE';
    const url = updating ? `${FAQ_URL}/update/id/${faqId}` : `${FAQ_URL}/insert`;
    try {
      const response = await Fetch(url, {
        method: updating ? 'PUT' : 'POST',
        headers: JSON_HEADERS,
        body: JSON.stringify(payload)
      });
      if (!response.ok) {
        await showResponseError(response, updating ? 'FAQ 수정에 실패했습니다.' : 'FAQ 등록에 실패했습니다.');
        return;
      }
      showToast(updating ? 'FAQ를 수정했습니다.' : 'FAQ를 등록했습니다.');
      closeFaqModal();
      loadFaqList(Math.max(Number(byId('pagination-current').value || 1) - 1, 0));
    } catch (error) {
      console.error('FAQ save failed', error);
      showToast('FAQ 저장 중 통신 오류가 발생했습니다.', true);
    }
  };

  window.deleteCurrentFaq = async function () {
    const faqId = valueOf('faq-target-id');
    if (!faqId || !window.confirm('이 FAQ를 보관 처리하시겠습니까? 사용자 화면에서는 더 이상 노출되지 않습니다.')) return;

    try {
      const response = await Fetch(`${FAQ_URL}/delete/id/${faqId}`, { method: 'DELETE' });
      if (!response.ok) {
        await showResponseError(response, 'FAQ 보관 처리에 실패했습니다.');
        return;
      }
      showToast('FAQ를 보관 처리했습니다.');
      closeFaqModal();
      loadFaqList(Math.max(Number(byId('pagination-current').value || 1) - 1, 0));
    } catch (error) {
      console.error('FAQ delete failed', error);
      showToast('FAQ 보관 처리 중 통신 오류가 발생했습니다.', true);
    }
  };

  window.Pagination.register({
    load: window.loadFaqList,
    getTotalPages: () => serverTotalPages
  });
  loadFaqList(0);
})();
