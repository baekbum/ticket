const STATUS_MESSAGES: Record<number, string> = {
  400: '입력한 정보를 확인한 후 다시 시도해주세요.',
  401: '로그인 정보가 만료되었거나 확인되지 않습니다. 다시 로그인해주세요.',
  403: '요청을 진행할 수 없습니다. 로그인 상태를 확인한 후 다시 시도해주세요.',
  404: '요청하신 정보를 찾을 수 없습니다. 화면을 새로고침한 후 다시 확인해주세요.',
  405: '요청을 처리할 수 없습니다. 화면을 새로고침한 후 다시 시도해주세요.',
  408: '응답이 지연되고 있습니다. 잠시 후 다시 시도해주세요.',
  409: '요청한 정보의 상태가 변경되었습니다. 현재 상태를 확인한 후 다시 시도해주세요.',
  410: '요청하신 정보가 만료되었거나 더 이상 제공되지 않습니다.',
  422: '입력한 정보를 확인한 후 다시 시도해주세요.',
  429: '대기열을 통과한 사용자만 티켓팅을 진행할 수 있습니다.',
};

const SERVER_ERROR_MESSAGE = '일시적인 서비스 오류로 요청을 완료하지 못했습니다. 잠시 후 다시 시도해주세요.';
const CODE_MESSAGES: Record<string, string> = {
  LOGIN_FAILED: '아이디 또는 비밀번호가 일치하지 않습니다.',
  TOKEN_EXPIRED: STATUS_MESSAGES[401],
  INVALID_TOKEN: STATUS_MESSAGES[401],
  REFRESH_TOKEN_REQUIRED: STATUS_MESSAGES[401],
  REFRESH_TOKEN_INVALID: STATUS_MESSAGES[401],
  REFRESH_TOKEN_MISMATCH: STATUS_MESSAGES[401],
  UNAUTHORIZED: STATUS_MESSAGES[401],
  FORBIDDEN: STATUS_MESSAGES[403],
  INVALID_REQUEST: STATUS_MESSAGES[400],
  VALIDATION_FAILED: STATUS_MESSAGES[400],
  REQUIRED_HEADER_MISSING: '요청에 필요한 정보가 누락되었습니다. 화면을 새로고침한 후 다시 시도해주세요.',
  QUEUE_TOKEN_INVALID: '예매 대기 정보가 만료되었거나 유효하지 않습니다. 예매를 다시 시작해주세요.',
  QUEUE_ACCESS_DENIED: '현재 예매에 입장할 수 없습니다. 예매 화면을 닫고 다시 입장해주세요.',
  SEAT_CACHE_NOT_FOUND: '좌석 선택 정보가 만료되었습니다. 좌석을 다시 선택해주세요.',
  SEAT_ALREADY_OCCUPIED: '이미 선택되었거나 예매 완료된 좌석입니다. 다른 좌석을 선택해주세요.',
  SEAT_OCCUPATION_FAILED: '좌석을 확보하지 못했습니다. 좌석 상태를 확인한 후 다시 선택해주세요.',
  TICKET_LIMIT_EXCEEDED: '예매 가능한 수량을 초과했습니다. 선택한 좌석 수를 확인해주세요.',
  REDIS_ERROR: SERVER_ERROR_MESSAGE,
  FEIGN_CLIENT_ERROR: SERVER_ERROR_MESSAGE,
  INTERNAL_SERVER_ERROR: SERVER_ERROR_MESSAGE,
};

export function getApiErrorMessage(status: number, responseText: string): string {
  const fallback = status >= 500
    ? SERVER_ERROR_MESSAGE
    : STATUS_MESSAGES[status] || '요청을 완료하지 못했습니다. 잠시 후 다시 시도해주세요.';
  // 서버 장애 응답에는 내부 예외나 프록시 HTML이 포함될 수 있다.
  if (status >= 500) return fallback;

  let message: unknown = responseText.trim();
  let code: unknown;
  try {
    const body: unknown = JSON.parse(responseText);
    if (body && typeof body === 'object' && !Array.isArray(body)) {
      const error = body as Record<string, unknown>;
      code = error.code;
      message = error.message || error.error;
    } else {
      message = typeof body === 'string' ? body : undefined;
    }
  } catch {
    // 일반 텍스트 응답도 아래에서 사용자 안내 문구인지 확인한다.
  }

  if (typeof code === 'string' && code !== 'INVALID_REQUEST' && code !== 'VALIDATION_FAILED'
      && Object.hasOwn(CODE_MESSAGES, code)) return CODE_MESSAGES[code];
  if (typeof message !== 'string') return fallback;
  const trimmed = message.trim();
  if (Object.hasOwn(CODE_MESSAGES, trimmed)) return CODE_MESSAGES[trimmed];
  // 짧은 한국어 업무 오류는 유지하고 HTTP 코드, HTML, 예외 상세는 안내로 대체한다.
  if (trimmed.length <= 250 && /[가-힣]/.test(trimmed)
      && !/[<>{}\r\n]|Exception|stack\s?trace|SQL|JDBC|https?:\/\/|요청 실패\s*:?\s*\d{3}/i.test(trimmed)) {
    return trimmed;
  }
  return fallback;
}
