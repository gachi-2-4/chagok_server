package gachi.chagok_server.domain.auth.oauth;
import org.springframework.beans.factory.annotation.Value;import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.stereotype.Component;
@Component
public class GoogleOAuthClient {
 private final JwtDecoder decoder;
 public GoogleOAuthClient(@Value("${auth.oauth.google.client-id}") String audience){
  NimbusJwtDecoder d=NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build();
  OAuth2TokenValidator<Jwt> validator=token->{
   boolean ok=("https://accounts.google.com".equals(token.getIssuer().toString())||"accounts.google.com".equals(token.getIssuer().toString()))&&token.getAudience().contains(audience);
   return ok?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_id_token","Invalid issuer or audience",null));
  };d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(),validator));decoder=d;
 }
 public OAuthUserInfo verify(String token){Jwt jwt=decoder.decode(token);return new OAuthUserInfo(jwt.getSubject(),jwt.getClaimAsString("name"),null,null);}
}
