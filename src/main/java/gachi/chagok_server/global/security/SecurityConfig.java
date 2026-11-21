package gachi.chagok_server.global.security;
import gachi.chagok_server.domain.auth.service.TokenService;
import gachi.chagok_server.domain.user.repository.UserRepository;
import org.springframework.context.annotation.*;import org.springframework.http.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
@Configuration
public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 // SSE 같은 비동기 응답의 재디스패치(ASYNC)는 이미 인증을 통과한 요청이라 다시 검사하지 않는다. 세션이 없어 다시 검사하면 응답 마무리에서 인증이 사라져 실패한다.
 /** AI 서버가 가져가는 GET 서류 상세·비교 결과·대화 메시지는 X-Internal-Key가 있을 때만 JWT 없이 컨트롤러까지 보낸다. 키 검증은 컨트롤러에서 한다. */
 static boolean isInternalDocumentRead(jakarta.servlet.http.HttpServletRequest request){return "GET".equals(request.getMethod())&&request.getHeader(InternalKeyVerifier.HEADER)!=null&&request.getRequestURI().matches("/api/v1/(documents/\\d+|comparisons/\\d+|chats/\\d+/messages)");}
 @Bean SecurityFilterChain filterChain(HttpSecurity http,JwtTokenProvider jwt,TokenService tokens,UserRepository users)throws Exception{
 return http.csrf(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable)
 .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
 // 내부 조회 API(GET /api/v1/user/{userId})는 JWT 대신 X-Internal-Key로 컨트롤러에서 검증한다. 숫자 ID만 열어 /me 등은 계속 JWT가 필요하다.
 .authorizeHttpRequests(a->a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC).permitAll().requestMatchers(SecurityConfig::isInternalDocumentRead).permitAll().requestMatchers("/api/v1/auth/**").permitAll().requestMatchers(HttpMethod.GET,"/api/v1/user/{userId:\\d+}").permitAll().anyRequest().authenticated())
 .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->{res.setStatus(401);res.setContentType(MediaType.APPLICATION_JSON_VALUE);res.setCharacterEncoding("UTF-8");res.getWriter().write("{\"success\":false,\"data\":null,\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"인증이 필요합니다.\"}}");}))
 .addFilterBefore(new JwtAuthenticationFilter(jwt,tokens,users),UsernamePasswordAuthenticationFilter.class).build();
 }
}
