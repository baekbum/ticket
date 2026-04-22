package dev.bum.auth_service.jpa;

public interface AuthRepository {
    void insert(Auth auth);
    Auth findById(Long id);
    Auth findByUserId(String userId);
    void isExist(String userId);
    void delete(String userId);
}
