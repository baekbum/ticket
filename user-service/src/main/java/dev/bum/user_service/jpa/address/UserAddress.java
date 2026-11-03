package dev.bum.user_service.jpa.address;

import dev.bum.common.service.user.address.dto.UpdateUserAddressRequest;
import dev.bum.common.service.user.address.dto.UserAddressResponse;
import dev.bum.common.service.user.address.enums.AddressStatus;
import dev.bum.user_service.jpa.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_addresses", indexes = {
        @Index(name = "idx_user_addresses_user_pk", columnList = "user_pk"),
        @Index(name = "idx_user_addresses_status", columnList = "status"),
        @Index(name = "idx_user_addresses_default", columnList = "user_pk, default_address")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAddress {

    // 배송지 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "address_id")
    private Long addressId;

    // 사용자 정보.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_pk", nullable = false)
    private User user;

    // 표시용 별칭.
    @Column(length = 50)
    private String alias;

    // 수령인 이름.
    @Column(name = "recipient_name", nullable = false, length = 30)
    private String recipientName;

    // 수령인 연락처.
    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    // 우편번호.
    @Column(name = "zip_code", nullable = false, length = 10)
    private String zipCode;

    // 주소.
    @Column(nullable = false, length = 255)
    private String address;

    // 상세 주소.
    @Column(name = "detail_address", length = 255)
    private String detailAddress;

    // 기본 배송지 여부.
    @Column(name = "default_address", nullable = false)
    private Boolean defaultAddress;

    // 사용자 배송지 상태.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AddressStatus status;

    // 생성 시각.
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // 마지막 수정 시각.
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 사용자 배송지를 수정한다.
    public void updateInfo(UpdateUserAddressRequest info) {

        if (StringUtils.hasText(info.getAlias())) {
            this.alias = info.getAlias();
        }

        if (StringUtils.hasText(info.getRecipientName())) {
            this.recipientName = info.getRecipientName();
        }

        if (StringUtils.hasText(info.getRecipientPhone())) {
            this.recipientPhone = info.getRecipientPhone();
        }

        if (StringUtils.hasText(info.getZipCode())) {
            this.zipCode = info.getZipCode();
        }

        if (StringUtils.hasText(info.getAddress())) {
            this.address = info.getAddress();
        }

        if (info.getDetailAddress() != null) {
            this.detailAddress = info.getDetailAddress();
        }

        if (info.getDefaultAddress() != null) {
            this.defaultAddress = info.getDefaultAddress();
        }

        if (info.getStatus() != null) {
            this.status = info.getStatus();
        }
    }

    // 배송지를 기본 배송지로 지정한다.
    public void markDefault(boolean defaultAddress) {

        this.defaultAddress = defaultAddress;
    }

    // 사용자 배송지를 삭제한다.
    public void delete() {

        this.status = AddressStatus.DELETED;
        this.defaultAddress = false;
    }

    // 외부 응답에 사용할 DTO로 변환한다.
    public UserAddressResponse toResponse() {

        return UserAddressResponse.builder()
                .addressId(this.addressId)
                .userPk(this.user != null ? this.user.getId() : null)
                .userId(this.user != null ? this.user.getUserId() : null)
                .alias(this.alias)
                .recipientName(this.recipientName)
                .recipientPhone(this.recipientPhone)
                .zipCode(this.zipCode)
                .address(this.address)
                .detailAddress(this.detailAddress)
                .defaultAddress(this.defaultAddress)
                .status(this.status)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt)
                .build();
    }
}
