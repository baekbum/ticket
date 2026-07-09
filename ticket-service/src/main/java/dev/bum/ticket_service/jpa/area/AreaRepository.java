package dev.bum.ticket_service.jpa.area;

import dev.bum.common.service.ticket.area.dto.AreaCondRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaRequest;
import dev.bum.common.service.ticket.area.dto.UpdateAreaRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AreaRepository {
    Area insert(InsertAreaRequest info);
    void isExist(AreaCondRequest cond);
    Area selectById(Long id);
    Page<Area> selectByCond(AreaCondRequest cond, Pageable pageable);
    Area update(Long id, UpdateAreaRequest info);
    Area delete(Long id);
}
