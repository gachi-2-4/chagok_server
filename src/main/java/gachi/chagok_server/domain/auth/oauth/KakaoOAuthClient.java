package gachi.chagok_server.domain.auth.oauth;
import gachi.chagok_server.global.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;import org.springframework.web.client.*;
import java.time.LocalDate;import java.util.Map;
@Component
public class KakaoOAuthClient {
 private final RestClient rest;
 private final long appId;
 private final String adminKey;
 public KakaoOAuthClient(RestClient.Builder builder,@Value("${auth.oauth.kakao.app-id}") long appId,@Value("${auth.oauth.kakao.admin-key:}") String adminKey){this.rest=builder.baseUrl("https://kapi.kakao.com").build();this.appId=appId;this.adminKey=adminKey;}
 /** 어드민 키가 설정돼 있어 연결 끊기를 호출할 수 있는지 */
 public boolean canUnlink(){return adminKey!=null&&!adminKey.isBlank();}
 /**
  * 카카오 연결 끊기(회원 탈퇴). 사용자 토큰이 아니라 앱 어드민 키로 카카오 회원 번호를 지정해 끊는다.
  * 어드민 키가 없으면 호출하지 않는다.
  */
 public void unlink(String kakaoUserId){
  if(!canUnlink())return;
  org.springframework.util.LinkedMultiValueMap<String,String> form=new org.springframework.util.LinkedMultiValueMap<>();
  form.add("target_id_type","user_id");form.add("target_id",kakaoUserId);
  rest.post().uri("/v1/user/unlink").header("Authorization","KakaoAK "+adminKey).contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toBodilessEntity();
 }
 @SuppressWarnings("unchecked")
 public OAuthUserInfo verify(String access){
  Map<String,Object> user;
  try{
   // 다른 앱에서 발급된 카카오 액세스 토큰으로 로그인하는 것(토큰 치환 공격)을 막기 위해 발급 앱 ID를 먼저 확인한다.
   Map<String,Object> info=rest.get().uri("/v1/user/access_token_info").headers(h->h.setBearerAuth(access)).retrieve().body(Map.class);
   if(info==null||!(info.get("app_id") instanceof Number id)||id.longValue()!=appId)throw new BusinessException(ErrorCode.UNAUTHORIZED);
   user=rest.get().uri("/v2/user/me").headers(h->h.setBearerAuth(access)).retrieve().body(Map.class);
  }catch(HttpClientErrorException e){throw new BusinessException(ErrorCode.UNAUTHORIZED,e);}
  if(user==null||user.get("id")==null)throw new BusinessException(ErrorCode.UNAUTHORIZED);
  Map<String,Object> account=user.get("kakao_account") instanceof Map<?,?> a?(Map<String,Object>)a:Map.of();
  String name=account.get("name") instanceof String s&&!s.isBlank()?s:null;
  String phone=account.get("phone_number") instanceof String p?p.replaceAll("[^0-9]",""):null;
  if(phone!=null&&phone.startsWith("82"))phone="0"+phone.substring(2);
  LocalDate birth=null;
  try{String year=(String)account.get("birthyear"),md=(String)account.get("birthday");if(year!=null&&md!=null&&md.length()==4)birth=LocalDate.of(Integer.parseInt(year),Integer.parseInt(md.substring(0,2)),Integer.parseInt(md.substring(2)));}catch(Exception ignored){}
  return new OAuthUserInfo(user.get("id").toString(),name,phone,birth);
 }
}
