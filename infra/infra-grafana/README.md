# Grafana

Grafana는 Prometheus를 datasource로 사용해 서비스 메트릭을 대시보드로 보여줍니다.

이 프로젝트에서는 `view-service`가 로컬 `3000` 포트를 사용하므로 Grafana는 호스트 `3001` 포트로 엽니다.

## 사전 준비

먼저 Prometheus가 실행 중이어야 합니다.

로컬 서비스 수집 모드:

```bash
docker compose -f infra/infra-prometheus/docker-compose-local.yml up -d
```

Docker 컨테이너 서비스 수집 모드:

```bash
docker compose -f infra/infra-prometheus/docker-compose.yml up -d
```

## 로컬 서비스 모드 실행

Spring Boot 서비스를 IntelliJ 또는 Gradle로 로컬 포트에서 직접 실행할 때 사용합니다.

```bash
docker compose -f infra/infra-grafana/docker-compose-local.yml up -d
```

이 모드의 Prometheus datasource URL은 `http://host.docker.internal:9090`입니다.

## Docker 컨테이너 서비스 모드 실행

서비스 컨테이너와 Prometheus가 공통 Docker 네트워크인 `ticket-network`에 있을 때 사용합니다.

```bash
docker compose -f infra/infra-grafana/docker-compose.yml up -d
```

이 모드의 Prometheus datasource URL은 `http://prometheus:9090`입니다.

## 접속 정보

Grafana UI:

```text
http://localhost:3001
```

기본 로그인:

```text
ID: admin
Password: admin
```

운영 환경에서는 반드시 `GF_SECURITY_ADMIN_PASSWORD` 환경 변수로 관리자 비밀번호를 변경해야 합니다.

## Provisioning

컨테이너가 시작되면 아래 설정이 자동 적용됩니다.

- Prometheus datasource: `provisioning/datasources/prometheus.yml`
- Dashboard provider: `provisioning/dashboards/dashboards.yml`
- 기본 대시보드: `dashboards/spring-services-overview.json`

기본 대시보드 이름은 `Ticket Spring Services Overview`입니다.

## 로그 확인

최초 실행 시 Grafana 내부 DB를 초기화하면서 `logger=migrator` 로그가 많이 출력됩니다.

예시:

```text
logger=migrator level=info msg="Executing migration"
logger=migrator level=info msg="Migration successfully executed"
```

이 로그는 정상입니다. Grafana가 처음 시작될 때 dashboard, user, alert, datasource 관련 테이블과 인덱스를 생성하거나 변경합니다.

정상 기동 여부는 아래 명령으로 확인합니다.

```bash
docker ps -a --filter "name=grafana"
```

컨테이너 상태가 `Up`이고 마지막 로그에 `HTTP Server Listen` 또는 `server is listening` 계열 메시지가 보이면 정상입니다.

아래 상황이면 문제로 봐야 합니다.

- 컨테이너 상태가 계속 `Restarting`으로 바뀐다.
- 동일한 migration 로그가 처음부터 반복된다.
- `level=error`, `failed`, `panic` 로그가 반복된다.
- `http://localhost:3001`에 접속되지 않는다.

## Admin Service iframe 연동 참고

Grafana embed를 위해 compose에 아래 설정을 넣어두었습니다.

- `GF_SECURITY_ALLOW_EMBEDDING=true`
- `GF_AUTH_ANONYMOUS_ENABLED=true`
- `GF_AUTH_ANONYMOUS_ORG_ROLE=Viewer`

다음 단계에서 `admin-service` 화면에 Grafana dashboard URL을 iframe으로 연결하면 됩니다.
