package dev.bum.ticket_service.service.area;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.area.dto.AreaCondRequest;
import dev.bum.common.service.ticket.area.dto.AreaResponse;
import dev.bum.common.service.ticket.area.dto.DeleteAreaBulkRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaBulkRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaJsonRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaRequest;
import dev.bum.common.service.ticket.area.dto.UpdateAreaRequest;
import dev.bum.common.service.ticket.area.enums.AreaStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.service.ticket.layout.dto.EventLayoutResponse;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.ticket_service.exception.area.AreaDuplicateException;
import dev.bum.ticket_service.jpa.area.Area;
import dev.bum.ticket_service.jpa.area.AreaRepository;
import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.ticket_service.jpa.event.EventRepository;
import dev.bum.ticket_service.jpa.layout.EventLayout;
import dev.bum.ticket_service.jpa.layout.EventLayoutJpaRepository;
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
    private final EventRepository eventRepository;
    private final EventLayoutJpaRepository layoutJpaRepository;
    private final ObjectMapper objectMapper;

    public AreaResponse insert(InsertAreaRequest info) {
        log.info("[AREA INSERT] {}", info);
        return repository.insert(info).toResponse();
    }

    public List<AreaResponse> insertBulk(InsertAreaBulkRequest info) {
        if (info.getAreas() == null || info.getAreas().isEmpty()) {
            throw new IllegalArgumentException("등록할 구역 정보가 없습니다.");
        }

        log.info("[AREA BULK INSERT] count : {}", info.getAreas().size());
        return info.getAreas().stream()
                .map(repository::insert)
                .map(Area::toResponse)
                .toList();
    }

    public List<AreaResponse> insertJson(InsertAreaJsonRequest info) {
        log.info("[AREA JSON INSERT]");
        try {
            List<InsertAreaRequest> areas = objectMapper.readValue(
                    info.getJsonText(),
                    new TypeReference<List<InsertAreaRequest>>() {}
            );

            return insertBulk(InsertAreaBulkRequest.builder()
                    .areas(areas)
                    .build());
        } catch (Exception e) {
            throw new IllegalArgumentException("구역 JSON 형식이 올바르지 않습니다.", e);
        }
    }

    public List<AreaResponse> insertSvg(Long eventId, MultipartFile svgFile) {
        if (eventId == null) {
            throw new IllegalArgumentException("이벤트 ID를 입력해주세요.");
        }
        if (svgFile == null || svgFile.isEmpty()) {
            throw new IllegalArgumentException("SVG 파일을 업로드해주세요.");
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

    @Transactional(readOnly = true)
    public EventLayoutResponse selectLayout(Long eventId) {
        log.info("[EVENT LAYOUT SELECT] eventId : {}", eventId);
        return layoutJpaRepository.findByEvent_EventId(eventId)
                .map(EventLayout::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public AreaResponse selectById(Long id) {
        log.info("[AREA SELECT] areaId : {}", id);
        return repository.selectById(id).toResponse();
    }

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

    public AreaResponse update(Long id, UpdateAreaRequest info) {
        log.info("[AREA UPDATE] areaId : {}, info : {}", id, info);
        return repository.update(id, info).toResponse();
    }

    public AreaResponse delete(Long id) {
        log.info("[AREA DELETE] areaId : {}", id);
        return repository.delete(id).toResponse();
    }

    public void deleteBulk(DeleteAreaBulkRequest info) {
        if (info.getAreaIds() == null || info.getAreaIds().isEmpty()) {
            throw new IllegalArgumentException("삭제할 구역 정보가 없습니다.");
        }

        log.info("[AREA BULK DELETE] areaIds : {}", info.getAreaIds());
        info.getAreaIds().forEach(this::delete);
    }

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

    private void saveEventLayout(Long eventId, String originalFileName, String svgText) {
        Event event = eventRepository.selectById(eventId);
        EventLayout layout = layoutJpaRepository.findByEvent_EventId(eventId)
                .orElseGet(() -> EventLayout.builder()
                        .event(event)
                        .build());

        layout.replace(originalFileName, svgText);
        layoutJpaRepository.save(layout);
    }

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
            Set<String> parsedAreaNames = new LinkedHashSet<>();

            collectSvgAreaElements(document.getElementsByTagName("path"), eventId, areas, parsedAreaNames);
            collectSvgAreaElements(document.getElementsByTagName("rect"), eventId, areas, parsedAreaNames);

            return areas;
        } catch (Exception e) {
            throw new IllegalArgumentException("SVG 파일을 분석하지 못했습니다.", e);
        }
    }

    private void collectSvgAreaElements(NodeList elements, Long eventId, List<InsertAreaRequest> areas, Set<String> parsedAreaNames) {
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            String className = element.getAttribute("class");
            if (!containsClass(className, "area")) {
                continue;
            }
            if (containsClass(className, "console")) {
                continue;
            }

            String areaName = firstText(element.getAttribute("data-area-name"), normalizeId(element.getAttribute("id")));
            if ("CONSOLE".equalsIgnoreCase(areaName)) {
                continue;
            }
            if (!StringUtils.hasText(areaName) || !parsedAreaNames.add(areaName)) {
                continue;
            }

            areas.add(InsertAreaRequest.builder()
                    .eventId(eventId)
                    .areaName(areaName)
                    .grade(parseGrade(element.getAttribute("data-grade"), className))
                    .price(parsePrice(element.getAttribute("data-price")))
                    .status(AreaStatus.ACTIVE)
                    .build());
        }
    }

    private String normalizeSvgFile(MultipartFile svgFile) {
        try {
            return normalizeSvgText(new String(svgFile.getBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalArgumentException("SVG 파일을 읽을 수 없습니다.", e);
        }
    }

    private boolean containsClass(String className, String target) {
        if (!StringUtils.hasText(className)) return false;
        for (String token : className.split("\\s+")) {
            if (target.equals(token)) return true;
        }
        return false;
    }

    private String normalizeId(String id) {
        if (!StringUtils.hasText(id)) return null;
        return id
                .replaceFirst("^area-2f-", "")
                .replaceFirst("^area-1f-", "")
                .replaceFirst("^area-vip-", "")
                .replaceFirst("^area-floor-", "")
                .replaceFirst("^area-", "");
    }

    private String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

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

    private Integer parsePrice(String price) {
        if (!StringUtils.hasText(price)) return 0;
        String digits = price.replaceAll("[^0-9]", "");
        return StringUtils.hasText(digits) ? Integer.parseInt(digits) : 0;
    }

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
