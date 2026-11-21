package gachi.chagok_server.domain.user.controller;
import gachi.chagok_server.domain.user.dto.UserDtos.InternalInfo;
import gachi.chagok_server.domain.user.service.UserService;
import gachi.chagok_server.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
@RestController @RequestMapping("/api/v1/user")
public class InternalUserController {
 private final UserService service;private final byte[] internalKey;
 public InternalUserController(UserService service,@Value("${chagok.internal.key}") String key){this.service=service;this.internalKey=key.getBytes(StandardCharsets.UTF_8);}
 @GetMapping("/{userId}") public ResponseEntity<ApiResponse<InternalInfo>> internal(@PathVariable Long userId,@RequestHeader(value="X-Internal-Key",required=false) String key){if(key==null||!MessageDigest.isEqual(internalKey,key.getBytes(StandardCharsets.UTF_8)))return ResponseEntity.status(403).build();return ResponseEntity.ok(ApiResponse.ok(service.internal(userId)));}
}
