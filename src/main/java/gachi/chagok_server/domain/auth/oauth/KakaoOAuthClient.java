package gachi.chagok_server.domain.auth.oauth;
import org.springframework.stereotype.Component;import org.springframework.web.client.RestClient;
import java.time.LocalDate;import java.util.Map;
@Component
public class KakaoOAuthClient {
 private final RestClient rest=RestClient.create();
 @SuppressWarnings("unchecked")
 public OAuthUserInfo verify(String access){
  Map<String,Object> user=rest.get().uri("https://kapi.kakao.com/v2/user/me").headers(h->h.setBearerAuth(access)).retrieve().body(Map.class);
  if(user==null||user.get("id")==null)throw new IllegalArgumentException("invalid Kakao identity");
  Map<String,Object> account=user.get("kakao_account") instanceof Map<?,?> a?(Map<String,Object>)a:Map.of();
  String name=account.get("name") instanceof String s&&!s.isBlank()?s:null;
  String phone=account.get("phone_number") instanceof String p?p.replaceAll("[^0-9]",""):null;
  if(phone!=null&&phone.startsWith("82"))phone="0"+phone.substring(2);
  LocalDate birth=null;
  try{String year=(String)account.get("birthyear"),md=(String)account.get("birthday");if(year!=null&&md!=null&&md.length()==4)birth=LocalDate.of(Integer.parseInt(year),Integer.parseInt(md.substring(0,2)),Integer.parseInt(md.substring(2)));}catch(Exception ignored){}
  return new OAuthUserInfo(user.get("id").toString(),name,phone,birth);
 }
}
