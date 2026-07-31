import './App.css';

const serviceLinks = [
  { label: '공연 목록', path: '/events' },
  { label: '예매 내역', path: '/reservations' },
  { label: '마이페이지', path: '/my' },
];

function App() {
  return (
    <main className="app">
      <section className="hero">
        <p className="eyebrow">Ticket Client</p>
        <h1>사용자 예매 서비스를 시작합니다.</h1>
        <p className="description">
          React 기반 클라이언트 서비스 초기 프레임입니다. 공연 조회, 좌석 선택,
          예매 요청 플로우를 이곳에서 확장합니다.
        </p>
        <div className="actions">
          <a href="/events">공연 둘러보기</a>
          <a className="secondary" href="/login">
            로그인
          </a>
        </div>
      </section>

      <section className="cards" aria-label="주요 메뉴">
        {serviceLinks.map((link) => (
          <a className="card" href={link.path} key={link.path}>
            <span>{link.label}</span>
            <strong>{link.path}</strong>
          </a>
        ))}
      </section>
    </main>
  );
}

export default App;
