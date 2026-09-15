import './CustomerServicePage.css';
import type { ReactNode } from 'react';

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

const notices = [
  { category: '안내', title: 'Ticksy 고객센터 이용 안내', date: '2026.09.01' },
  { category: '예매', title: '안전한 티켓 예매를 위한 유의사항', date: '2026.08.25' },
  { category: '결제', title: '무통장입금 결제 및 입금 기한 안내', date: '2026.08.18' },
];

const faqItems = [
  ['선택한 좌석은 언제까지 유지되나요?', '좌석 선택이 완료된 시점부터 10분 동안 유지됩니다. 시간 안에 결제를 완료하지 않으면 좌석이 자동으로 해제됩니다.'],
  ['예매 내역은 어디에서 확인하나요?', '상단의 마이티켓 메뉴에서 예매 내역과 결제 상태를 확인할 수 있습니다.'],
  ['무통장입금은 언제까지 해야 하나요?', '예매 완료 화면과 마이티켓에 표시된 입금 기한까지 입금해야 하며, 기한이 지나면 예매가 자동 취소될 수 있습니다.'],
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
              <tr><td>화요일 ~ 토요일</td><td>관람일 전일 오후 5시</td></tr>
              <tr><td>일요일 ~ 월요일</td><td>토요일 오전 11시</td></tr>
              <tr><td>공휴일 및 공휴일 다음 날</td><td>공휴일 전 평일 오후 5시</td></tr>
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
          <li>예매 당일 자정 이후에는 예매 수수료가 환불되지 않을 수 있습니다.</li>
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

      {activeTab === 'notice' && (
        <section className="service-simple-panel">
          <div className="service-panel-title"><h2>공지사항</h2><p>Ticksy의 새로운 소식과 서비스 안내입니다.</p></div>
          <div className="notice-list">
            {notices.map((notice) => (
              <article key={notice.title}>
                <span>{notice.category}</span><h3>{notice.title}</h3><time>{notice.date}</time><b aria-hidden="true">›</b>
              </article>
            ))}
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
        <section className="service-simple-panel">
          <div className="service-panel-title"><h2>자주 묻는 질문</h2><p>궁금한 내용을 빠르게 확인해 보세요.</p></div>
          <div className="faq-list">
            {faqItems.map(([question, answer]) => (
              <details key={question}><summary><span>Q</span>{question}<b aria-hidden="true">+</b></summary><p>{answer}</p></details>
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
