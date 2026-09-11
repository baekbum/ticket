// common의 CardCompany와 동일한 코드이며 화면에는 한글 카드사명을 표시한다.
export const CARD_COMPANIES = [
  { code: 'SHINHAN', name: '신한카드' },
  { code: 'KB', name: '국민카드' },
  { code: 'SAMSUNG', name: '삼성카드' },
  { code: 'HYUNDAI', name: '현대카드' },
  { code: 'LOTTE', name: '롯데카드' },
  { code: 'KAKAO', name: '카카오카드' },
  { code: 'TOSS', name: '토스카드' },
  { code: 'NH', name: '농협카드' },
  { code: 'WOORI', name: '우리카드' },
  { code: 'HANA', name: '하나카드' },
  { code: 'BC', name: '비씨카드' },
  { code: 'IBK', name: '기업카드' },
  { code: 'CITI', name: '씨티카드' },
  { code: 'SC', name: '에스씨제일카드' },
  { code: 'SUHYUP', name: '수협카드' },
  { code: 'K_BANK', name: '케이뱅크카드' },
] as const;

export type CardCompany = typeof CARD_COMPANIES[number]['code'];

// 마지막으로 지정한 주요 9개 카드사. 전체 표시명은 위 목록 한 곳에서 관리한다.
export const PRIMARY_CARD_CODES: readonly CardCompany[] = [
  'SHINHAN', 'KB', 'SAMSUNG', 'HYUNDAI', 'LOTTE', 'KAKAO', 'TOSS', 'NH', 'WOORI',
];
export const PRIMARY_CARD_COMPANIES = PRIMARY_CARD_CODES.map((code) => CARD_COMPANIES.find((card) => card.code === code)!);
export const OTHER_CARD_COMPANIES = CARD_COMPANIES.filter((card) => !PRIMARY_CARD_CODES.includes(card.code));

export type CardPaymentStep = 1 | 2 | 3;
export type CardPaymentForm = {
  company: CardCompany | '';
  cardNumber: [string, string, string, string];
  cvc: string;
  password: string;
};
export function emptyCardPaymentForm(): CardPaymentForm {
  return { company: '', cardNumber: ['', '', '', ''], cvc: '', password: '' };
}

type CardPaymentAction =
  | { type: 'company'; value: CardCompany | '' }
  | { type: 'number'; index: number; value: string }
  | { type: 'pasteNumber'; index: number; value: string }
  | { type: 'cvc'; value: string }
  | { type: 'password'; value: string }
  | { type: 'reset' };

export function cardPaymentFormReducer(form: CardPaymentForm, action: CardPaymentAction): CardPaymentForm {
  if (action.type === 'reset') return emptyCardPaymentForm();
  if (action.type === 'company') return { ...form, company: action.value };
  if (action.type === 'cvc') return { ...form, cvc: action.value.replace(/\D/g, '').slice(0, 3) };
  if (action.type === 'password') return { ...form, password: action.value };
  const cardNumber: CardPaymentForm['cardNumber'] = [...form.cardNumber];
  const digits = action.value.replace(/\D/g, '');
  if (action.type === 'number') {
    cardNumber[action.index] = digits.slice(0, 4);
  } else {
    const start = digits.length >= 16 ? 0 : action.index;
    for (let index = start; index < 4 && (index - start) * 4 < digits.length; index++) {
      cardNumber[index] = digits.slice((index - start) * 4, (index - start + 1) * 4);
    }
  }
  return { ...form, cardNumber };
}

export function isCardStepValid(step: CardPaymentStep, form: CardPaymentForm) {
  if (!CARD_COMPANIES.some((card) => card.code === form.company)) return false;
  if (step >= 2 && (!form.cardNumber.every((part) => /^\d{4}$/.test(part)) || !/^\d{3}$/.test(form.cvc))) return false;
  return step < 3 || (form.password.trim().length > 0 && form.password.length <= 64);
}

export function buildCardApprovalRequest(form: CardPaymentForm, payment: { paymentNo: string; amount: number }) {
  if (!isCardStepValid(3, form)) throw new Error('카드 정보와 온라인 결제 비밀번호를 확인해주세요.');
  return { paymentNo: payment.paymentNo, amount: payment.amount, cardCompany: form.company,
    cardNumber: form.cardNumber.join(''), cvc: form.cvc, cardPassword: form.password };
}
export type CardApprovalResponse = {
  paymentNo: string;
  approved?: boolean;
  status?: string;
  transactionId?: string;
  cardCompany?: CardCompany;
  maskedCardNumber?: string;
};

export function cardResult(result: CardApprovalResponse): 'COMPLETE' | 'TERMINAL' | 'PENDING' {
  if (result.status === 'PENDING' || result.status === 'APPROVED' || result.status === 'TICKET_PAYMENT_FAILED') return 'PENDING';
  if (['CANCELLED', 'APPROVAL_FAILED', 'REFUNDED', 'PARTIALLY_REFUNDED'].includes(result.status || '')) return 'TERMINAL';
  if (result.status === 'TICKET_PAYMENT_COMPLETED' || (result.approved === true && !result.status)) return 'COMPLETE';
  return 'PENDING';
}

export const INVALID_CARD_INFORMATION_MESSAGE = '정보가 올바르지 않습니다.';

/** HTTP 400은 카드 승인 거부가 확정된 응답이므로 승인 이력을 조회하지 않는다. */
export function shouldCheckCardApprovalStatus(error: unknown) {
  return !(error instanceof Error && 'status' in error && error.status === 400);
}

export function formatCardNumber(value: string) {
  return value.replace(/\D/g, '').slice(0, 16).replace(/(.{4})(?=.)/g, '$1-');
}
