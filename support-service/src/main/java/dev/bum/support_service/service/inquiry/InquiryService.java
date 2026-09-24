package dev.bum.support_service.service.inquiry;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.support.inquiry.dto.CreateInquiryRequest;
import dev.bum.common.service.support.inquiry.dto.InquiryResponse;
import dev.bum.common.service.support.inquiry.dto.InquirySummaryResponse;
import dev.bum.common.service.support.inquiry.dto.UpdateInquiryRequest;
import dev.bum.support_service.exception.InquiryNotFoundException;
import dev.bum.support_service.jpa.inquiry.Inquiry;
import dev.bum.support_service.jpa.inquiry.InquiryAnswer;
import dev.bum.support_service.jpa.inquiry.InquiryAnswerJpaRepository;
import dev.bum.support_service.jpa.inquiry.InquiryJpaRepository;
import dev.bum.support_service.mapper.InquiryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class InquiryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final InquiryJpaRepository inquiryRepository;
    private final InquiryAnswerJpaRepository answerRepository;

    public InquiryResponse insert(CreateInquiryRequest request, String requesterId) {
        Inquiry inquiry = Inquiry.create(
                normalizeUserId(requesterId),
                request.category(),
                request.title(),
                request.content()
        );
        return InquiryMapper.toResponse(inquiryRepository.save(inquiry), null);
    }

    @Transactional(readOnly = true)
    public CustomPageResponse<InquirySummaryResponse> selectMine(String requesterId, int page, int size) {
        validatePage(page, size);
        Page<InquirySummaryResponse> result = inquiryRepository.findAllByRequesterId(
                        normalizeUserId(requesterId),
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"))
                )
                .map(InquiryMapper::toSummaryResponse);
        return toPageResponse(result);
    }

    @Transactional(readOnly = true)
    public InquiryResponse selectById(Long inquiryId, String requesterId) {
        Inquiry inquiry = findMine(inquiryId, requesterId);
        InquiryAnswer answer = answerRepository.findByInquiryId(inquiryId).orElse(null);
        return InquiryMapper.toResponse(inquiry, answer);
    }

    public InquiryResponse update(Long inquiryId, UpdateInquiryRequest request, String requesterId) {
        Inquiry inquiry = findMine(inquiryId, requesterId);
        inquiry.update(request.category(), request.title(), request.content());
        return InquiryMapper.toResponse(inquiry, null);
    }

    public void delete(Long inquiryId, String requesterId) {
        Inquiry inquiry = inquiryRepository.findByIdAndRequesterIdForUpdate(
                        inquiryId,
                        normalizeUserId(requesterId)
                )
                .orElseThrow(() -> new InquiryNotFoundException("존재하지 않는 문의입니다."));
        inquiry.ensureDeletable();
        inquiryRepository.delete(inquiry);
    }

    private Inquiry findMine(Long inquiryId, String requesterId) {
        return inquiryRepository.findByIdAndRequesterId(inquiryId, normalizeUserId(requesterId))
                .orElseThrow(() -> new InquiryNotFoundException("존재하지 않는 문의입니다."));
    }

    private String normalizeUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("로그인 사용자 정보가 필요합니다.");
        }
        return userId.trim();
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("페이지는 0 이상, 페이지 크기는 1~100이어야 합니다.");
        }
    }

    private CustomPageResponse<InquirySummaryResponse> toPageResponse(Page<InquirySummaryResponse> page) {
        return CustomPageResponse.of(
                page.getContent(),
                page.getSize(),
                page.getNumber(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
