# Infra

서비스별 인프라와 모니터링 실행 파일을 관리합니다.

## 디렉터리 구조

- `infra-auth`: auth-service용 DB/Redis
- `infra-user`: user-service용 DB
- `infra-ticket`: ticket-service용 DB/Redis
- `infra-queue`: queue-service용 Redis
- `infra-kafka`: Kafka/Zookeeper
- `infra-ingress`: 로컬/운영 Nginx ingress (`infra-ingress/README.md`)
- `infra-monitoring`: Prometheus/Grafana 통합 모니터링

## 공통 네트워크

Docker 컨테이너 서비스 모드에서는 모든 컨테이너가 `ticket-network`에 연결되어야 합니다.

```bash
docker network create ticket-network
```

이미 존재하면 에러가 날 수 있지만 한 번만 생성하면 됩니다.

## 운영 필수 보안 환경 변수

`prod` 프로필은 아래 값에 기본값을 사용하지 않습니다. 값이 없으면 서비스가 시작되지 않습니다.

- `TOKEN_SECRET`: JWT를 발급하거나 검증하는 모든 서비스에서 같은 값을 사용합니다.
- `APP_INTERNAL_SERVICE_TOKEN`: `ticket-service`와 `payment-gateway-service` 간 호출에 같은 값을 사용합니다.
- `APP_PAYMENT_PROVIDER_TOKEN`: 외부 결제 제공자가 가상계좌 입금을 통지할 때 사용합니다.

루트의 `.env.example`을 참고해 실제 값은 Git에 포함되지 않는 `.env` 또는 배포 환경의 secret 저장소에 설정합니다.

## 모니터링 로컬 모드

Spring Boot 서비스를 IntelliJ 또는 Gradle로 로컬에서 직접 실행할 때 사용합니다.

```bash
docker compose -f infra/infra-monitoring/docker-compose-local.yml up -d
```

접속 URL:

- Prometheus: `http://localhost:9090`
- Prometheus targets: `http://localhost:9090/targets`
- Grafana: `http://localhost:3001`

## 모니터링 Docker 컨테이너 모드

서비스들도 Docker 컨테이너로 실행할 때 사용합니다.

```bash
docker compose -f infra/infra-monitoring/docker-compose.yml up -d
```

이 모드에서는 Prometheus가 `ticket-network` 내부 서비스명으로 수집합니다.

## 상세 문서

모니터링 상세 실행 방법과 target 목록은 `infra/infra-monitoring/README.md`를 확인합니다.
