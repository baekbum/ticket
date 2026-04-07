package dev.bum.auth_service.jpa;

public interface AuthRepository {
    Auth findById(Long id);
    Auth findByUserId(String userId);
}
