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

    // 페이지당 조회할 수 있는 최대 항목 수.
    private static final int MAX_PAGE_SIZE = 100;
    // 문의 데이터를 조회하고 저장하는 레포지토리.
    private final InquiryJpaRepository inquiryRepository;
    // 문의 답변 데이터를 조회하고 저장하는 레포지토리.
    private final InquiryAnswerJpaRepository answerRepository;

    /**
     * 문의를 생성하고 저장한다.
     */
    public InquiryResponse insert(CreateInquiryRequest request, String requesterId) {

        Inquiry inquiry = Inquiry.create(
                normalizeUserId(requesterId),
                request.category(),
                request.title(),
                request.content()
        );
        return InquiryMapper.toResponse(inquiryRepository.save(inquiry), null);
    }

    /**
     * 현재 사용자의 항목을 조회한다.
     */
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

    /**
     * 문의 정보를 식별자로 조회한다.
     */
    @Transactional(readOnly = true)
    public InquiryResponse selectById(Long inquiryId, String requesterId) {

        Inquiry inquiry = findMine(inquiryId, requesterId);
        InquiryAnswer answer = answerRepository.findByInquiryId(inquiryId).orElse(null);
        return InquiryMapper.toResponse(inquiry, answer);
    }

    /**
     * 문의를 수정한다.
     */
    public InquiryResponse update(Long inquiryId, UpdateInquiryRequest request, String requesterId) {

        Inquiry inquiry = findMine(inquiryId, requesterId);
        inquiry.update(request.category(), request.title(), request.content());
        return InquiryMapper.toResponse(inquiry, null);
    }

    /**
     * 문의를 삭제한다.
     */
    public void delete(Long inquiryId, String requesterId) {

        Inquiry inquiry = inquiryRepository.findByIdAndRequesterIdForUpdate(
                        inquiryId,
                        normalizeUserId(requesterId)
                )
                .orElseThrow(() -> new InquiryNotFoundException("존재하지 않는 문의입니다."));
        inquiry.ensureDeletable();
        inquiryRepository.delete(inquiry);
    }

    /**
     * 현재 사용자의 항목을 조회한다.
     */
    private Inquiry findMine(Long inquiryId, String requesterId) {

        return inquiryRepository.findByIdAndRequesterId(inquiryId, normalizeUserId(requesterId))
                .orElseThrow(() -> new InquiryNotFoundException("존재하지 않는 문의입니다."));
    }

    /**
     * 사용자 ID의 공백과 대소문자를 정규화한다.
     */
    private String normalizeUserId(String userId) {

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("로그인 사용자 정보가 필요합니다.");
        }
        return userId.trim();
    }

    /**
     * 페이지 조회 조건이 유효한지 검증한다.
     */
    private void validatePage(int page, int size) {

        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("페이지는 0 이상, 페이지 크기는 1~100이어야 합니다.");
        }
    }

    /**
     * 조회 결과와 페이지 정보를 응답으로 변환한다.
     */
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
