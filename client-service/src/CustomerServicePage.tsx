import './CustomerServicePage.css';
import { useEffect, useState, type FormEvent, type ReactNode } from 'react';

export type CustomerServiceTab = 'notice' | 'guide' | 'faq' | 'inquiry';
export type GuideTab = 'booking' | 'cancel' | 'delivery';

type Props = {
  activeTab: CustomerServiceTab;
  activeGuideTab: GuideTab;
  onTabChange: (tab: CustomerServiceTab) => void;
  onGuideTabChange: (tab: GuideTab) => void;
};

const serviceTabs: Array<{ key: CustomerServiceTab; label: string }> = [
  { key: 'notice', label: '공지사항' },
  { key: 'guide', label: '이용안내' },
  { key: 'faq', label: 'FAQ' },
  { key: 'inquiry', label: '나의 문의 내역' },
];

const guideTabs: Array<{ key: GuideTab; label: string }> = [
  { key: 'booking', label: '예매방법' },
  { key: 'cancel', label: '취소/환불' },
  { key: 'delivery', label: '발권/배송' },
];

const bookingSteps = [
  {
    title: '로그인',
    description: '예매 전 Ticksy에 로그인하고 회원 정보를 확인해 주세요.',
    icon: 'user',
  },
  {
    title: '공연 선택',
    description: '카테고리 또는 검색을 이용해 관람하고 싶은 공연을 선택해 주세요.',
    icon: 'ticket',
  },
  {
    title: '일정 선택',
    description: '공연 상세에서 관람 날짜와 회차를 선택한 뒤 예매하기를 눌러 주세요.',
    icon: 'calendar',
  },
  {
    title: '좌석 선택',
    description: '좌석도에서 원하는 좌석을 고르세요. 선택한 좌석은 10분 동안 확보됩니다.',
    icon: 'seat',
  },
  {
    title: '할인 선택',
    description: '사용 가능한 쿠폰과 할인 혜택을 확인하고 적용해 주세요.',
    icon: 'coupon',
  },
  {
    title: '수령 방법',
    description: '공연별로 제공되는 현장 수령 또는 배송 방법을 선택해 주세요.',
    icon: 'box',
  },
  {
    title: '결제',
    description: '신용카드 또는 무통장입금 중 원하는 결제수단으로 결제해 주세요.',
    icon: 'card',
  },
  {
    title: '예매 확인',
    description: '마이티켓의 예매 내역에서 예매 상태와 상세 정보를 확인할 수 있어요.',
    icon: 'check',
  },
];

type NoticeCategory = 'GENERAL' | 'SERVICE' | 'EVENT' | 'MAINTENANCE';

type Notice = {
  noticeId: number;
  title: string;
  category: NoticeCategory;
  pinned: boolean;
  publishedAt: string | null;
};

type NoticePageResponse = {
  content: Notice[];
};

type NoticeDetail = Notice & {
  content: string;
  viewCount: number;
};

const noticeCategoryLabels: Record<NoticeCategory, string> = {
  GENERAL: '일반',
  SERVICE: '서비스',
  EVENT: '이벤트',
  MAINTENANCE: '점검',
};

function formatNoticeDate(value: string | null) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  return [date.getFullYear(), date.getMonth() + 1, date.getDate()]
    .map((part, index) => index === 0 ? String(part) : String(part).padStart(2, '0'))
    .join('.');
}

function readNoticeId() {
  const value = Number(new URLSearchParams(window.location.search).get('noticeId'));
  return Number.isInteger(value) && value > 0 ? value : null;
}

type FaqCategory = 'BOOKING' | 'PAYMENT' | 'REFUND' | 'TICKET' | 'ACCOUNT' | 'ETC';

type Faq = {
  faqId: number;
  question: string;
  answer: string;
  category: FaqCategory;
  displayOrder: number;
};

type FaqPageResponse = {
  content: Faq[];
  page?: {
    totalElements?: number;
  };
};

const faqCategoryLabels: Record<FaqCategory, string> = {
  BOOKING: '예매',
  PAYMENT: '결제',
  REFUND: '취소/환불',
  TICKET: '티켓',
  ACCOUNT: '계정',
  ETC: '기타',
};

const faqCategories: Array<{ key: '' | FaqCategory; label: string }> = [
  { key: '', label: '전체' },
  { key: 'BOOKING', label: '예매' },
  { key: 'PAYMENT', label: '결제' },
  { key: 'REFUND', label: '취소/환불' },
  { key: 'TICKET', label: '티켓' },
  { key: 'ACCOUNT', label: '계정' },
  { key: 'ETC', label: '기타' },
];

function GuideIcon({ name }: { name: string }) {
  const paths: Record<string, ReactNode> = {
    user: <><circle cx="12" cy="8" r="3.5" /><path d="M5.5 20c.5-4.1 2.7-6 6.5-6s6 1.9 6.5 6" /></>,
    ticket: <><path d="M4 7.5A2.5 2.5 0 0 0 4 12.5V17h16v-4.5a2.5 2.5 0 0 0 0-5V3H4z" /><path d="M12 5.5v2M12 11v2M12 16.5v.5" /></>,
    calendar: <><rect x="3.5" y="5" width="17" height="15" rx="2" /><path d="M8 3v4M16 3v4M3.5 9.5h17M8 13h3M8 16.5h6" /></>,
    seat: <><path d="M6 11V7.5a2 2 0 0 1 4 0V11M14 11V7.5a2 2 0 0 1 4 0V11M4 11v4a3 3 0 0 0 3 3h10a3 3 0 0 0 3-3v-4M7 18v3M17 18v3" /></>,
    coupon: <><path d="M4 6h16v4a2.5 2.5 0 0 0 0 5v4H4v-4a2.5 2.5 0 0 0 0-5z" /><path d="m9 15 6-6M9.5 9.5h.01M14.5 14.5h.01" /></>,
    box: <><path d="m4 8 8-4 8 4-8 4zM4 8v9l8 4 8-4V8M12 12v9" /></>,
    card: <><rect x="3" y="5" width="18" height="14" rx="2" /><path d="M3 9h18M7 15h4" /></>,
    check: <><circle cx="12" cy="12" r="9" /><path d="m8 12 2.7 2.7L16.5 9" /></>,
  };

  return <svg viewBox="0 0 24 24" aria-hidden="true">{paths[name]}</svg>;
}

function BookingGuide() {
  return (
    <section className="guide-panel" aria-labelledby="booking-guide-title">
      <div className="guide-panel-heading">
        <span>BOOKING GUIDE</span>
        <h3 id="booking-guide-title">Ticksy 예매 방법을 안내해 드립니다.</h3>
        <p>공연 선택부터 결제 완료까지, 아래 순서대로 진행해 주세요.</p>
      </div>
      <ol className="booking-step-list">
        {bookingSteps.map((step, index) => (
          <li key={step.title}>
            <div className="booking-step-number">{String(index + 1).padStart(2, '0')}</div>
            <div className="booking-step-icon"><GuideIcon name={step.icon} /></div>
            <div>
              <h4>{step.title}</h4>
              <p>{step.description}</p>
            </div>
          </li>
        ))}
      </ol>
      <div className="guide-callout">
        <strong>잠깐!</strong>
        <p>결제 도중 창을 닫거나 뒤로 가면 선택한 좌석이 해제될 수 있습니다. 예매가 끝날 때까지 결제 창을 유지해 주세요.</p>
      </div>
    </section>
  );
}

function CancelGuide() {
  return (
    <section className="guide-panel" aria-labelledby="cancel-guide-title">
      <div className="guide-panel-heading">
        <span>CANCEL &amp; REFUND</span>
        <h3 id="cancel-guide-title">취소 및 환불 기준을 확인해 주세요.</h3>
        <p>공연별 정책이 우선 적용되므로 예매 전 상세 안내를 꼭 확인해 주세요.</p>
      </div>

      <div className="guide-section-block">
        <h4>취소 마감 시간</h4>
        <div className="guide-table-wrap">
          <table>
            <thead><tr><th>공연 관람일</th><th>취소 마감</th></tr></thead>
            <tbody>
              <tr><td>모든 공연</td><td>관람일 전날까지</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <div className="guide-section-block">
        <h4>취소 수수료</h4>
        <div className="guide-table-wrap">
          <table>
            <thead><tr><th>취소 시점</th><th>수수료</th></tr></thead>
            <tbody>
              <tr><td>예매 후 7일 이내</td><td>없음</td></tr>
              <tr><td>관람일 9일 전 ~ 7일 전</td><td>티켓 금액의 10%</td></tr>
              <tr><td>관람일 6일 전 ~ 3일 전</td><td>티켓 금액의 20%</td></tr>
              <tr><td>관람일 2일 전 ~ 1일 전</td><td>티켓 금액의 30%</td></tr>
            </tbody>
          </table>
        </div>
        <ul className="guide-notes">
          <li>취소 마감 이후 및 관람일 당일에는 취소·변경·환불이 불가합니다.</li>
          <li>환불 완료 시점은 카드사 및 은행 영업일에 따라 달라질 수 있습니다.</li>
        </ul>
      </div>
    </section>
  );
}

function DeliveryGuide() {
  return (
    <section className="guide-panel" aria-labelledby="delivery-guide-title">
      <div className="guide-panel-heading">
        <span>TICKET DELIVERY</span>
        <h3 id="delivery-guide-title">티켓 수령 방법을 안내해 드립니다.</h3>
        <p>공연에 따라 가능한 수령 방식이 다를 수 있습니다.</p>
      </div>
      <div className="delivery-card-grid">
        <article>
          <div className="delivery-card-icon"><GuideIcon name="box" /></div>
          <h4>배송</h4>
          <p>결제 확인 후 등록된 주소로 티켓을 보내드립니다. 배송 시작일은 공연 상세에서 확인해 주세요.</p>
          <ul><li>주소와 연락처를 정확히 입력해 주세요.</li><li>발송 후 주소 변경이 제한될 수 있어요.</li></ul>
        </article>
        <article>
          <div className="delivery-card-icon"><GuideIcon name="ticket" /></div>
          <h4>현장 수령</h4>
          <p>공연 당일 매표소에서 예매자 확인 후 티켓을 받을 수 있습니다.</p>
          <ul><li>예매번호와 실물 신분증을 지참해 주세요.</li><li>할인 예매 시 증빙 서류가 필요합니다.</li></ul>
        </article>
      </div>
      <div className="guide-callout neutral">
        <strong>꼭 확인해 주세요</strong>
        <p>분실하거나 훼손된 티켓은 재발권 및 취소가 어려울 수 있으니 안전하게 보관해 주세요.</p>
      </div>
    </section>
  );
}

export default function CustomerServicePage({
  activeTab,
  activeGuideTab,
  onTabChange,
  onGuideTabChange,
}: Props) {
  const noticeIdFromUrl = readNoticeId();
  const [notices, setNotices] = useState<Notice[]>([]);
  const [noticeLoading, setNoticeLoading] = useState(false);
  const [noticeError, setNoticeError] = useState('');
  const [selectedNoticeId, setSelectedNoticeId] = useState<number | null>(noticeIdFromUrl);
  const [selectedNotice, setSelectedNotice] = useState<NoticeDetail | null>(null);
  const [noticeDetailLoading, setNoticeDetailLoading] = useState(false);
  const [noticeDetailError, setNoticeDetailError] = useState('');
  const [faqs, setFaqs] = useState<Faq[]>([]);
  const [faqCategory, setFaqCategory] = useState<'' | FaqCategory>('');
  const [faqSearchInput, setFaqSearchInput] = useState('');
  const [faqKeyword, setFaqKeyword] = useState('');
  const [faqLoading, setFaqLoading] = useState(false);
  const [faqError, setFaqError] = useState('');
  const [faqTotalCount, setFaqTotalCount] = useState(0);

  useEffect(() => {
    if (activeTab !== 'notice' || selectedNoticeId !== null) return;

    const controller = new AbortController();
    setNoticeLoading(true);
    setNoticeError('');

    fetch('/support/api/v1/notice/select?page=0&size=100', { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) throw new Error('공지사항을 불러오지 못했습니다.');
        return response.json() as Promise<NoticePageResponse>;
      })
      .then((response) => setNotices(response.content ?? []))
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return;
        setNoticeError('공지사항을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
      })
      .finally(() => {
        if (!controller.signal.aborted) setNoticeLoading(false);
      });

    return () => controller.abort();
  }, [activeTab, selectedNoticeId]);

  useEffect(() => {
    const syncNoticeId = () => {
      setSelectedNoticeId(activeTab === 'notice' ? readNoticeId() : null);
    };
    syncNoticeId();
    window.addEventListener('popstate', syncNoticeId);
    return () => window.removeEventListener('popstate', syncNoticeId);
  }, [activeTab, noticeIdFromUrl]);

  useEffect(() => {
    if (activeTab !== 'notice' || selectedNoticeId === null) {
      setSelectedNotice(null);
      setNoticeDetailError('');
      return;
    }

    const controller = new AbortController();
    setNoticeDetailLoading(true);
    setNoticeDetailError('');

    fetch(`/support/api/v1/notice/select/id/${selectedNoticeId}`, { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) throw new Error('공지사항 상세 내용을 불러오지 못했습니다.');
        return response.json() as Promise<NoticeDetail>;
      })
      .then(setSelectedNotice)
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return;
        setSelectedNotice(null);
        setNoticeDetailError('공지사항 상세 내용을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
      })
      .finally(() => {
        if (!controller.signal.aborted) setNoticeDetailLoading(false);
      });

    return () => controller.abort();
  }, [activeTab, selectedNoticeId]);

  function openNoticeDetail(noticeId: number) {
    const url = new URL(window.location.href);
    url.searchParams.set('noticeId', String(noticeId));
    window.history.pushState(
      { ...window.history.state, noticeId },
      '',
      `${url.pathname}${url.search}${url.hash}`,
    );
    setSelectedNoticeId(noticeId);
    window.scrollTo(0, 0);
  }

  function closeNoticeDetail() {
    const url = new URL(window.location.href);
    url.searchParams.delete('noticeId');
    window.history.pushState(
      { ...window.history.state, noticeId: null },
      '',
      `${url.pathname}${url.search}${url.hash}`,
    );
    setSelectedNoticeId(null);
    window.scrollTo(0, 0);
  }

  useEffect(() => {
    if (activeTab !== 'faq') return;

    const controller = new AbortController();
    const params = new URLSearchParams({ page: '0', size: '100' });
    if (faqCategory) params.set('category', faqCategory);
    if (faqKeyword) params.set('keyword', faqKeyword);

    setFaqLoading(true);
    setFaqError('');

    fetch(`/support/api/v1/faq/select?${params.toString()}`, { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) throw new Error('FAQ를 불러오지 못했습니다.');
        return response.json() as Promise<FaqPageResponse>;
      })
      .then((response) => {
        setFaqs(response.content ?? []);
        setFaqTotalCount(Number(response.page?.totalElements ?? response.content?.length ?? 0));
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return;
        setFaqError('FAQ를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
      })
      .finally(() => {
        if (!controller.signal.aborted) setFaqLoading(false);
      });

    return () => controller.abort();
  }, [activeTab, faqCategory, faqKeyword]);

  function submitFaqSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFaqKeyword(faqSearchInput.trim());
  }

  function resetFaqSearch() {
    setFaqSearchInput('');
    setFaqKeyword('');
  }

  return (
    <main className="customer-service-page">
      <div className="customer-service-breadcrumb" aria-label="현재 위치">
        <span>홈</span><span aria-hidden="true">›</span><strong>고객센터</strong>
      </div>
      <header className="customer-service-header">
        <p>HELP CENTER</p>
        <h1>고객센터</h1>
        <span>Ticksy 이용에 필요한 안내를 한곳에서 확인하세요.</span>
      </header>

      <nav className="customer-service-tabs" aria-label="고객센터 메뉴">
        {serviceTabs.map((tab) => (
          <button
            key={tab.key}
            className={activeTab === tab.key ? 'active' : ''}
            type="button"
            aria-current={activeTab === tab.key ? 'page' : undefined}
            onClick={() => onTabChange(tab.key)}
          >
            {tab.label}
          </button>
        ))}
      </nav>

      {activeTab === 'notice' && selectedNoticeId === null && (
        <section className="service-simple-panel">
          <div className="service-panel-title"><h2>공지사항</h2><p>Ticksy의 새로운 소식과 서비스 안내입니다.</p></div>
          <div className="notice-list" aria-busy={noticeLoading}>
            {noticeLoading && <p className="notice-list-status">공지사항을 불러오고 있습니다.</p>}
            {!noticeLoading && noticeError && <p className="notice-list-status error">{noticeError}</p>}
            {!noticeLoading && !noticeError && notices.length === 0 && (
              <p className="notice-list-status">등록된 공지사항이 없습니다.</p>
            )}
            {!noticeLoading && !noticeError && notices.map((notice) => (
              <button
                className="notice-list-row"
                key={notice.noticeId}
                type="button"
                onClick={() => openNoticeDetail(notice.noticeId)}
              >
                <h3>[ {noticeCategoryLabels[notice.category] ?? notice.category} ] {notice.title}</h3>
                <time>{formatNoticeDate(notice.publishedAt)}</time>
                <b aria-hidden="true">›</b>
              </button>
            ))}
          </div>
        </section>
      )}

      {activeTab === 'notice' && selectedNoticeId !== null && (
        <section className="service-simple-panel notice-detail">
          <div className="service-panel-title"><h2>공지사항</h2><p>Ticksy의 새로운 소식과 서비스 안내입니다.</p></div>
          <div className="notice-detail-card" aria-busy={noticeDetailLoading}>
            {noticeDetailLoading && <p className="notice-detail-status">공지사항을 불러오고 있습니다.</p>}
            {!noticeDetailLoading && noticeDetailError && (
              <p className="notice-detail-status error">{noticeDetailError}</p>
            )}
            {!noticeDetailLoading && !noticeDetailError && selectedNotice && (
              <>
                <header className="notice-detail-header">
                  <span>{selectedNotice.noticeId}</span>
                  <h3>[ {noticeCategoryLabels[selectedNotice.category] ?? selectedNotice.category} ] {selectedNotice.title}</h3>
                  <time>{formatNoticeDate(selectedNotice.publishedAt)}</time>
                </header>
                <div className="notice-detail-content">{selectedNotice.content}</div>
              </>
            )}
          </div>
          <div className="notice-detail-actions">
            <button type="button" onClick={closeNoticeDetail}>목록</button>
          </div>
        </section>
      )}

      {activeTab === 'guide' && (
        <section className="service-guide-area">
          <nav className="guide-tabs" aria-label="이용안내 세부 메뉴">
            {guideTabs.map((tab) => (
              <button
                key={tab.key}
                className={activeGuideTab === tab.key ? 'active' : ''}
                type="button"
                aria-pressed={activeGuideTab === tab.key}
                onClick={() => onGuideTabChange(tab.key)}
              >
                {tab.label}
              </button>
            ))}
          </nav>
          {activeGuideTab === 'booking' && <BookingGuide />}
          {activeGuideTab === 'cancel' && <CancelGuide />}
          {activeGuideTab === 'delivery' && <DeliveryGuide />}
        </section>
      )}

      {activeTab === 'faq' && (
        <section className="service-simple-panel faq-service-panel">
          <div className="service-panel-title"><h2>자주 묻는 질문</h2><p>궁금한 내용을 빠르게 확인해 보세요.</p></div>

          <form className="faq-search-box" onSubmit={submitFaqSearch}>
            <label htmlFor="faq-search-input">자주 묻는 질문 검색</label>
            <div>
              <input
                id="faq-search-input"
                type="search"
                value={faqSearchInput}
                placeholder="궁금한 내용을 입력해 주세요."
                onChange={(event) => setFaqSearchInput(event.target.value)}
              />
              {faqKeyword && <button className="faq-search-reset" type="button" onClick={resetFaqSearch}>초기화</button>}
              <button className="faq-search-submit" type="submit" aria-label="FAQ 검색">검색</button>
            </div>
          </form>

          <nav className="faq-category-tabs" aria-label="FAQ 분류">
            {faqCategories.map((category) => (
              <button
                key={category.key || 'ALL'}
                className={faqCategory === category.key ? 'active' : ''}
                type="button"
                aria-pressed={faqCategory === category.key}
                onClick={() => setFaqCategory(category.key)}
              >
                {category.label}
              </button>
            ))}
          </nav>

          <div className="faq-result-heading">
            <h3>{faqCategory ? `${faqCategoryLabels[faqCategory]} FAQ` : '자주 묻는 질문'}</h3>
            {!faqLoading && !faqError && <span>총 {faqTotalCount}건</span>}
          </div>

          <div className="faq-list" aria-busy={faqLoading}>
            {faqLoading && <p className="faq-list-status">FAQ를 불러오고 있습니다.</p>}
            {!faqLoading && faqError && <p className="faq-list-status error">{faqError}</p>}
            {!faqLoading && !faqError && faqs.length === 0 && (
              <p className="faq-list-status">조건에 맞는 FAQ가 없습니다.</p>
            )}
            {!faqLoading && !faqError && faqs.map((faq, index) => (
              <details key={faq.faqId}>
                <summary>
                  <span className="faq-number">{index + 1}</span>
                  <span className="faq-category">{faqCategoryLabels[faq.category] ?? faq.category}</span>
                  <strong>{faq.question}</strong>
                  <b aria-hidden="true">+</b>
                </summary>
                <div className="faq-answer"><span>A</span><p>{faq.answer}</p></div>
              </details>
            ))}
          </div>
        </section>
      )}

      {activeTab === 'inquiry' && (
        <section className="service-simple-panel inquiry-empty">
          <div className="inquiry-empty-icon" aria-hidden="true">?</div>
          <h2>나의 문의 내역</h2>
          <p>로그인 후 문의 내역과 답변 상태를 확인할 수 있습니다.</p>
          <button type="button">1:1 문의 안내</button>
        </section>
      )}
    </main>
  );
}
