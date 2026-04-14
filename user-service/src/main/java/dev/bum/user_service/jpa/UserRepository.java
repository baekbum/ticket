package dev.bum.user_service.jpa;

import dev.bum.user_service.vo.InsertUserInfo;
import dev.bum.user_service.vo.UpdateUserInfo;
import dev.bum.user_service.vo.UserCond;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserRepository {
    User insert(InsertUserInfo info);
    Page<User> selectAll(Pageable pageable);
    User selectById(String userId);
    Page<User> selectByCond(UserCond cond, Pageable pageable);
    User update(String userId, UpdateUserInfo info);
    User delete(String userId);
}
