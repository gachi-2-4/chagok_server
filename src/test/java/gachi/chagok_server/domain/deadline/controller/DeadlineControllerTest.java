package gachi.chagok_server.domain.deadline.controller;

import gachi.chagok_server.domain.auth.service.TokenService;
import gachi.chagok_server.domain.deadline.dto.DeadlineDtos.UpdateRequest;
import gachi.chagok_server.domain.deadline.entity.DeadlineType;
import gachi.chagok_server.domain.deadline.entity.Recurrence;
import gachi.chagok_server.domain.deadline.service.DeadlineService;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.domain.user.repository.UserRepository;
import gachi.chagok_server.global.security.JwtTokenProvider;
import gachi.chagok_server.global.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeadlineController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class})
@TestPropertySource(properties = {
        "auth.jwt.secret=test-secret-test-secret-test-secret-0123456789",
        "chagok.internal.key=internal-test-key"
})
class DeadlineControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @MockitoBean DeadlineService deadlineService;
    @MockitoBean TokenService tokenService;
    @MockitoBean UserRepository userRepository;

    private String bearer() {
        String token = jwtTokenProvider.issue(1L);
        given(tokenService.isRevoked(token)).willReturn(false);
        given(userRepository.findById(1L)).willReturn(Optional.of(User.phoneUser("01012345678", "hash", "홍길동")));
        return "Bearer " + token;
    }

    private UpdateRequest patchBody(String json) throws Exception {
        mockMvc.perform(patch("/api/v1/deadlines/72").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk());
        ArgumentCaptor<UpdateRequest> captor = ArgumentCaptor.forClass(UpdateRequest.class);
        verify(deadlineService).update(eq(1L), eq(72L), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("recurrence·endDate·amount에 null을 보내면 비우겠다는 뜻으로 서비스에 알린다")
    void explicitNullsAreCleared() throws Exception {
        // when
        UpdateRequest request = patchBody("{\"label\":\"월세\",\"recurrence\":null,\"endDate\":null,\"amount\":null}");

        // then
        assertThat(request.label()).isEqualTo("월세");
        assertThat(request.clears("recurrence")).isTrue();
        assertThat(request.clears("endDate")).isTrue();
        assertThat(request.clears("amount")).isTrue();
        assertThat(request.recurrence()).isNull();
    }

    @Test
    @DisplayName("보내지 않은 항목은 비우지 않는다")
    void absentIsNotCleared() throws Exception {
        // when
        UpdateRequest request = patchBody("{\"date\":\"2027-03-01\",\"recurrence\":\"MONTHLY\"}");

        // then
        assertThat(request.date()).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(request.recurrence()).isEqualTo(Recurrence.MONTHLY);
        assertThat(request.clears("recurrence")).isFalse();
        assertThat(request.clears("endDate")).isFalse();
        assertThat(request.clears("amount")).isFalse();
    }

    @Test
    @DisplayName("요청 JSON의 clear 값은 읽지 않는다 (비우기는 null로만 표현)")
    void clientCannotSetClearDirectly() throws Exception {
        // when
        UpdateRequest request = patchBody("{\"clear\":[\"amount\"],\"label\":\"월세\"}");

        // then
        assertThat(request.clears("amount")).isFalse();
    }

    @Test
    @DisplayName("코드표에 없는 값이나 날짜 형식 오류는 500이 아닌 400")
    void invalidValuesAre400() throws Exception {
        mockMvc.perform(patch("/api/v1/deadlines/72").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"WHATEVER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        mockMvc.perform(patch("/api/v1/deadlines/72").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"내일\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        mockMvc.perform(patch("/api/v1/deadlines/72").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(deadlineService);
    }

    @Test
    @DisplayName("기한 종류는 보낸 그대로 전달한다")
    void typePassedThrough() throws Exception {
        UpdateRequest request = patchBody("{\"type\":\"ACTION\"}");

        assertThat(request.type()).isEqualTo(DeadlineType.ACTION);
        verify(deadlineService).update(eq(1L), eq(72L), any(UpdateRequest.class));
    }
}
