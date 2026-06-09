package dev.bum.auth_service.jpa;

import dev.bum.common.kafka.user.UserDtoForEvent;

public interface AuthRepository {
    void insert(UserDtoForEvent event);
    Auth findById(Long id);
    Auth findByUserId(String userId);
    void isExist(String userId);
    void update(UserDtoForEvent event);
    void delete(String userId);
}
