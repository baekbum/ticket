package dev.bum.user_service.service.address;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.user.address.dto.*;
import dev.bum.common.service.user.address.enums.AddressStatus;
import dev.bum.user_service.audit.AuditLog;
import dev.bum.user_service.jpa.address.UserAddress;
import dev.bum.user_service.jpa.address.UserAddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserAddressService {

    private final UserAddressRepository repository;

    @AuditLog(action = "USER_ADDRESS_CREATE", targetType = "USER_ADDRESS")
    public UserAddressResponse insert(String userId, InsertUserAddressRequest info) {
        String targetUserId = resolveUserId(userId, info.getUserId());
        log.info("[INSERT ADDRESS] userId : {}, info : {}", targetUserId, info);
        return repository.insert(targetUserId, info).toResponse();
    }

    @Transactional(readOnly = true)
    public UserAddressResponse selectById(Long addressId) {
        log.info("[SELECT ADDRESS] addressId : {}", addressId);
        return repository.selectById(addressId).toResponse();
    }

    @Transactional(readOnly = true)
    public CustomPageResponse<UserAddressResponse> selectByCond(UserAddressCondRequest cond) {
        log.info("[SELECT ADDRESS] {}", cond);
        PageRequest pageRequest = PageRequest.of(cond.getPage(), cond.getSize(), makeSortInfo(cond.getSort()));
        Page<UserAddressResponse> addressPage = repository.selectByCond(cond, pageRequest).map(UserAddress::toResponse);

        return CustomPageResponse.of(
                addressPage.getContent(),
                addressPage.getSize(),
                addressPage.getNumber(),
                addressPage.getTotalElements(),
                addressPage.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public CustomPageResponse<UserAddressResponse> selectByUserId(String userId, UserAddressCondRequest cond) {
        cond.setUserId(userId);
        if (cond.getStatus() == null) {
            cond.setStatus(AddressStatus.ACTIVE);
        }
        return selectByCond(cond);
    }

    @AuditLog(action = "USER_ADDRESS_UPDATE", targetType = "USER_ADDRESS")
    public UserAddressResponse update(Long addressId, UpdateUserAddressRequest info) {
        log.info("[UPDATE ADDRESS] addressId : {}, info : {}", addressId, info);
        return repository.update(addressId, info).toResponse();
    }

    @AuditLog(action = "USER_ADDRESS_UPDATE", targetType = "USER_ADDRESS")
    public UserAddressResponse updateMyAddress(String currentUserId, Long addressId, UpdateUserAddressRequest info) {
        UserAddress address = repository.selectById(addressId);
        validateOwner(currentUserId, address);
        log.info("[UPDATE MY ADDRESS] userId : {}, addressId : {}, info : {}", currentUserId, addressId, info);
        return repository.update(addressId, info).toResponse();
    }

    @AuditLog(action = "USER_ADDRESS_DELETE", targetType = "USER_ADDRESS")
    public UserAddressResponse delete(Long addressId) {
        log.info("[DELETE ADDRESS] addressId : {}", addressId);
        return repository.delete(addressId).toResponse();
    }

    @AuditLog(action = "USER_ADDRESS_DELETE", targetType = "USER_ADDRESS")
    public UserAddressResponse deleteMyAddress(String currentUserId, Long addressId) {
        UserAddress address = repository.selectById(addressId);
        validateOwner(currentUserId, address);
        log.info("[DELETE MY ADDRESS] userId : {}, addressId : {}", currentUserId, addressId);
        return repository.delete(addressId).toResponse();
    }

    @AuditLog(action = "USER_ADDRESS_DELETE_BULK", targetType = "USER_ADDRESS")
    public void deleteBulk(DeleteUserAddressBulkRequest info) {
        if (info.getAddressIds() == null || info.getAddressIds().isEmpty()) {
            throw new IllegalArgumentException("삭제할 배송지 정보가 없습니다.");
        }
        log.info("[BULK DELETE ADDRESS] addressIds : {}", info.getAddressIds());
        info.getAddressIds().forEach(this::delete);
    }

    private String resolveUserId(String pathUserId, String bodyUserId) {
        if (StringUtils.hasText(pathUserId)) {
            return pathUserId;
        }
        if (StringUtils.hasText(bodyUserId)) {
            return bodyUserId;
        }
        throw new IllegalArgumentException("배송지를 등록할 사용자 ID가 없습니다.");
    }

    private void validateOwner(String currentUserId, UserAddress address) {
        String addressOwnerId = address.getUser().getUserId();
        if (!StringUtils.hasText(currentUserId) || !currentUserId.equals(addressOwnerId)) {
            throw new AccessDeniedException("본인 주소만 수정하거나 삭제할 수 있습니다.");
        }
    }

    private Sort makeSortInfo(List<String> sorts) {
        Sort sort = Sort.unsorted();
        if (sorts != null && !sorts.isEmpty()) {
            List<Sort.Order> orders = new ArrayList<>();

            for (String infoStr : sorts) {
                String[] infos = infoStr.split("-");

                if (infos.length == 2) {
                    String field = infos[0];
                    String direction = infos[1];
                    orders.add(new Sort.Order(Sort.Direction.fromString(direction), field));
                }
            }
            sort = Sort.by(orders);
        }

        return sort;
    }
}
