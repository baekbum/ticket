(function () {
  const INQUIRY_URL = `${base()}/admin/api/${API.VERSION}/inquiry`;
  const JSON_HEADERS = { 'Content-Type': 'application/json' };

  let currentInquiries = [];
  let currentInquiry = null;
  let serverTotalPages = 1;
  let submitting = false;

  const categoryLabels = {
    BOOKING: '예매', PAYMENT: '결제', REFUND: '취소/환불',
    TICKET: '티켓', ACCOUNT: '계정', ETC: '기타'
  };
  const statusLabels = { WAITING: '답변 대기', ANSWERED: '답변 완료' };

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
    return `<span class="inquiry-badge ${escapeHtml(cssClass)}">${escapeHtml(statusLabels[status] || status || '-')}</span>`;
  }

  function buildSearchUrl(page) {
    const size = Number(byId('pagination-size')?.value || 20);
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const status = valueOf('inquiry-search-status');
    const category = valueOf('inquiry-search-category');
    const keyword = valueOf('inquiry-search-keyword');
    if (status) params.set('status', status);
    if (category) params.set('category', category);
    if (keyword) params.set('keyword', keyword);
    return `${INQUIRY_URL}/select?${params.toString()}`;
  }

  function renderInquiries(page, pageSize) {
    const tbody = byId('inquiry-table-body');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (currentInquiries.length === 0) {
      tbody.innerHTML = '<tr><td colspan="8" class="inquiry-empty-row">조회된 문의가 없습니다.</td></tr>';
      return;
    }

    currentInquiries.forEach((inquiry, index) => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td class="center muted">${page * pageSize + index + 1}</td>
        <td><strong>${escapeHtml(inquiry.inquiryId)}</strong></td>
        <td>${escapeHtml(categoryLabels[inquiry.category] || inquiry.category || '-')}</td>
        <td title="${escapeHtml(inquiry.title)}"><strong>${escapeHtml(inquiry.title || '-')}</strong></td>
        <td title="${escapeHtml(inquiry.requesterId)}">${escapeHtml(inquiry.requesterId || '-')}</td>
        <td>${statusBadge(inquiry.status)}</td>
        <td>${formatDateTime(inquiry.createdAt)}</td>
        <td class="actions" onclick="event.stopPropagation()">
          <button class="inquiry-btn small outline" type="button" onclick="openInquiryDetail(${Number(inquiry.inquiryId)})">상세</button>
        </td>`;
      tr.addEventListener('click', () => openInquiryDetail(inquiry.inquiryId));
      tbody.appendChild(tr);
    });
  }

  window.loadInquiryList = async function (page = 0) {
    try {
      const response = await Fetch(buildSearchUrl(page), { method: 'GET' });
      if (!response.ok) {
        await showResponseError(response, '문의 목록 조회에 실패했습니다.');
        return;
      }
      const paged = await response.json();
      currentInquiries = paged.content || [];
      const metadata = paged.page || {};
      serverTotalPages = Math.max(Number(metadata.totalPages || 1), 1);
      const pageSize = Number(metadata.size || byId('pagination-size')?.value || 20);
      byId('pagination-current').value = Number(metadata.number ?? page) + 1;
      byId('pagination-total').textContent = serverTotalPages;
      byId('pagination-total-count').textContent = Number(metadata.totalElements || 0).toLocaleString();
      renderInquiries(Number(metadata.number ?? page), pageSize);
    } catch (error) {
      console.error('Inquiry list load failed', error);
      showToast('문의 목록을 불러오는 중 통신 오류가 발생했습니다.', true);
    }
  };

  window.triggerInquirySearch = function () {
    loadInquiryList(0);
  };

  window.resetInquirySearch = function () {
    byId('inquiry-search-status').value = '';
    byId('inquiry-search-category').value = '';
    byId('inquiry-search-keyword').value = '';
    loadInquiryList(0);
  };

  function fillInquiryDetail(inquiry) {
    const answer = inquiry.answer;
    byId('inquiry-target-id').value = inquiry.inquiryId ?? '';
    byId('inquiry-detail-id').textContent = inquiry.inquiryId ?? '-';
    byId('inquiry-detail-requester').textContent = inquiry.requesterId || '-';
    byId('inquiry-detail-category').textContent = categoryLabels[inquiry.category] || inquiry.category || '-';
    byId('inquiry-detail-status').innerHTML = statusBadge(inquiry.status);
    byId('inquiry-detail-created').textContent = formatDateTime(inquiry.createdAt);
    byId('inquiry-detail-title').textContent = inquiry.title || '-';
    byId('inquiry-detail-content').textContent = inquiry.content || '-';
    byId('inquiry-answer-content').value = answer?.content || '';
    byId('inquiry-answer-meta').textContent = answer
      ? `${answer.responderId || '-'} · ${formatDateTime(answer.updatedAt || answer.createdAt)}`
      : '아직 답변이 등록되지 않았습니다.';
    byId('inquiry-answer-submit').querySelector('span').textContent = answer ? '답변 수정' : '답변 등록';
    updateAnswerLength();
  }

  window.openInquiryDetail = async function (inquiryId) {
    try {
      const response = await Fetch(`${INQUIRY_URL}/select/id/${inquiryId}`, { method: 'GET' });
      if (!response.ok) {
        await showResponseError(response, '문의 상세 조회에 실패했습니다.');
        return;
      }
      currentInquiry = await response.json();
      fillInquiryDetail(currentInquiry);
      byId('inquiry-modal').style.display = 'flex';
      byId('inquiry-answer-content').focus();
    } catch (error) {
      console.error('Inquiry detail load failed', error);
      showToast('문의 상세를 불러오는 중 통신 오류가 발생했습니다.', true);
    }
  };

  window.closeInquiryModal = function () {
    byId('inquiry-modal').style.display = 'none';
    currentInquiry = null;
  };

  function updateAnswerLength() {
    byId('inquiry-answer-length').textContent = String(byId('inquiry-answer-content')?.value.length || 0);
  }

  window.submitInquiryAnswer = async function () {
    if (!currentInquiry || submitting) return;
    const content = valueOf('inquiry-answer-content');
    if (!content) {
      showToast('답변 내용을 입력해주세요.', true);
      return;
    }
    if (content.length > 10000) {
      showToast('답변 내용은 10,000자 이하여야 합니다.', true);
      return;
    }

    submitting = true;
    byId('inquiry-answer-submit').disabled = true;
    const updating = Boolean(currentInquiry.answer);
    try {
      const response = await Fetch(`${INQUIRY_URL}/answer/id/${currentInquiry.inquiryId}`, {
        method: updating ? 'PUT' : 'POST',
        headers: JSON_HEADERS,
        body: JSON.stringify({ content })
      });
      if (!response.ok) {
        await showResponseError(response, updating ? '답변 수정에 실패했습니다.' : '답변 등록에 실패했습니다.');
        return;
      }
      currentInquiry = await response.json();
      fillInquiryDetail(currentInquiry);
      showToast(updating ? '답변을 수정했습니다.' : '답변을 등록했습니다.');
      loadInquiryList(Math.max(Number(byId('pagination-current').value || 1) - 1, 0));
    } catch (error) {
      console.error('Inquiry answer save failed', error);
      showToast('답변 저장 중 통신 오류가 발생했습니다.', true);
    } finally {
      submitting = false;
      byId('inquiry-answer-submit').disabled = false;
    }
  };

  byId('inquiry-answer-content')?.addEventListener('input', updateAnswerLength);
  byId('inquiry-modal')?.addEventListener('click', event => {
    if (event.target === byId('inquiry-modal')) closeInquiryModal();
  });
  window.addEventListener('keydown', event => {
    if (event.key === 'Escape' && byId('inquiry-modal')?.style.display === 'flex') closeInquiryModal();
  });

  window.Pagination.register({
    load: window.loadInquiryList,
    getTotalPages: () => serverTotalPages
  });
  loadInquiryList(0);
})();
