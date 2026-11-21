package gachi.chagok_server.domain.auth.oauth;
import gachi.chagok_server.global.exception.*;
import org.springframework.beans.factory.annotation.Value;import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.stereotype.Component;
@Component
public class GoogleOAuthClient {
 private final JwtDecoder decoder;
 public GoogleOAuthClient(@Value("${auth.oauth.google.client-id}") String audience){
  NimbusJwtDecoder d=NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build();
  OAuth2TokenValidator<Jwt> validator=token->{
   String iss=token.getClaimAsString("iss");java.util.List<String> aud=token.getAudience();
   boolean ok=("https://accounts.google.com".equals(iss)||"accounts.google.com".equals(iss))&&aud!=null&&aud.contains(audience);
   return ok?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_id_token","Invalid issuer or audience",null));
  };d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(),validator));decoder=d;
 }
 public OAuthUserInfo verify(String token){
  Jwt jwt;
  try{jwt=decoder.decode(token);}catch(BadJwtException e){throw new BusinessException(ErrorCode.UNAUTHORIZED,e);}
  return new OAuthUserInfo(jwt.getSubject(),jwt.getClaimAsString("name"),null,null);
 }
}
