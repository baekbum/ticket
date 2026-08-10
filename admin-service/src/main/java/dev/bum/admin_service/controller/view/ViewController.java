package dev.bum.admin_service.controller.view;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/api/v1/view")
@RequiredArgsConstructor
public class ViewController {

    @Value("${app.monitoring.dashboards.spring-boot-jvm-url}")
    private String springBootJvmDashboardUrl;

    @Value("${app.monitoring.dashboards.postgresql-url}")
    private String postgresqlDashboardUrl;

    @Value("${app.monitoring.dashboards.redis-url}")
    private String redisDashboardUrl;

    @Value("${app.monitoring.dashboards.docker-url}")
    private String dockerDashboardUrl;

    @Value("${app.monitoring.dashboards.nginx-url}")
    private String nginxDashboardUrl;

    /**
     * 로그인 및 화원가입 화면
     * @return
     */
    @GetMapping("/login")
    public String loginPage() {
        return "login/login";
    }

    /**
     * 홈 화면
     * @return
     */
    @GetMapping("/home")
    public String home() {
        return "admin/dashboard";
    }

    @GetMapping("/embed/{menuName}")
    public String embed(@PathVariable String menuName, Model model) {
        model.addAttribute("menuName", menuName);
        return "admin/embed";
    }

    /**
     * content 화면
     * @param menuName
     * @return
     */
    @GetMapping("/fragment/{menuName}")
    public String getAdminFragment(@PathVariable String menuName, Model model) {
        // 요청된 메뉴 이름에 맞춰 templates 폴더 안의 조각 HTML 파일명을 리턴합니다.
        if ("user".equals(menuName)) {
            return "fragment/fragment-user";
        } else if ("event".equals(menuName)) {
            return "fragment/fragment-event";
        } else if ("area".equals(menuName)) {
            return "fragment/fragment-area";
        } else if ("seat".equals(menuName)) {
            return "fragment/fragment-seat";
        } else if ("reservation".equals(menuName)) {
            return "fragment/fragment-reservation";
        } else if ("reservationDelivery".equals(menuName)) {
            return "fragment/fragment-reservation-delivery";
        } else if ("coupon".equals(menuName)) {
            return "fragment/fragment-coupon";
        } else if ("userCoupon".equals(menuName)) {
            return "fragment/fragment-user-coupon";
        } else if ("auditLog".equals(menuName)) {
            return "fragment/fragment-audit-log";
        } else if ("redisHub".equals(menuName)) {
            return "fragment/fragment-redis-hub";
        } else if ("kafkaDlq".equals(menuName)) {
            return "fragment/fragment-kafka-dlq";
        } else if ("kafkaDlqHistory".equals(menuName)) {
            return "fragment/fragment-kafka-dlq-history";
        } else if ("testHub".equals(menuName)) {
            return "fragment/fragment-test-hub";
        } else if ("seatRedis".equals(menuName)) {
            return "fragment/fragment-redis";
        } else if ("queueRedis".equals(menuName)) {
            return "fragment/fragment-queue-redis";
        } else if ("seatCacheSyncFailures".equals(menuName)) {
            return "fragment/fragment-seat-cache-sync-failures";
        } else if ("seatReservationTest".equals(menuName)) {
            return "fragment/fragment-seat-reservation-test";
        } else if ("queueEnterTest".equals(menuName)) {
            return "fragment/fragment-queue-enter-test";
        } else if ("monitoring".equals(menuName)) {
            model.addAttribute("monitoringDashboardCards", monitoringDashboardCards());
            return "fragment/fragment-monitoring";
        } else if ("failureMonitoring".equals(menuName)) {
            return "fragment/fragment-failure-monitoring";
        }

        return "error/404";
    }

    private List<Map<String, String>> monitoringDashboardCards() {
        return List.of(
                monitoringDashboardCard(
                        "ti ti-leaf",
                        "Spring Boot / JVM",
                        "SpringBoot APM Dashboard (12900), JVM Micrometer",
                        "HTTP 요청, 응답 시간, 예외, CPU, 메모리, GC, 스레드를 확인합니다.",
                        springBootJvmDashboardUrl
                ),
                monitoringDashboardCard(
                        "ti ti-database",
                        "PostgreSQL",
                        "PostgreSQL Dashboard (9628)",
                        "커넥션, 트랜잭션, 쿼리 처리량, DB 리소스 상태를 확인합니다.",
                        postgresqlDashboardUrl
                ),
                monitoringDashboardCard(
                        "ti ti-database",
                        "Redis",
                        "Redis Dashboard",
                        "메모리, 명령 처리량, 연결 수, 대기열/락 저장소 상태를 확인합니다.",
                        redisDashboardUrl
                ),
                monitoringDashboardCard(
                        "ti ti-brand-docker",
                        "Docker",
                        "Docker / cAdvisor Dashboard",
                        "컨테이너 CPU, 메모리, 네트워크, 파일시스템 사용량을 확인합니다.",
                        dockerDashboardUrl
                ),
                monitoringDashboardCard(
                        "ti ti-world-www",
                        "Nginx",
                        "Nginx Ingress Dashboard",
                        "Ingress 요청량, 응답 코드, 지연 시간, 업스트림 상태를 확인합니다.",
                        nginxDashboardUrl
                )
        );
    }

    private Map<String, String> monitoringDashboardCard(
            String icon,
            String title,
            String subtitle,
            String description,
            String url
    ) {
        return Map.of(
                "icon", icon,
                "title", title,
                "subtitle", subtitle,
                "description", description,
                "url", url
        );
    }
}
