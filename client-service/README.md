# client-service

React 기반 사용자 클라이언트 서비스입니다.

## 로컬 실행

```bash
npm install
npm run dev
```

- 개발 서버: `http://localhost:3000`
- API 프록시
  - `/auth/*` → `http://localhost:8080`
  - `/user/*` → `http://localhost:8081`
  - `/ticket/*` → `http://localhost:8082`
  - `/queue/*` → `http://localhost:8083`

## 빌드

```bash
npm run build
npm run preview
```

## Docker

```bash
docker build -t client-service ./client-service
docker run --rm -p 3000:3000 client-service
```
