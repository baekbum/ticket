package dev.bum.user_service.jpa;

import dev.bum.common.service.user.vo.InsertUserInfo;
import dev.bum.common.service.user.vo.UpdateUserInfo;
import dev.bum.common.service.user.vo.UserCond;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserRepository {
    User insert(InsertUserInfo info);
    void isExist(String userId);
    Page<User> selectAll(Pageable pageable);
    User selectById(String userId);
    Page<User> selectByCond(UserCond cond, Pageable pageable);
    User update(String userId, UpdateUserInfo info);
    User delete(String userId);
}
