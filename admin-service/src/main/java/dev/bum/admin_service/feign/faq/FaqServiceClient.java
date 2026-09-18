package dev.bum.admin_service.feign.faq;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.support.faq.dto.CreateFaqRequest;
import dev.bum.common.service.support.faq.dto.FaqResponse;
import dev.bum.common.service.support.faq.dto.FaqSearchRequest;
import dev.bum.common.service.support.faq.dto.UpdateFaqRequest;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "support-service",
        contextId = "faq-service-client",
        url = "${services.support-service.url}",
        path = "/api/v1/manage/faq"
)
public interface FaqServiceClient {

    @PostMapping("/insert")
    FaqResponse insert(@Valid @RequestBody CreateFaqRequest request);

    @GetMapping("/select/id/{faqId}")
    FaqResponse selectById(@PathVariable("faqId") Long faqId);

    @GetMapping("/select")
    CustomPageResponse<FaqResponse> select(@SpringQueryMap FaqSearchRequest request);

    @PutMapping("/update/id/{faqId}")
    FaqResponse update(
            @PathVariable("faqId") Long faqId,
            @Valid @RequestBody UpdateFaqRequest request
    );

    @DeleteMapping("/delete/id/{faqId}")
    void delete(@PathVariable("faqId") Long faqId);
}
