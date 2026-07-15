package dev.bum.user_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.user.address.dto.DeleteUserAddressBulkRequest;
import dev.bum.common.service.user.address.dto.InsertUserAddressRequest;
import dev.bum.common.service.user.address.dto.UpdateUserAddressRequest;
import dev.bum.common.service.user.address.dto.UserAddressCondRequest;
import dev.bum.common.service.user.address.dto.UserAddressResponse;
import dev.bum.common.service.user.address.enums.AddressStatus;
import dev.bum.user_service.controller.address.UserAddressController;
import dev.bum.user_service.security.SecurityConfig;
import dev.bum.user_service.service.address.UserAddressService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({JwtAuthenticationFilter.class, SecurityConfig.class})
@WebMvcTest(UserAddressController.class)
class UserAddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserAddressService userAddressService;

    private final String baseUrl = "/api/v1/address";

    @Test
    @DisplayName("인증 정보 없이 주소 조회 요청 시 4xx 응답")
    void token_invalid() throws Exception {
        mockMvc.perform(get(baseUrl + "/select/id/1"))
                .andExpect(status().is4xxClientError());
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("주소 등록")
    void address_insert() throws Exception {
        InsertUserAddressRequest info = insertRequest("user01");
        given(userAddressService.insert(null, info)).willReturn(addressResponse());

        mockMvc.perform(post(baseUrl + "/insert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addressId").value(1L));

        then(userAddressService).should().insert(null, info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("사용자 ID로 주소 등록")
    void address_insert_by_user_id() throws Exception {
        InsertUserAddressRequest info = insertRequest(null);
        given(userAddressService.insert("user01", info)).willReturn(addressResponse());

        mockMvc.perform(post(baseUrl + "/insert/user/user01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user01"));

        then(userAddressService).should().insert("user01", info);
    }

    @Test
    @DisplayName("내 주소 등록")
    void address_insert_me() throws Exception {
        InsertUserAddressRequest info = insertRequest(null);
        given(userAddressService.insert("user01", info)).willReturn(addressResponse());

        mockMvc.perform(post(baseUrl + "/insert/me")
                        .with(authentication(adminAuthentication("user01")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user01"));

        then(userAddressService).should().insert("user01", info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("ID로 주소 조회")
    void address_select_by_id() throws Exception {
        given(userAddressService.selectById(1L)).willReturn(addressResponse());

        mockMvc.perform(get(baseUrl + "/select/id/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addressId").value(1L));

        then(userAddressService).should().selectById(1L);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("조건으로 주소 조회")
    void address_select_by_cond() throws Exception {
        UserAddressCondRequest cond = UserAddressCondRequest.builder().userId("user01").build();
        CustomPageResponse<UserAddressResponse> response = CustomPageResponse.of(List.of(addressResponse()), 10, 0, 1, 1);
        given(userAddressService.selectByCond(any())).willReturn(response);

        mockMvc.perform(post(baseUrl + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].addressId").value(1L));

        then(userAddressService).should().selectByCond(cond);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("사용자 ID로 주소 조회")
    void address_select_by_user_id() throws Exception {
        UserAddressCondRequest cond = UserAddressCondRequest.builder().build();
        CustomPageResponse<UserAddressResponse> response = CustomPageResponse.of(List.of(addressResponse()), 10, 0, 1, 1);
        given(userAddressService.selectByUserId("user01", cond)).willReturn(response);

        mockMvc.perform(post(baseUrl + "/select/user/user01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value("user01"));

        then(userAddressService).should().selectByUserId("user01", cond);
    }

    @Test
    @DisplayName("내 주소 조회")
    void address_select_me() throws Exception {
        UserAddressCondRequest cond = UserAddressCondRequest.builder().build();
        CustomPageResponse<UserAddressResponse> response = CustomPageResponse.of(List.of(addressResponse()), 10, 0, 1, 1);
        given(userAddressService.selectByUserId("user01", cond)).willReturn(response);

        mockMvc.perform(post(baseUrl + "/select/me")
                        .with(authentication(adminAuthentication("user01")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value("user01"));

        then(userAddressService).should().selectByUserId("user01", cond);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("주소 수정")
    void address_update() throws Exception {
        UpdateUserAddressRequest info = UpdateUserAddressRequest.builder().alias("home").build();
        given(userAddressService.update(1L, info)).willReturn(addressResponse());

        mockMvc.perform(put(baseUrl + "/update/id/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addressId").value(1L));

        then(userAddressService).should().update(1L, info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("주소 삭제")
    void address_delete() throws Exception {
        given(userAddressService.delete(1L)).willReturn(addressResponse());

        mockMvc.perform(delete(baseUrl + "/delete/id/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addressId").value(1L));

        then(userAddressService).should().delete(1L);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("주소 벌크 삭제")
    void address_delete_bulk() throws Exception {
        DeleteUserAddressBulkRequest info = DeleteUserAddressBulkRequest.builder().addressIds(List.of(1L, 2L)).build();

        mockMvc.perform(delete(baseUrl + "/delete/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(userAddressService).should().deleteBulk(info);
    }

    private InsertUserAddressRequest insertRequest(String userId) {
        return InsertUserAddressRequest.builder()
                .userId(userId)
                .alias("home")
                .recipientName("User")
                .recipientPhone("01012345678")
                .zipCode("12345")
                .address("Seoul")
                .detailAddress("101")
                .defaultAddress(true)
                .build();
    }

    private UserAddressResponse addressResponse() {
        return UserAddressResponse.builder()
                .addressId(1L)
                .userId("user01")
                .alias("home")
                .recipientName("User")
                .recipientPhone("01012345678")
                .zipCode("12345")
                .address("Seoul")
                .detailAddress("101")
                .defaultAddress(true)
                .status(AddressStatus.ACTIVE)
                .build();
    }

    private UsernamePasswordAuthenticationToken adminAuthentication(String userId) {
        return new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }
}
