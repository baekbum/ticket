const TICKET_POSTER_PREFIX = '/ticket/uploads/events/posters/';
const CLIENT_POSTER_PREFIX = '/client-api/api/v1/event/posters/';

export function ticketAssetUrl(url: string): string {
  if (!url.startsWith(TICKET_POSTER_PREFIX)) return url;

  return CLIENT_POSTER_PREFIX + url
    .slice(TICKET_POSTER_PREFIX.length)
    .split('/')
    .map(encodeURIComponent)
    .join('/');
}
