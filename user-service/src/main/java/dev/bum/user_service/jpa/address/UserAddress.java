package dev.bum.user_service.jpa.address;

import dev.bum.common.service.user.address.dto.InsertUserAddressRequest;
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "address_id")
    private Long addressId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_pk", nullable = false)
    private User user;

    @Column(length = 50)
    private String alias;

    @Column(name = "recipient_name", nullable = false, length = 30)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    @Column(name = "zip_code", nullable = false, length = 10)
    private String zipCode;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(name = "detail_address", length = 255)
    private String detailAddress;

    @Column(name = "default_address", nullable = false)
    private Boolean defaultAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AddressStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public UserAddress(User user, InsertUserAddressRequest info, boolean defaultAddress) {
        this.user = user;
        this.alias = info.getAlias();
        this.recipientName = info.getRecipientName();
        this.recipientPhone = info.getRecipientPhone();
        this.zipCode = info.getZipCode();
        this.address = info.getAddress();
        this.detailAddress = info.getDetailAddress();
        this.defaultAddress = defaultAddress;
        this.status = AddressStatus.ACTIVE;
    }

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

    public void markDefault(boolean defaultAddress) {
        this.defaultAddress = defaultAddress;
    }

    public void delete() {
        this.status = AddressStatus.DELETED;
        this.defaultAddress = false;
    }

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
