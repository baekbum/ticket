# 관리자 화면

관리자 로그인과 메뉴는 React로 제공합니다. 기존 관리 화면 조각은 `legacy-source`에 보관하고 빌드 시 정적 페이지로 변환합니다. 각 화면의 API 요청은 `scripts/bridge.js`에서 서비스별 ingress 경로로 변경합니다. 기존 화면 조각을 React 컴포넌트로 교체하는 동안에도 admin-service의 Feign 중계는 사용하지 않습니다.

## 로컬 실행

auth, user, ticket, queue, audit, support 및 admin-service의 운영 API를 호스트에서 실행하고, 로컬 ingress를 시작합니다.

```bash
cd admin-client-service
npm ci
npm run dev
```

관리자 화면: `http://localhost/admin/` (Vite 직접 접속: `http://localhost:8999/admin/`). 기존 admin-service 운영 API는 8998번 포트에서 실행됩니다.

`/auth/`, `/user/`, `/ticket/`, `/queue/`, `/audit/`, `/support/`는 각 서비스에 연결됩니다. `/admin-api/`만 admin-service의 DLQ·모니터링 API에 연결됩니다.

## 운영 빌드

`infra/infra-ingress/Dockerfile`이 사용자와 관리자 화면을 함께 빌드합니다. 관리자 정적 파일은 ingress의 `/admin/`에서 제공됩니다. 각 Spring 서비스는 동일한 JWT 비밀 키를 사용해야 합니다.
