export type LogItem = {
  id: number;
  eventId: number;
  userId: string;
  detectionType: string;
  occurredAt: string;
};

export type LogDetail = LogItem & {
  traceId: string | null;
  sessionKeyHash: string;
  evidence: Record<string, unknown>;
};

export type LogPage = {
  content: LogItem[];
  page: { number: number; totalElements: number; totalPages: number };
};

export const detectionLogEndpoint = '/audit/api/v1/ticketing-detection-log/select';

export const detectionLabels: Record<string, string> = {
  AREA_LAYOUT_ACTIVE_TOKEN_MISSING: '배치도 요청에 Active Token 없음',
  AREA_LAYOUT_REFRESH_LIMIT_REACHED: '배치도 새로고침 한도 도달',
  SEAT_OCCUPATION_CAPTCHA_NOT_PASSED: '캡차 미통과 상태에서 좌석 선점 요청',
  PREPARE_TOO_FAST: '결제 준비 시간 기준 이하',
  CAPTCHA_ATTEMPT_LIMIT_REACHED: '캡차 오답 한도 도달',
};
