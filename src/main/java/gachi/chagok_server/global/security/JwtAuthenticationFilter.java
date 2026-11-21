package gachi.chagok_server.global.security;
import gachi.chagok_server.domain.auth.service.TokenService;
import gachi.chagok_server.domain.user.repository.UserRepository;
import jakarta.servlet.*;import jakarta.servlet.http.*;
import io.jsonwebtoken.JwtException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;import java.util.List;
public class JwtAuthenticationFilter extends OncePerRequestFilter {
 private final JwtTokenProvider jwt;private final TokenService tokens;private final UserRepository users;
 public JwtAuthenticationFilter(JwtTokenProvider jwt,TokenService tokens,UserRepository users){this.jwt=jwt;this.tokens=tokens;this.users=users;}
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
 String auth=req.getHeader("Authorization");if(auth!=null&&auth.startsWith("Bearer "))try{
 String token=auth.substring(7);Long id=jwt.userId(token);if(!tokens.isRevoked(token)&&users.findById(id).filter(u->!u.isDeleted()).isPresent())SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id,null,List.of()));
 }catch(JwtException|IllegalArgumentException e){SecurityContextHolder.clearContext();}
 chain.doFilter(req,res);
 }
}
