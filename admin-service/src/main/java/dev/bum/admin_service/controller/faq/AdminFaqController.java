package dev.bum.admin_service.controller.faq;

import dev.bum.admin_service.feign.faq.FaqServiceClient;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.support.faq.dto.CreateFaqRequest;
import dev.bum.common.service.support.faq.dto.FaqResponse;
import dev.bum.common.service.support.faq.dto.FaqSearchRequest;
import dev.bum.common.service.support.faq.dto.UpdateFaqRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/faq")
public class AdminFaqController {

    private final FaqServiceClient faqServiceClient;

    @PostMapping("/insert")
    public ResponseEntity<FaqResponse> insert(@Valid @RequestBody CreateFaqRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(faqServiceClient.insert(request));
    }

    @GetMapping("/select/id/{faqId}")
    public ResponseEntity<FaqResponse> selectById(@PathVariable Long faqId) {
        return ResponseEntity.ok(faqServiceClient.selectById(faqId));
    }

    @GetMapping("/select")
    public ResponseEntity<CustomPageResponse<FaqResponse>> select(
            @Valid @ModelAttribute FaqSearchRequest request
    ) {
        return ResponseEntity.ok(faqServiceClient.select(request));
    }

    @PutMapping("/update/id/{faqId}")
    public ResponseEntity<FaqResponse> update(
            @PathVariable Long faqId,
            @Valid @RequestBody UpdateFaqRequest request
    ) {
        return ResponseEntity.ok(faqServiceClient.update(faqId, request));
    }

    @DeleteMapping("/delete/id/{faqId}")
    public ResponseEntity<Void> delete(@PathVariable Long faqId) {
        faqServiceClient.delete(faqId);
        return ResponseEntity.noContent().build();
    }
}
