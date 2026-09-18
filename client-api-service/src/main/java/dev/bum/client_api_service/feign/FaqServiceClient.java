package dev.bum.client_api_service.feign;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.support.faq.dto.FaqResponse;
import dev.bum.common.service.support.faq.enums.FaqCategory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "client-support-faq-service",
        url = "${services.support-service.url}",
        path = "/api/v1/faq"
)
public interface FaqServiceClient {

    @GetMapping("/select")
    CustomPageResponse<FaqResponse> select(
            @RequestParam(value = "category", required = false) FaqCategory category,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam("page") int page,
            @RequestParam("size") int size
    );
}
