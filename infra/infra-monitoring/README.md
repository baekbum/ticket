# Monitoring

Prometheus와 Grafana를 함께 실행하는 모니터링 패키지입니다.

- Prometheus: 각 서비스의 `/actuator/prometheus` 메트릭 수집
- Grafana: Prometheus를 datasource로 사용해 대시보드 표시

`client-service`가 로컬 `3000` 포트를 사용하므로 Grafana는 호스트 `3001` 포트로 엽니다.

## 로컬 서비스 모드

Spring Boot 서비스를 IntelliJ 또는 Gradle로 로컬에서 직접 실행할 때 사용합니다.

```bash
docker compose -f infra/infra-monitoring/docker-compose-local.yml up -d
```

Prometheus는 Docker 컨테이너로 실행되지만, 수집 대상은 호스트 PC의 로컬 포트입니다. 그래서 `prometheus/prometheus-local.yml`에서는 `host.docker.internal`을 사용합니다.

로컬 수집 경로:

- `auth-service`: `host.docker.internal:8080/auth/actuator/prometheus`
- `user-service`: `host.docker.internal:8081/user/actuator/prometheus`
- `ticket-service`: `host.docker.internal:8082/ticket/actuator/prometheus`
- `queue-service`: `host.docker.internal:8083/queue/actuator/prometheus`
- `audit-service`: `host.docker.internal:8084/audit/actuator/prometheus`
- `admin-service`: `host.docker.internal:8999/admin/actuator/prometheus`
- `client-service`: `host.docker.internal:3000/health`
- `local-postgres`: `host.docker.internal:5432` 대상 `local-postgres-exporter`
- `refresh-redis`: `host.docker.internal:6379` 대상 `refresh-redis-exporter`
- `seat-redis`: `host.docker.internal:6380` 대상 `seat-redis-exporter`
- `queue-redis`: `host.docker.internal:6381` 대상 `queue-redis-exporter`
- `Docker`: `cadvisor:8080`

로컬 PostgreSQL은 기본적으로 단일 DB 컨테이너를 사용합니다.

```yaml
LOCAL_POSTGRES_USER: postgres
LOCAL_POSTGRES_PASSWORD: hsck2301
LOCAL_POSTGRES_PORT: 5432
LOCAL_POSTGRES_DB: msa
```

서버/컨테이너 모드는 서비스별 DB를 분리해서 수집합니다.

## Docker 컨테이너 서비스 모드

서비스들도 Docker 컨테이너로 실행할 때 사용합니다.

공통 Docker 네트워크가 없다면 한 번만 생성합니다.

```bash
docker network create ticket-network
```

```bash
docker compose -f infra/infra-monitoring/docker-compose.yml up -d
```

Prometheus는 `ticket-network` 내부 서비스명으로 수집합니다.

컨테이너 수집 경로:

- `auth-service:8080/actuator/prometheus`
- `user-service:8080/actuator/prometheus`
- `ticket-service:8080/actuator/prometheus`
- `queue-service:8080/actuator/prometheus`
- `audit-service:8080/actuator/prometheus`
- `admin-service:8999/admin/actuator/prometheus`
- `client-service:3000/health`
- `auth-db:5432` 대상 `auth-postgres-exporter`
- `user-db:5432` 대상 `user-postgres-exporter`
- `ticket-db:5432` 대상 `ticket-postgres-exporter`
- `refresh-redis:6379` 대상 `refresh-redis-exporter`
- `seat-redis:6379` 대상 `seat-redis-exporter`
- `queue-redis:6379` 대상 `queue-redis-exporter`
- `Docker`: `cadvisor:8080`

## 접속 정보

- Prometheus: `http://localhost:9090`
- Prometheus targets: `http://localhost:9090/targets`
- Grafana: `http://localhost:3001`

Grafana 기본 로그인:

```text
ID: admin
Password: admin
```

운영 환경에서는 반드시 `GF_SECURITY_ADMIN_PASSWORD` 환경 변수로 관리자 비밀번호를 변경해야 합니다.

## Grafana Provisioning

컨테이너가 시작되면 아래 설정이 자동 적용됩니다.

- Prometheus datasource: `grafana/provisioning/datasources/prometheus.yml`
- Dashboard provider: `grafana/provisioning/dashboards/dashboards.yml`
- Spring Boot / JVM 대시보드: `grafana/dashboards/spring-services-overview.json`
- PostgreSQL 대시보드: `grafana/dashboards/postgresql-overview.json`
- Redis 대시보드: `grafana/dashboards/redis-overview.json`
- Docker/cAdvisor 대시보드: `grafana/dashboards/docker-cadvisor-overview.json`

기본 대시보드 이름은 아래와 같습니다.

- `Ticket Spring Services Overview`
- `Ticket PostgreSQL Overview`
- `Ticket Redis Overview`
- `Ticket Docker cAdvisor Overview`

대시보드 상단에는 아래 변수를 제공합니다.

- `Service`: `auth-service`, `ticket-service` 같은 서비스 단위 필터
- `Job`: Prometheus scrape job 필터
- `Instance`: 실제 target 주소 필터

각 변수는 `All`과 다중 선택을 지원합니다.

## 로그 확인

Grafana 최초 실행 시 내부 DB를 초기화하면서 `logger=migrator` 로그가 많이 출력될 수 있습니다.

```text
logger=migrator level=info msg="Executing migration"
logger=migrator level=info msg="Migration successfully executed"
```

이 로그는 정상입니다. Grafana가 처음 시작될 때 dashboard, user, alert, datasource 관련 테이블과 인덱스를 생성하거나 변경합니다.

정상 기동 여부는 아래 명령으로 확인합니다.

```bash
docker ps -a --filter "name=grafana"
```

컨테이너 상태가 `Up`이고 마지막 로그에 `HTTP Server Listen` 계열 메시지가 보이면 정상입니다.

아래 상황이면 문제로 봐야 합니다.

- 컨테이너 상태가 계속 `Restarting`으로 바뀐다.
- 동일한 migration 로그가 처음부터 반복된다.
- `level=error`, `failed`, `panic` 로그가 반복된다.
- `http://localhost:3001`에 접속되지 않는다.

## Admin Service 연동 참고

Grafana를 새 창으로 열 수 있도록 compose에 아래 설정을 넣어두었습니다.

- `GF_AUTH_ANONYMOUS_ENABLED=true`
- `GF_AUTH_ANONYMOUS_ORG_ROLE=Viewer`

`admin-service`의 모니터링 메뉴는 카드형 허브 화면을 표시합니다.

카드를 클릭하면 각 Grafana 대시보드 URL이 새 창으로 열립니다. Spring Boot / JVM은 현재 기본 대시보드인 `Ticket Spring Services Overview`로 연결됩니다.

PostgreSQL, Redis, Docker/cAdvisor 카드는 이 패키지에서 provisioning하는 대시보드로 바로 연결됩니다.

Nginx 카드는 아직 Nginx exporter와 대시보드가 없으므로 Grafana 검색 화면으로 연결됩니다. 실제 데이터 대시보드로 바로 열려면 Nginx exporter와 dashboard JSON을 추가한 뒤 `APP_MONITORING_NGINX_DASHBOARD_URL`을 지정합니다.

```yaml
app:
  monitoring:
    grafana-dashboard-url: ${APP_MONITORING_GRAFANA_DASHBOARD_URL:http://localhost:3001/d/ticket-spring-services/ticket-spring-services-overview?orgId=1&refresh=10s&kiosk}
    dashboards:
      spring-boot-jvm-url: ${APP_MONITORING_SPRING_BOOT_JVM_DASHBOARD_URL:http://localhost:3001/d/ticket-spring-services/ticket-spring-services-overview?orgId=1&refresh=10s&var-application=All&kiosk}
      postgresql-url: ${APP_MONITORING_POSTGRESQL_DASHBOARD_URL:http://localhost:3001/d/ticket-postgresql-overview/ticket-postgresql-overview?orgId=1&refresh=10s&kiosk}
      redis-url: ${APP_MONITORING_REDIS_DASHBOARD_URL:http://localhost:3001/d/ticket-redis-overview/ticket-redis-overview?orgId=1&refresh=10s&kiosk}
      docker-url: ${APP_MONITORING_DOCKER_DASHBOARD_URL:http://localhost:3001/d/ticket-docker-cadvisor-overview/ticket-docker-cadvisor-overview?orgId=1&refresh=10s&kiosk}
      nginx-url: ${APP_MONITORING_NGINX_DASHBOARD_URL:http://localhost:3001/dashboards?query=Nginx}
```
