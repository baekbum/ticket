# Ingress

브라우저는 `http://localhost`의 ingress에 요청합니다. ingress는 URL의 서비스 접두사로 목적지를 정하고, `Authorization` 헤더를 해당 서비스에 전달합니다. 인증·권한 검사는 각 서비스가 담당합니다.

로컬과 운영에서 브라우저가 사용하는 API URL 및 Spring Boot 서비스에 전달되는 경로는 같습니다. API 라우팅에서 달라지는 부분은 ingress가 서비스를 찾는 주소입니다.

| 환경 | Compose 파일 | 백엔드 주소 | `/ticket/api/v1/...` 전달 경로 |
| --- | --- | --- | --- |
| 로컬 | `docker-compose-local.yml` | `host.docker.internal:8082` | `/api/v1/...` |
| 운영 | `docker-compose.yml` | `ticket-service:8080` | `/api/v1/...` |

## 로컬

호스트에서 auth-service(8080), user-service(8081), ticket-service(8082), queue-service(8083), audit-service(8084), support-service(8085), admin-service 운영 API(8998), payment-gateway-service(8099), client-service Vite(3000), admin-client-service Vite(8999)를 실행한 후:

```bash
docker compose -f infra/infra-ingress/docker-compose-local.yml up -d
```

`http://localhost/`는 사용자 Vite 서버로, `http://localhost/admin/`은 관리자 Vite 서버로 전달됩니다. `/auth/`, `/user/`, `/ticket/`, `/queue/`, `/audit/`, `/support/`, `/payment-gateway/`는 호스트의 Spring Boot 서비스로 전달됩니다. `/admin-api/`는 DLQ와 모니터링을 처리하는 admin-service로 전달됩니다. `host.docker.internal`은 컨테이너에서 호스트 PC를 가리킵니다. `/client-api/`는 로컬 client-api-service(8090)로 전달됩니다.

현재 client-service 화면은 아직 `/client-api/`를 호출하므로 화면 전체를 사용하려면 기존 client-api-service(8090)도 호스트에서 실행해야 합니다.

## 운영

서비스 컨테이너가 `ticket-network`에 연결된 상태에서:

```bash
docker network create ticket-network
docker compose -f infra/infra-ingress/docker-compose.yml up -d --build
```

운영 ingress 이미지는 사용자와 관리자 React 정적 파일을 빌드해 포함합니다. 별도의 화면 컨테이너는 실행하지 않습니다. 화면 코드를 바꾼 후에는 ingress 이미지를 다시 빌드해야 합니다. API는 Docker 서비스 이름으로 연결합니다.

`/auth/`, `/user/`, `/ticket/`, `/queue/`, `/support/`, `/payment-gateway/`는 각 서비스로 접두사 단위로 전달합니다. ingress는 토큰을 검증하지 않으며, 브라우저가 임의로 보낸 `X-User-Id`와 `X-User-Role` 헤더는 제거합니다. 클라이언트의 별도 Nginx 결제 프록시는 사용하지 않습니다.

관리자 화면은 `/admin/`에서 제공됩니다. 관리자 화면의 일반 API 요청은 서비스별 경로로 전달되며, Kafka DLQ와 장애 지표처럼 admin-service가 직접 처리하는 요청만 `/admin-api/`로 전달됩니다.

운영 화면의 기존 `/client-api/` 요청은 ingress에서 client-api-service로 전달됩니다. client-api-service 제거와 화면의 API 경로 전환은 별도 작업입니다.

두 Compose 파일은 같은 호스트 80번 포트와 컨테이너 이름을 사용하므로 한 번에 하나만 실행합니다. 환경을 바꿀 때는 현재 환경의 Compose 파일로 `down`을 실행한 뒤 다른 환경을 시작합니다.

각 서비스는 전달받은 JWT를 직접 검증합니다. 서비스 간 호출을 포함해 사용되는 `TOKEN_SECRET`은 동일한 값으로 설정해야 합니다.
