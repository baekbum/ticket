package dev.bum.support_service.service.inquiry;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.support.inquiry.dto.InquiryAnswerRequest;
import dev.bum.common.service.support.inquiry.dto.InquiryResponse;
import dev.bum.common.service.support.inquiry.dto.InquirySummaryResponse;
import dev.bum.common.service.support.inquiry.enums.InquiryCategory;
import dev.bum.common.service.support.inquiry.enums.InquiryStatus;
import dev.bum.support_service.exception.InquiryAnswerNotFoundException;
import dev.bum.support_service.exception.InquiryNotFoundException;
import dev.bum.support_service.exception.InquiryStateConflictException;
import dev.bum.support_service.jpa.inquiry.Inquiry;
import dev.bum.support_service.jpa.inquiry.InquiryAnswer;
import dev.bum.support_service.jpa.inquiry.InquiryAnswerJpaRepository;
import dev.bum.support_service.jpa.inquiry.InquiryJpaRepository;
import dev.bum.support_service.mapper.InquiryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional
public class InquiryManagementService {

    private static final int MAX_PAGE_SIZE = 100;

    private final InquiryJpaRepository inquiryRepository;
    private final InquiryAnswerJpaRepository answerRepository;

    @Transactional(readOnly = true)
    public CustomPageResponse<InquirySummaryResponse> select(
            InquiryStatus status,
            InquiryCategory category,
            String keyword,
            int page,
            int size
    ) {
        validatePage(page, size);
        Specification<Inquiry> specification = Specification.where(statusEquals(status))
                .and(categoryEquals(category))
                .and(keywordContains(keyword));
        Page<InquirySummaryResponse> result = inquiryRepository.findAll(
                        specification,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt", "id"))
                )
                .map(InquiryMapper::toSummaryResponse);
        return CustomPageResponse.of(
                result.getContent(), result.getSize(), result.getNumber(),
                result.getTotalElements(), result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public InquiryResponse selectById(Long inquiryId) {
        Inquiry inquiry = findById(inquiryId);
        return InquiryMapper.toResponse(
                inquiry,
                answerRepository.findByInquiryId(inquiryId).orElse(null)
        );
    }

    public InquiryResponse answer(Long inquiryId, InquiryAnswerRequest request, String responderId) {
        Inquiry inquiry = inquiryRepository.findByIdForUpdate(inquiryId)
                .orElseThrow(() -> new InquiryNotFoundException("존재하지 않는 문의입니다."));

        if (answerRepository.findByInquiryId(inquiryId).isPresent()) {
            throw new InquiryStateConflictException("이미 답변이 등록된 문의입니다.");
        }

        InquiryAnswer answer = InquiryAnswer.create(inquiry, normalizeUserId(responderId), request.content());
        inquiry.markAnswered();
        return InquiryMapper.toResponse(inquiry, answerRepository.save(answer));
    }

    public InquiryResponse updateAnswer(Long inquiryId, InquiryAnswerRequest request) {
        Inquiry inquiry = findById(inquiryId);
        InquiryAnswer answer = answerRepository.findByInquiryId(inquiryId)
                .orElseThrow(() -> new InquiryAnswerNotFoundException("등록된 답변이 없습니다."));
        answer.update(request.content());
        return InquiryMapper.toResponse(inquiry, answer);
    }

    private Inquiry findById(Long inquiryId) {
        return inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new InquiryNotFoundException("존재하지 않는 문의입니다."));
    }

    private Specification<Inquiry> statusEquals(InquiryStatus status) {
        return status == null ? null : (root, query, builder) -> builder.equal(root.get("status"), status);
    }

    private Specification<Inquiry> categoryEquals(InquiryCategory category) {
        return category == null ? null : (root, query, builder) -> builder.equal(root.get("category"), category);
    }

    private Specification<Inquiry> keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, builder) -> builder.or(
                builder.like(builder.lower(root.get("title")), pattern),
                builder.like(builder.lower(root.get("content")), pattern),
                builder.like(builder.lower(root.get("requesterId")), pattern)
        );
    }

    private String normalizeUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("답변자 정보가 필요합니다.");
        }
        return userId.trim();
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("페이지는 0 이상, 페이지 크기는 1~100이어야 합니다.");
        }
    }
}
