package dev.bum.client_api_service.controller;

import dev.bum.client_api_service.feign.FaqServiceClient;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.support.faq.dto.FaqResponse;
import dev.bum.common.service.support.faq.enums.FaqCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/faq")
@RequiredArgsConstructor
public class ClientFaqController {

    private final FaqServiceClient faqServiceClient;

    @GetMapping("/select")
    public ResponseEntity<CustomPageResponse<FaqResponse>> select(
            @RequestParam(required = false) FaqCategory category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        return ResponseEntity.ok(faqServiceClient.select(category, keyword, page, size));
    }
}
