export type SearchQuery = {
  keyword: string;
  field: 'ALL' | 'TITLE' | 'ARTIST' | 'VENUE';
  page: number;
};

export function readSearch(): SearchQuery {
  const params = new URLSearchParams(window.location.search);
  const field = params.get('field');
  const page = Number(params.get('searchPage'));
  return {
    keyword: (params.get('keyword') || '').trim().slice(0, 100),
    field: field === 'TITLE' || field === 'ARTIST' || field === 'VENUE' ? field : 'ALL',
    page: Number.isSafeInteger(page) && page >= 0 ? page : 0,
  };
}

