package dev.bum.ticket_service.service.area;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.area.dto.AreaCondRequest;
import dev.bum.common.service.ticket.area.dto.AreaResponse;
import dev.bum.common.service.ticket.area.dto.DeleteAreaBulkRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaRequest;
import dev.bum.common.service.ticket.area.dto.UpdateAreaRequest;
import dev.bum.common.service.ticket.area.enums.AreaStatus;
import dev.bum.common.service.ticket.event.eventLayout.dto.EventLayoutResponse;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.ticket_service.exception.area.AreaDuplicateException;
import dev.bum.ticket_service.exception.area.AreaLayoutAlreadyExistsException;
import dev.bum.ticket_service.audit.AuditDataMapper;
import dev.bum.ticket_service.audit.AuditLog;
import dev.bum.ticket_service.jpa.area.Area;
import dev.bum.ticket_service.jpa.area.AreaJpaRepository;
import dev.bum.ticket_service.jpa.area.AreaRepository;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventRepository;
import dev.bum.ticket_service.jpa.event.eventLayout.EventLayout;
import dev.bum.ticket_service.jpa.event.eventLayout.EventLayoutJpaRepository;
import dev.bum.ticket_service.jpa.seat.SeatJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AreaService {

    private final AreaRepository repository;
    private final AreaJpaRepository areaJpaRepository;
    private final EventRepository eventRepository;
    private final EventLayoutJpaRepository layoutJpaRepository;
    private final SeatJpaRepository seatJpaRepository;
    /**
     * SVG 배치도 파일을 저장하고 SVG 안의 구역 path/rect 정보를 구역 데이터로 등록한다.
     */
    @AuditLog(action = "AREA_CREATE_SVG", targetType = "AREA")
    public List<AreaResponse> insertSvg(Long eventId, MultipartFile svgFile, boolean force) {
        if (eventId == null) {
            throw new IllegalArgumentException("이벤트 ID를 입력해주세요.");
        }
        if (svgFile == null || svgFile.isEmpty()) {
            throw new IllegalArgumentException("SVG 파일을 업로드해주세요.");
        }
        if (hasAreaLayout(eventId)) {
            if (!force) {
                throw new AreaLayoutAlreadyExistsException("해당 구역 배치도가 이미 존재합니다. 새로 등록하시겠습니까?");
            }
            deleteAreaLayout(eventId);
        }

        String svgText = normalizeSvgFile(svgFile);
        saveEventLayout(eventId, svgFile.getOriginalFilename(), svgText);

        List<InsertAreaRequest> areas = parseSvgAreas(eventId, svgText);
        if (areas.isEmpty()) {
            throw new IllegalArgumentException("SVG 파일에서 등록 가능한 구역 path를 찾지 못했습니다.");
        }

        log.info("[AREA SVG INSERT] eventId : {}, count : {}", eventId, areas.size());
        return areas.stream()
                .map(area -> {
                    try {
                        return repository.insert(area).toResponse();
                    } catch (AreaDuplicateException e) {
                        log.info("[AREA SVG INSERT SKIP DUPLICATE] eventId : {}, areaName : {}", eventId, area.getAreaName());
                        return null;
                    }
                })
                .filter(response -> response != null)
                .toList();
    }

    /**
     * 이벤트에 기존 구역 배치도 또는 구역 데이터가 존재하는지 확인한다.
     */
    private boolean hasAreaLayout(Long eventId) {
        return layoutJpaRepository.existsByEvent_EventId(eventId) || areaJpaRepository.existsByEvent_EventId(eventId);
    }

    /**
     * SVG 재등록을 위해 기존 좌석, 구역, 배치도 데이터를 삭제한다.
     */
    private void deleteAreaLayout(Long eventId) {
        log.info("[AREA SVG REPLACE] delete previous layout. eventId : {}", eventId);
        seatJpaRepository.deleteByEventEventId(eventId);
        areaJpaRepository.deleteByEvent_EventId(eventId);
        layoutJpaRepository.deleteByEvent_EventId(eventId);
    }

    /**
     * 이벤트 ID로 저장된 구역 배치도 SVG를 조회한다.
     */
    @Transactional(readOnly = true)
    public EventLayoutResponse selectLayout(Long eventId) {
        log.info("[EVENT LAYOUT SELECT] eventId : {}", eventId);
        return layoutJpaRepository.findByEvent_EventId(eventId)
                .map(EventLayout::toResponse)
                .orElse(null);
    }

    /**
     * 구역 ID로 단건 구역 정보를 조회한다.
     */
    @Transactional(readOnly = true)
    public AreaResponse selectById(Long id) {
        log.info("[AREA SELECT] areaId : {}", id);
        return repository.selectById(id).toResponse();
    }

    /**
     * 검색 조건과 페이징 조건으로 구역 목록을 조회한다.
     */
    @Transactional(readOnly = true)
    public CustomPageResponse<AreaResponse> selectByCond(AreaCondRequest cond) {
        log.info("[AREA SELECT] cond : {}", cond);

        PageRequest pageRequest = PageRequest.of(cond.getPage(), cond.getSize(), makeSortInfo(cond.getSort()));
        Page<AreaResponse> areaPage = repository.selectByCond(cond, pageRequest).map(Area::toResponse);

        return CustomPageResponse.of(
                areaPage.getContent(),
                areaPage.getSize(),
                areaPage.getNumber(),
                areaPage.getTotalElements(),
                areaPage.getTotalPages()
        );
    }

    /**
     * 구역 정보를 수정한다.
     */
    @AuditLog(action = "AREA_UPDATE", targetType = "AREA")
    public AreaResponse update(Long id, UpdateAreaRequest info) {
        log.info("[AREA UPDATE] areaId : {}, info : {}", id, info);
        Area beforeArea = repository.selectById(id);
        AuditDataMapper.setChangedData(beforeArea, info);
        return repository.update(id, info).toResponse();
    }

    /**
     * 구역 ID로 단건 구역을 삭제한다.
     */
    @AuditLog(action = "AREA_DELETE", targetType = "AREA")
    public AreaResponse delete(Long id) {
        log.info("[AREA DELETE] areaId : {}", id);
        return repository.delete(id).toResponse();
    }

    /**
     * 선택한 구역 ID 목록을 일괄 삭제한다.
     */
    @AuditLog(action = "AREA_DELETE_BULK", targetType = "AREA")
    public void deleteBulk(DeleteAreaBulkRequest info) {
        if (info.getAreaIds() == null || info.getAreaIds().isEmpty()) {
            throw new IllegalArgumentException("삭제할 구역 정보가 없습니다.");
        }

        log.info("[AREA BULK DELETE] areaIds : {}", info.getAreaIds());
        info.getAreaIds().forEach(this::delete);
    }

    /**
     * 구역 등록 요청 목록을 검증하고 repository에 저장한다.
     */
    private List<AreaResponse> insertAreas(List<InsertAreaRequest> areas) {
        if (areas == null || areas.isEmpty()) {
            throw new IllegalArgumentException("등록할 구역 정보가 없습니다.");
        }

        log.info("[AREA INSERT AREAS] count : {}", areas.size());
        return areas.stream()
                .map(repository::insert)
                .map(Area::toResponse)
                .toList();
    }

    /**
     * 요청 sort 문자열을 Spring Data Sort 객체로 변환한다.
     */
    private Sort makeSortInfo(List<String> sorts) {
        Sort sort = Sort.unsorted();
        if (sorts != null && !sorts.isEmpty()) {
            List<Sort.Order> orders = new ArrayList<>();

            for (String infoStr : sorts) {
                String[] infos = infoStr.split("-");
                if (infos.length == 2) {
                    orders.add(new Sort.Order(Sort.Direction.fromString(infos[1]), infos[0]));
                }
            }
            sort = Sort.by(orders);
        }

        return sort;
    }

    /**
     * 이벤트별 원본 SVG 배치도 스냅샷을 저장하거나 교체한다.
     */
    private void saveEventLayout(Long eventId, String originalFileName, String svgText) {
        Event event = eventRepository.selectById(eventId);
        EventLayout layout = layoutJpaRepository.findByEvent_EventId(eventId)
                .orElseGet(() -> EventLayout.builder()
                        .event(event)
                        .build());

        layout.replace(originalFileName, svgText);
        layoutJpaRepository.save(layout);
    }

    /**
     * SVG 문서를 파싱해 구역 등록 요청 목록으로 변환한다.
     */
    private List<InsertAreaRequest> parseSvgAreas(Long eventId, String svgText) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            Document document = factory.newDocumentBuilder().parse(
                    new InputSource(new InputStreamReader(
                            new ByteArrayInputStream(svgText.getBytes(StandardCharsets.UTF_8)),
                            StandardCharsets.UTF_8
                    ))
            );

            List<InsertAreaRequest> areas = new ArrayList<>();
            Set<String> parsedLayoutKeys = new LinkedHashSet<>();

            collectSvgAreaElements(document.getElementsByTagName("path"), eventId, areas, parsedLayoutKeys);
            collectSvgAreaElements(document.getElementsByTagName("rect"), eventId, areas, parsedLayoutKeys);

            return areas;
        } catch (Exception e) {
            throw new IllegalArgumentException("SVG 파일을 분석하지 못했습니다.", e);
        }
    }

    /**
     * SVG path/rect 요소 중 구역으로 표시된 요소를 찾아 등록 요청 목록에 추가한다.
     */
    private void collectSvgAreaElements(NodeList elements, Long eventId, List<InsertAreaRequest> areas, Set<String> parsedLayoutKeys) {
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            String className = element.getAttribute("class");
            if (!containsClass(className, "area")) {
                continue;
            }
            if (containsClass(className, "console")) {
                continue;
            }

            String layoutKey = firstText(element.getAttribute("data-layout-key"), normalizeId(element.getAttribute("id")));
            String areaName = firstText(element.getAttribute("data-area-name"), layoutKey);
            if ("CONSOLE".equalsIgnoreCase(areaName)) {
                continue;
            }
            if (!StringUtils.hasText(layoutKey) || !parsedLayoutKeys.add(layoutKey)) {
                continue;
            }

            areas.add(InsertAreaRequest.builder()
                    .eventId(eventId)
                    .areaName(areaName)
                    .layoutKey(layoutKey)
                    .grade(parseGrade(element.getAttribute("data-grade"), className))
                    .price(parsePrice(element.getAttribute("data-price")))
                    .status(AreaStatus.ACTIVE)
                    .build());
        }
    }

    /**
     * 업로드된 SVG 파일의 바이트를 UTF-8 텍스트로 읽고 정규화한다.
     */
    private String normalizeSvgFile(MultipartFile svgFile) {
        try {
            return normalizeSvgText(new String(svgFile.getBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalArgumentException("SVG 파일을 읽을 수 없습니다.", e);
        }
    }

    /**
     * SVG class 속성에 특정 class token이 포함되어 있는지 확인한다.
     */
    private boolean containsClass(String className, String target) {
        if (!StringUtils.hasText(className)) return false;
        for (String token : className.split("\\s+")) {
            if (target.equals(token)) return true;
        }
        return false;
    }

    /**
     * SVG 요소 ID에서 구역 prefix를 제거해 layoutKey 후보를 만든다.
     */
    private String normalizeId(String id) {
        if (!StringUtils.hasText(id)) return null;
        return id
                .replaceFirst("^area-2f-", "")
                .replaceFirst("^area-1f-", "")
                .replaceFirst("^area-vip-", "")
                .replaceFirst("^area-floor-", "")
                .replaceFirst("^area-", "");
    }

    /**
     * 값이 있으면 trim한 값을 사용하고 없으면 fallback을 반환한다.
     */
    private String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    /**
     * SVG 속성 또는 class 정보에서 좌석 등급을 결정한다.
     */
    private SeatGrade parseGrade(String grade, String className) {
        String value = firstText(grade, null);
        if (!StringUtils.hasText(value)) {
            if (containsClass(className, "vip")) value = "VIP";
            else if (containsClass(className, "r")) value = "R";
            else if (containsClass(className, "s")) value = "S";
            else value = "A";
        }
        return SeatGrade.valueOf(value.toUpperCase());
    }

    /**
     * 가격 문자열에서 숫자만 추출해 정수 가격으로 변환한다.
     */
    private Integer parsePrice(String price) {
        if (!StringUtils.hasText(price)) return 0;
        String digits = price.replaceAll("[^0-9]", "");
        return StringUtils.hasText(digits) ? Integer.parseInt(digits) : 0;
    }

    /**
     * 일반 SVG 텍스트 또는 data URI 형태의 SVG를 파싱 가능한 SVG 텍스트로 정규화한다.
     */
    private String normalizeSvgText(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            throw new IllegalArgumentException("SVG 파일 내용이 비어 있습니다.");
        }

        String text = rawText.stripLeading().replaceFirst("^\\uFEFF", "");
        if (text.startsWith("data:image/svg+xml")) {
            int commaIndex = text.indexOf(',');
            if (commaIndex < 0) {
                throw new IllegalArgumentException("SVG data URI 형식이 올바르지 않습니다.");
            }

            String meta = text.substring(0, commaIndex);
            String payload = text.substring(commaIndex + 1);
            if (meta.contains(";base64")) {
                text = new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8);
            } else {
                text = java.net.URLDecoder.decode(payload, StandardCharsets.UTF_8);
            }
            text = text.stripLeading().replaceFirst("^\\uFEFF", "");
        }

        int svgStart = text.indexOf("<svg");
        if (svgStart > 0) {
            text = text.substring(svgStart);
        }

        if (!text.startsWith("<svg")) {
            throw new IllegalArgumentException("SVG 루트 태그를 찾을 수 없습니다.");
        }

        return text;
    }
}
