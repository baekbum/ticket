package dev.bum.auth_service.jpa;

import dev.bum.auth_service.exception.UserAlreadyExistException;
import dev.bum.auth_service.exception.UserNotExistException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class AuthRepositoryImpl implements AuthRepository {

    private final AuthJpaRepository jpaRepository;

    @Override
    public void isExist(String userId) {
        if (jpaRepository.findByUserId(userId).isPresent()) {
            throw new UserAlreadyExistException("이미 해당 유저가 존재합니다.");
        }
    }

    @Override
    public void insert(Auth auth) {
        isExist(auth.getUserId());

        jpaRepository.save(auth);
    }

    @Override
    public Auth findById(Long id) {
        return jpaRepository.findById(id)
                .orElseThrow(() -> new UserNotExistException("해당 유저를 발견하지 못했습니다."));
    }

    @Override
    public Auth findByUserId(String userId) {
        return jpaRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotExistException("해당 유저를 발견하지 못했습니다."));
    }

    @Override
    public void delete(String userId) {
        Auth foundUser = findByUserId(userId);

        jpaRepository.delete(foundUser);
    }
}
