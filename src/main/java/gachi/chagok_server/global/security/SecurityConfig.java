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
 @Bean SecurityFilterChain filterChain(HttpSecurity http,JwtTokenProvider jwt,TokenService tokens,UserRepository users)throws Exception{
 return http.csrf(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable)
 .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
 // 내부 조회 API(GET /api/v1/user/{userId})는 JWT 대신 X-Internal-Key로 컨트롤러에서 검증한다. 숫자 ID만 열어 /me 등은 계속 JWT가 필요하다.
 .authorizeHttpRequests(a->a.requestMatchers("/api/v1/auth/**").permitAll().requestMatchers(HttpMethod.GET,"/api/v1/user/{userId:\\d+}").permitAll().anyRequest().authenticated())
 .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->{res.setStatus(401);res.setContentType(MediaType.APPLICATION_JSON_VALUE);res.setCharacterEncoding("UTF-8");res.getWriter().write("{\"success\":false,\"data\":null,\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"인증이 필요합니다.\"}}");}))
 .addFilterBefore(new JwtAuthenticationFilter(jwt,tokens,users),UsernamePasswordAuthenticationFilter.class).build();
 }
}
