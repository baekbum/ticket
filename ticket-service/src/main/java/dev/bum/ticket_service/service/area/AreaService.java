package dev.bum.ticket_service.service.area;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.area.dto.AreaCondRequest;
import dev.bum.common.service.ticket.area.dto.AreaResponse;
import dev.bum.common.service.ticket.area.dto.InsertAreaRequest;
import dev.bum.common.service.ticket.area.dto.UpdateAreaRequest;
import dev.bum.ticket_service.jpa.area.Area;
import dev.bum.ticket_service.jpa.area.AreaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AreaService {

    private final AreaRepository repository;

    public AreaResponse insert(InsertAreaRequest info) {
        log.info("[AREA INSERT] {}", info);
        return repository.insert(info).toResponse();
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
}
