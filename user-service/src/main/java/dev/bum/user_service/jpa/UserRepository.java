package dev.bum.user_service.jpa;

import dev.bum.common.service.user.dto.InsertUserRequest;
import dev.bum.common.service.user.dto.UpdateUserRequest;
import dev.bum.common.service.user.dto.UserCondRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserRepository {
    User insert(InsertUserRequest info);
    void isExist(String userId);
    Page<User> selectAll(Pageable pageable);
    User selectById(String userId);
    Page<User> selectByCond(UserCondRequest cond, Pageable pageable);
    User update(String userId, UpdateUserRequest info);
    User delete(String userId);
}
