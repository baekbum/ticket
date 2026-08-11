package dev.bum.admin_service.controller.view;

import dev.bum.admin_service.config.MonitoringDashboardProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequestMapping("/api/v1/view")
@RequiredArgsConstructor
public class ViewController {

    private final MonitoringDashboardProperties monitoringDashboardProperties;
    private final Environment environment;

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
        } else if ("monitoring".equals(menuName)) {
            model.addAttribute("monitoringDashboardCards", monitoringDashboardProperties.getDashboards());
            return "fragment/fragment-monitoring";
        } else if ("failureMonitoring".equals(menuName)) {
            return "fragment/fragment-failure-monitoring";
        } else if ("redisHub".equals(menuName)) {
            return "fragment/fragment-redis-hub";
        } else if ("seatRedis".equals(menuName)) {
            return "fragment/fragment-redis";
        } else if ("queueRedis".equals(menuName)) {
            return "fragment/fragment-queue-redis";
        } else if ("seatCacheSyncFailures".equals(menuName)) {
            return "fragment/fragment-seat-cache-sync-failures";
        } else if ("kafkaDlq".equals(menuName)) {
            return "fragment/fragment-kafka-dlq";
        } else if ("kafkaDlqHistory".equals(menuName)) {
            return "fragment/fragment-kafka-dlq-history";
        } else if ("testHub".equals(menuName)) {
            model.addAttribute("localProfile", isLocalProfile());
            return "fragment/fragment-test-hub";
        } else if ("seatReservationTest".equals(menuName)) {
            return "fragment/fragment-seat-reservation-test";
        } else if ("queueEnterTest".equals(menuName)) {
            return "fragment/fragment-queue-enter-test";
        } else if ("dltPublishTest".equals(menuName)) {
            return isLocalProfile() ? "fragment/fragment-dlt-publish-test" : "error/404";
        } else if ("dltSlackTest".equals(menuName)) {
            return isLocalProfile() ? "fragment/fragment-dlt-slack-test" : "error/404";
        }

        return "error/404";
    }

    private boolean isLocalProfile() {
        return environment.acceptsProfiles(Profiles.of("local"));
    }
}
