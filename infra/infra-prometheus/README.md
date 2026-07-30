# Prometheus

Prometheus는 각 서비스의 Spring Boot actuator 메트릭을 수집합니다.

이 프로젝트는 실행 방식이 두 가지라 Prometheus 설정도 분리합니다.

- Docker 컨테이너 서비스 수집: `prometheus.yml`
- 로컬에서 직접 실행한 서비스 수집: `prometheus-local.yml`

## Docker 컨테이너 서비스 수집

서비스 컨테이너들이 공통 Docker 네트워크인 `ticket-network`에서 실행 중일 때 사용합니다.

공통 네트워크가 없다면 한 번만 생성합니다.

```bash
docker network create ticket-network
```

Prometheus를 실행합니다.

```bash
docker compose -f infra/infra-prometheus/docker-compose.yml up -d
```

컨테이너 환경에서는 production profile 기준으로 대부분의 서비스가 내부 포트 `8080`을 사용합니다.

- `auth-service:8080/actuator/prometheus`
- `user-service:8080/actuator/prometheus`
- `ticket-service:8080/actuator/prometheus`
- `queue-service:8080/actuator/prometheus`
- `audit-service:8080/actuator/prometheus`
- `admin-service:8999/admin/actuator/prometheus`
- `view-service:3000/actuator/prometheus`

## 로컬 서비스 수집

Spring Boot 서비스를 IntelliJ 또는 Gradle로 로컬에서 직접 실행할 때 사용합니다.

Prometheus는 Docker 컨테이너로 실행하지만, 수집 대상은 호스트 PC의 로컬 포트입니다. 그래서 target host는 `localhost`가 아니라 Docker Desktop 기준 `host.docker.internal`을 사용합니다.

```bash
docker compose -f infra/infra-prometheus/docker-compose-local.yml up -d
```

로컬 환경에서는 각 서비스의 `server.servlet.context-path`와 포트가 반영됩니다.

- `host.docker.internal:8080/auth/actuator/prometheus`
- `host.docker.internal:8081/user/actuator/prometheus`
- `host.docker.internal:8082/ticket/actuator/prometheus`
- `host.docker.internal:8083/queue/actuator/prometheus`
- `host.docker.internal:8084/audit/actuator/prometheus`
- `host.docker.internal:8999/admin/actuator/prometheus`
- `host.docker.internal:3000/actuator/prometheus`

## 확인 방법

Prometheus UI:

```text
http://localhost:9090
```

수집 대상 상태:

```text
http://localhost:9090/targets
```

실행 중이지 않은 서비스는 target 화면에서 `DOWN`으로 표시됩니다.
