package dev.bum.admin_service.controller.view;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequestMapping("/api/v1/view")
@RequiredArgsConstructor
public class ViewController {

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

    /**
     * content 화면
     * @param menuName
     * @return
     */
    @GetMapping("/fragment/{menuName}")
    public String getAdminFragment(@PathVariable String menuName) {
        // 요청된 메뉴 이름에 맞춰 templates 폴더 안의 조각 HTML 파일명을 리턴합니다.
        if ("user".equals(menuName)) {
            return "fragment/fragment-user";
        } else if ("event".equals(menuName)) {
            return "fragment/fragment-event";
        } else if ("seat".equals(menuName)) {
            return "fragment/fragment-seat";
        }

        return "error/404";
    }
}
