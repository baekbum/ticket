package dev.bum.admin_service.controller.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.admin_service.feign.user.UserServiceClient;
import dev.bum.admin_service.security.SecurityConfig;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.user.address.dto.UserAddressCondRequest;
import dev.bum.common.service.user.address.dto.UserAddressResponse;
import dev.bum.common.service.user.address.dto.UpdateUserAddressRequest;
import dev.bum.common.service.user.address.enums.AddressStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({JwtAuthenticationFilter.class, SecurityConfig.class})
@WebMvcTest(AdminUserAddressController.class)
class AdminUserAddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserServiceClient userServiceClient;

    private final String baseUrl = "/api/v1/user";

    @Test
    @DisplayName("인증 정보 없이 사용자 주소 조회 요청 시 4xx 응답")
    void token_invalid() throws Exception {
        mockMvc.perform(get(baseUrl + "/address/delete/id/1"))
                .andExpect(status().is4xxClientError());
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("사용자 주소 조회")
    void user_address_select() throws Exception {
        UserAddressCondRequest cond = UserAddressCondRequest.builder().build();
        CustomPageResponse<UserAddressResponse> response = CustomPageResponse.of(List.of(addressResponse()), 10, 0, 1, 1);

        given(userServiceClient.selectAddressByUserId("user01", cond)).willReturn(response);

        mockMvc.perform(post(baseUrl + "/address/select/user/user01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].addressId").value(1L));

        then(userServiceClient).should().selectAddressByUserId("user01", cond);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("사용자 주소 수정")
    void user_address_update() throws Exception {
        UpdateUserAddressRequest info = UpdateUserAddressRequest.builder().alias("home").build();
        given(userServiceClient.updateAddress(1L, info)).willReturn(addressResponse());

        mockMvc.perform(put(baseUrl + "/address/update/id/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addressId").value(1L));

        then(userServiceClient).should().updateAddress(1L, info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("사용자 주소 삭제")
    void user_address_delete() throws Exception {
        given(userServiceClient.deleteAddress(1L)).willReturn(addressResponse());

        mockMvc.perform(delete(baseUrl + "/address/delete/id/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addressId").value(1L));

        then(userServiceClient).should().deleteAddress(1L);
    }

    private UserAddressResponse addressResponse() {
        return UserAddressResponse.builder()
                .addressId(1L)
                .userId("user01")
                .alias("home")
                .address("Seoul")
                .status(AddressStatus.ACTIVE)
                .build();
    }
}
