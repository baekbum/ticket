import type { SearchQuery } from './searchQuery';
import { useEffect, useState } from 'react';
import './SearchResultsPage.css';
import { ticketAssetUrl } from './ticketAssetUrl';

type SearchResult = {
  eventGroupCode: string;
  title: string;
  artistName: string;
  venue: string;
  posterUrl: string;
  eventStartDate: string;
  eventEndDate: string;
};

type Results = {
  content: SearchResult[];
  page: { totalElements: number; totalPages: number };
};

export default function SearchResultsPage({ query, onSearch, onSelectEvent }: {
  query: SearchQuery;
  onSearch: (query: SearchQuery) => void;
  onSelectEvent: (code: string) => void;
}) {
  const [results, setResults] = useState<Results | null>(null);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setResults(null);
    setError('');
    if (!query.keyword) return () => controller.abort();
    const params = new URLSearchParams({
      keyword: query.keyword, field: query.field, page: String(query.page), size: '10',
    });
    fetch('/ticket/api/v1/event/search?' + params, { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) throw new Error('검색 결과를 불러오지 못했습니다. 다시 시도해 주세요.');
        return response.json() as Promise<Results>;
      })
      .then((data) => { if (!controller.signal.aborted) setResults(data); })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : '검색 중 오류가 발생했습니다.');
      });
    return () => controller.abort();
  }, [query.keyword, query.field, query.page, retry]);

  return (
    <section className="search-results" aria-labelledby="search-results-title">
      <h1 id="search-results-title"><span>{query.keyword}</span> 검색 결과</h1>
      <div aria-live="polite">
        {!query.keyword ? <p className="search-message">검색어를 입력해 주세요.</p> : error ? (
          <div className="search-message" role="alert">{error} <button type="button" onClick={() => setRetry(retry + 1)}>다시 시도</button></div>
        ) : !results ? <p className="search-message">검색 중입니다…</p> : (
          <>
            <p className="search-count">총 <strong>{results.page.totalElements.toLocaleString()}</strong>건</p>
            {results.content.length === 0 ? <p className="search-message">검색 결과가 없습니다. 다른 검색어나 검색 조건으로 다시 검색해 주세요.</p> : (
              <ul className="search-result-list">
                {results.content.map((event) => (
                  <li key={event.eventGroupCode}>
                    <button className="search-result-row" type="button" onClick={() => onSelectEvent(event.eventGroupCode)}>
                      {event.posterUrl ? <img src={ticketAssetUrl(event.posterUrl)} alt="" /> : <span className="search-poster-placeholder">Ticksy</span>}
                      <span className="search-result-info"><strong>{event.title}</strong><span>{event.artistName}</span></span>
                      <span className="search-result-date">{event.eventStartDate.replaceAll('-', '.')}<br />~ {event.eventEndDate.replaceAll('-', '.')}</span>
                      <span className="search-result-venue">{event.venue}</span>
                      <span className="search-result-arrow" aria-hidden="true">↗</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
            {results.page.totalPages > 1 && (
              <nav className="search-pagination" aria-label="검색 결과 페이지">
                <button type="button" disabled={query.page === 0} onClick={() => onSearch({ ...query, page: query.page - 1 })}>이전</button>
                <span>{query.page + 1} / {results.page.totalPages}</span>
                <button type="button" disabled={query.page + 1 >= results.page.totalPages} onClick={() => onSearch({ ...query, page: query.page + 1 })}>다음</button>
              </nav>
            )}
          </>
        )}
      </div>
    </section>
  );
}
