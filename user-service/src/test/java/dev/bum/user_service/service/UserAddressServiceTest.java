package dev.bum.user_service.service;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.user.address.dto.DeleteUserAddressBulkRequest;
import dev.bum.common.service.user.address.dto.InsertUserAddressRequest;
import dev.bum.common.service.user.address.dto.UpdateUserAddressRequest;
import dev.bum.common.service.user.address.dto.UserAddressCondRequest;
import dev.bum.common.service.user.address.dto.UserAddressResponse;
import dev.bum.common.service.user.address.enums.AddressStatus;
import dev.bum.common.service.user.user.enums.UserRole;
import dev.bum.user_service.jpa.address.UserAddress;
import dev.bum.user_service.jpa.address.UserAddressRepository;
import dev.bum.user_service.jpa.user.User;
import dev.bum.user_service.service.address.UserAddressService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserAddressServiceTest {

    @InjectMocks
    private UserAddressService userAddressService;

    @Mock
    private UserAddressRepository repository;

    @Test
    @DisplayName("주소 등록")
    void address_insert() {
        InsertUserAddressRequest info = insertRequest("user01", true);
        UserAddress address = address(1L, user("user01"), true, AddressStatus.ACTIVE);

        given(repository.insert("user01", info)).willReturn(address);

        UserAddressResponse response = userAddressService.insert(null, info);

        assertThat(response.getAddressId()).isEqualTo(1L);
        assertThat(response.getUserId()).isEqualTo("user01");
        then(repository).should().insert("user01", info);
    }

    @Test
    @DisplayName("경로 사용자 ID가 있으면 본문 사용자 ID보다 우선 사용")
    void address_insert_prefers_path_user_id() {
        InsertUserAddressRequest info = insertRequest("body-user", true);
        UserAddress address = address(1L, user("path-user"), true, AddressStatus.ACTIVE);

        given(repository.insert("path-user", info)).willReturn(address);

        UserAddressResponse response = userAddressService.insert("path-user", info);

        assertThat(response.getUserId()).isEqualTo("path-user");
        then(repository).should().insert("path-user", info);
    }

    @Test
    @DisplayName("사용자 ID 없이 주소 등록 시 예외 발생")
    void address_insert_fail_without_user_id() {
        InsertUserAddressRequest info = insertRequest(null, true);

        assertThatThrownBy(() -> userAddressService.insert(null, info))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ID로 주소 조회")
    void address_select_by_id() {
        UserAddress address = address(1L, user("user01"), true, AddressStatus.ACTIVE);

        given(repository.selectById(1L)).willReturn(address);

        UserAddressResponse response = userAddressService.selectById(1L);

        assertThat(response.getAddressId()).isEqualTo(1L);
        then(repository).should().selectById(1L);
    }

    @Test
    @DisplayName("조건으로 주소 조회")
    void address_select_by_cond() {
        UserAddressCondRequest cond = UserAddressCondRequest.builder()
                .userId("user01")
                .page(0)
                .size(10)
                .sort(List.of("addressId-desc"))
                .build();
        UserAddress address = address(1L, user("user01"), true, AddressStatus.ACTIVE);

        given(repository.selectByCond(any(), any(Pageable.class))).willReturn(new PageImpl<>(List.of(address)));

        CustomPageResponse<UserAddressResponse> response = userAddressService.selectByCond(cond);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getPage().getTotalElements()).isEqualTo(1);
        then(repository).should().selectByCond(
                eq(cond),
                argThat(pageable -> pageable.getPageNumber() == 0
                        && pageable.getPageSize() == 10
                        && pageable.getSort().getOrderFor("addressId") != null)
        );
    }

    @Test
    @DisplayName("사용자 ID로 주소 조회 시 ACTIVE 상태 기본 적용")
    void address_select_by_user_id() {
        UserAddressCondRequest cond = UserAddressCondRequest.builder().build();
        UserAddress address = address(1L, user("user01"), true, AddressStatus.ACTIVE);

        given(repository.selectByCond(any(), any(Pageable.class))).willReturn(new PageImpl<>(List.of(address)));

        CustomPageResponse<UserAddressResponse> response = userAddressService.selectByUserId("user01", cond);

        assertThat(response.getContent()).hasSize(1);
        assertThat(cond.getUserId()).isEqualTo("user01");
        assertThat(cond.getStatus()).isEqualTo(AddressStatus.ACTIVE);
        then(repository).should().selectByCond(eq(cond), any(Pageable.class));
    }

    @Test
    @DisplayName("주소 수정")
    void address_update() {
        UpdateUserAddressRequest info = UpdateUserAddressRequest.builder().alias("office").build();
        UserAddress address = address(1L, user("user01"), true, AddressStatus.ACTIVE);

        given(repository.update(1L, info)).willReturn(address);

        UserAddressResponse response = userAddressService.update(1L, info);

        assertThat(response.getAddressId()).isEqualTo(1L);
        then(repository).should().update(1L, info);
    }

    @Test
    @DisplayName("내 주소 수정")
    void address_update_my_address() {
        UpdateUserAddressRequest info = UpdateUserAddressRequest.builder().alias("office").build();
        UserAddress address = address(1L, user("user01"), true, AddressStatus.ACTIVE);

        given(repository.selectById(1L)).willReturn(address);
        given(repository.update(1L, info)).willReturn(address);

        UserAddressResponse response = userAddressService.updateMyAddress("user01", 1L, info);

        assertThat(response.getAddressId()).isEqualTo(1L);
        then(repository).should().selectById(1L);
        then(repository).should().update(1L, info);
    }

    @Test
    @DisplayName("다른 사용자의 주소 수정 시 예외 발생")
    void address_update_my_address_fail_not_owner() {
        UpdateUserAddressRequest info = UpdateUserAddressRequest.builder().alias("office").build();
        UserAddress address = address(1L, user("user01"), true, AddressStatus.ACTIVE);

        given(repository.selectById(1L)).willReturn(address);

        assertThatThrownBy(() -> userAddressService.updateMyAddress("user02", 1L, info))
                .isInstanceOf(AccessDeniedException.class);

        then(repository).should().selectById(1L);
        then(repository).should(never()).update(1L, info);
    }

    @Test
    @DisplayName("주소 삭제")
    void address_delete() {
        UserAddress address = address(1L, user("user01"), false, AddressStatus.DELETED);

        given(repository.delete(1L)).willReturn(address);

        UserAddressResponse response = userAddressService.delete(1L);

        assertThat(response.getStatus()).isEqualTo(AddressStatus.DELETED);
        then(repository).should().delete(1L);
    }

    @Test
    @DisplayName("내 주소 삭제")
    void address_delete_my_address() {
        UserAddress address = address(1L, user("user01"), false, AddressStatus.DELETED);

        given(repository.selectById(1L)).willReturn(address);
        given(repository.delete(1L)).willReturn(address);

        UserAddressResponse response = userAddressService.deleteMyAddress("user01", 1L);

        assertThat(response.getStatus()).isEqualTo(AddressStatus.DELETED);
        then(repository).should().selectById(1L);
        then(repository).should().delete(1L);
    }

    @Test
    @DisplayName("다른 사용자의 주소 삭제 시 예외 발생")
    void address_delete_my_address_fail_not_owner() {
        UserAddress address = address(1L, user("user01"), true, AddressStatus.ACTIVE);

        given(repository.selectById(1L)).willReturn(address);

        assertThatThrownBy(() -> userAddressService.deleteMyAddress("user02", 1L))
                .isInstanceOf(AccessDeniedException.class);

        then(repository).should().selectById(1L);
        then(repository).should(never()).delete(1L);
    }

    @Test
    @DisplayName("주소 일괄 삭제")
    void address_delete_bulk() {
        DeleteUserAddressBulkRequest info = DeleteUserAddressBulkRequest.builder().addressIds(List.of(1L, 2L)).build();
        given(repository.delete(1L)).willReturn(address(1L, user("user01"), false, AddressStatus.DELETED));
        given(repository.delete(2L)).willReturn(address(2L, user("user01"), false, AddressStatus.DELETED));

        userAddressService.deleteBulk(info);

        then(repository).should().delete(1L);
        then(repository).should().delete(2L);
    }

    @Test
    @DisplayName("주소 일괄 삭제 시 ID 목록이 비어 있으면 예외 발생")
    void address_delete_bulk_fail_empty_ids() {
        DeleteUserAddressBulkRequest info = DeleteUserAddressBulkRequest.builder().addressIds(List.of()).build();

        assertThatThrownBy(() -> userAddressService.deleteBulk(info))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private InsertUserAddressRequest insertRequest(String userId, Boolean defaultAddress) {
        return InsertUserAddressRequest.builder()
                .userId(userId)
                .alias("home")
                .recipientName("User")
                .recipientPhone("01012345678")
                .zipCode("12345")
                .address("Seoul")
                .detailAddress("101")
                .defaultAddress(defaultAddress)
                .build();
    }

    private UserAddress address(Long addressId, User user, Boolean defaultAddress, AddressStatus status) {
        return UserAddress.builder()
                .addressId(addressId)
                .user(user)
                .alias("home")
                .recipientName("User")
                .recipientPhone("01012345678")
                .zipCode("12345")
                .address("Seoul")
                .detailAddress("101")
                .defaultAddress(defaultAddress)
                .status(status)
                .build();
    }

    private User user(String userId) {
        return User.builder()
                .id(1L)
                .userId(userId)
                .password("password")
                .role(UserRole.ROLE_USER)
                .name("User")
                .phoneNumber("01012345678")
                .email(userId + "@test.com")
                .isBlacklisted(false)
                .build();
    }
}
