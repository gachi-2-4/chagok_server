package gachi.chagok_server.domain.chat.controller;

import gachi.chagok_server.domain.chat.dto.ChatDtos.*;
import gachi.chagok_server.domain.chat.service.ChatService;
import gachi.chagok_server.global.common.ApiResponse;
import gachi.chagok_server.global.common.PageResponse;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import gachi.chagok_server.global.security.InternalKeyVerifier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chats")
public class ChatController {

    private final ChatService chatService;
    private final InternalKeyVerifier internalKey;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateResponse>> create(@AuthenticationPrincipal Long userId,
                                                              @RequestBody(required = false) LinkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(chatService.create(userId, request)));
    }

    @GetMapping
    public ApiResponse<PageResponse<ListItem>> list(@AuthenticationPrincipal Long userId,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(chatService.list(userId, keyword, page, size));
    }

    /**
     * 대화 메시지 조회. 앱은 Bearer 토큰으로, AI 서버(서류 기반 채팅의 최근 대화 조회)는 X-Internal-Key + userId 쿼리로 부른다.
     */
    @GetMapping("/{id}/messages")
    public ApiResponse<MessagesResponse> messages(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                                  @RequestParam(required = false) Long beforeMessageId,
                                                  @RequestParam(defaultValue = "30") int size,
                                                  @RequestHeader(value = InternalKeyVerifier.HEADER, required = false) String key,
                                                  @RequestParam(name = "userId", required = false) Long ownerId) {
        if (key != null) {
            internalKey.verify(key);
            if (ownerId == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            return ApiResponse.ok(chatService.messages(ownerId, id, beforeMessageId, size, false));
        }
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.ok(chatService.messages(userId, id, beforeMessageId, size, true));
    }

    @PutMapping("/{id}/documents")
    public ApiResponse<LinksResponse> replaceDocuments(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                                       @RequestBody(required = false) LinkRequest request) {
        return ApiResponse.ok(chatService.replaceDocuments(userId, id, request));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<AskResponse>> ask(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                                        @Valid @RequestBody AskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(chatService.ask(userId, id, request.content())));
    }

    /**
     * stream=true: 답변을 SSE(delta → citations → done)로 준다.
     * AI 서버가 아직 스트리밍을 주지 않아, 답변을 모두 받은 뒤 같은 형식의 이벤트로 보낸다 (앱은 같은 방식으로 받으면 된다).
     * 오류는 스트림이 시작되기 전에 나므로 일반 오류 응답으로 준다.
     */
    @PostMapping(value = "/{id}/messages", params = "stream=true")
    public SseEmitter askStreaming(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                   @Valid @RequestBody AskRequest request) throws IOException {
        AskResponse response = chatService.ask(userId, id, request.content());
        MessageView answer = response.answer();

        SseEmitter emitter = new SseEmitter();
        emitter.send(SseEmitter.event().name("delta").data(Map.of("text", answer.content())));
        emitter.send(SseEmitter.event().name("citations").data(Map.of("citations", answer.citations())));
        emitter.send(SseEmitter.event().name("done").data(Map.of(
                "questionMessageId", response.question().messageId(),
                "answerMessageId", answer.messageId(),
                "answerType", answer.answerType() == null ? "" : answer.answerType(),
                "followUpQuestions", answer.followUpQuestions() == null ? List.of() : answer.followUpQuestions())));
        emitter.complete();
        return emitter;
    }
}
