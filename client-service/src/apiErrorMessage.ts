const STATUS_MESSAGES: Record<number, string> = {
  400: '입력한 정보를 확인한 후 다시 시도해주세요.',
  401: '로그인 정보가 만료되었거나 확인되지 않습니다. 다시 로그인해주세요.',
  403: '처리 도중 문제가 발생했습니다. 잠시 후 다시 시도해주세요.',
  404: '요청하신 정보를 찾을 수 없습니다. 화면을 새로고침한 후 다시 확인해주세요.',
  405: '요청을 처리할 수 없습니다. 화면을 새로고침한 후 다시 시도해주세요.',
  408: '응답이 지연되고 있습니다. 잠시 후 다시 시도해주세요.',
  409: '요청한 정보의 상태가 변경되었습니다. 현재 상태를 확인한 후 다시 시도해주세요.',
  410: '요청하신 정보가 만료되었거나 더 이상 제공되지 않습니다.',
  422: '입력한 정보를 확인한 후 다시 시도해주세요.',
  429: '요청이 많아 잠시 이용이 제한되었습니다. 잠시 후 다시 시도해주세요.',
};

const SERVER_ERROR_MESSAGE = '일시적인 서비스 오류로 요청을 완료하지 못했습니다. 잠시 후 다시 시도해주세요.';
const CODE_MESSAGES: Record<string, string> = {
  LOGIN_FAILED: '아이디 또는 비밀번호가 일치하지 않습니다.',
  USER_WITHDRAWN: '이미 탈퇴한 사용자입니다.',
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
  RESERVATION_PROCESSING: '해당 예매에 대한 처리가 진행 중입니다. 잠시 후 다시 시도해 주세요.',
  GATEWAY_REFUND_REJECTED: '환불 요청이 거절되었습니다. 신청 내용을 확인하거나 기존 신청을 철회해주세요.',
  REFUND_REQUEST_CONFLICT: '진행 중인 환불 신청과 내용이 다릅니다. 기존 신청을 확인하거나 철회한 후 다시 신청해주세요.',
  GATEWAY_REFUND_UNCONFIRMED: '환불 결과를 확인하지 못했습니다. 같은 신청으로 다시 시도해주세요.',
  GATEWAY_CARD_PREPARATION_UNCONFIRMED: '카드 결제 준비 결과를 확인하지 못했습니다. 같은 결제 요청으로 다시 시도해주세요.',
  GATEWAY_METHOD_CHANGE_UNCONFIRMED: '기존 카드 승인 또는 결제수단 변경 결과를 확인하지 못했습니다. 같은 요청으로 다시 시도해주세요.',
  GATEWAY_PAYMENT_METHOD_CHANGED: '결제수단이 변경되었습니다. 예매 내역에서 현재 결제 정보를 확인해주세요.',
  GATEWAY_ACCOUNT_ISSUE_UNCONFIRMED: '가상계좌 발급 결과를 확인하지 못했습니다. 같은 결제 요청으로 다시 시도하거나 예매 내역을 확인해주세요.',
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

  // 서버의 원문 오류는 표시하지 않고 발급 결과 미확인 코드에만 고정 안내를 허용한다.
  if (status >= 500) {
    return status === 503 && (code === 'GATEWAY_ACCOUNT_ISSUE_UNCONFIRMED' || code === 'GATEWAY_REFUND_UNCONFIRMED' || code === 'GATEWAY_CARD_PREPARATION_UNCONFIRMED' || code === 'GATEWAY_METHOD_CHANGE_UNCONFIRMED')
      ? CODE_MESSAGES[code]
      : fallback;
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
