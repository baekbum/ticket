package dev.bum.view_service.controller.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class AdminController {

    // 동적으로 레이아웃 조각을 반환할 수 있는 가벼운 API 추가
    @GetMapping("/admin/fragment/{menuName}")
    public String getAdminFragment(@PathVariable String menuName) {
        // 요청된 메뉴 이름에 맞춰 templates 폴더 안의 조각 HTML 파일명을 리턴합니다.
        if ("user".equals(menuName)) {
            return "admin/fragment-user"; // templates/admin/fragment-user.html 반환
        } else if ("event".equals(menuName)) {
            return "admin/fragment-event"; // 나중에 만들 공연용 조각 파일 매핑용 예시
        }

        return "error/404";
    }
}
