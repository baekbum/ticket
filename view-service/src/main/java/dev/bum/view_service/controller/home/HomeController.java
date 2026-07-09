package dev.bum.view_service.controller.home;

import dev.bum.view_service.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/home")
    public String home(@CookieValue(value = "accessToken", required = false) String token) {
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            return "redirect:/login-page"; // 실제 로그인 뷰 주소 적어주기
        }

        String role = jwtTokenProvider.getRole(token);

        if ("ROLE_ADMIN".equals(role)) {
            return "admin/dashboard"; // templates/admin/dashboard.html
        } else {
            return "user/user-mypage";      // templates/user/user-mypage.html
        }
    }
}
