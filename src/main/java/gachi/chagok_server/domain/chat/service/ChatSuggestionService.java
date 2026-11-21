package gachi.chagok_server.domain.chat.service;

import gachi.chagok_server.domain.ai.client.AiServerClient;
import gachi.chagok_server.domain.ai.dto.AiChatDtos.Suggestion;
import gachi.chagok_server.domain.document.entity.DocType;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 채팅창 위에 띄울 자주 묻는 질문 칩. 내 보관함 서류 종류를 기준으로 고르며, AI가 실패해도 채팅은 계속 쓸 수 있도록 빈 목록을 준다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSuggestionService {

    static final int SUGGESTION_COUNT = 3;

    private final DocumentRepository documents;
    private final AiServerClient ai;

    public List<Suggestion> forUser(Long userId) {
        try {
            Optional<Long> documentId = representativeDocument(userId);
            if (documentId.isEmpty()) {
                return List.of(); // AI 칩 API는 서류 ID가 필수라 서류가 없으면 받을 수 없다.
            }
            return ai.suggestions(userId, documentId.get(), SUGGESTION_COUNT);
        } catch (RuntimeException e) {
            log.warn("질문 칩을 받지 못했어요: userId={}", userId, e);
            return List.of();
        }
    }

    /**
     * 내 서류 중 가장 많은 종류를 골라 그 종류의 가장 최근 서류를 대표로 쓴다.
     * (AI 칩 API가 서류 종류 목록을 받게 되면 종류 목록으로 바꾼다.)
     */
    Optional<Long> representativeDocument(Long userId) {
        List<Document> mine = documents.findByUserIdAndStatusInOrderByIdDesc(userId,
                List.of(gachi.chagok_server.domain.document.entity.DocumentStatus.REVIEW_REQUIRED,
                        gachi.chagok_server.domain.document.entity.DocumentStatus.CONFIRMED));
        Map<DocType, Long> counts = mine.stream().filter(d -> d.getDocType() != null)
                .collect(Collectors.groupingBy(Document::getDocType, Collectors.counting()));
        return counts.entrySet().stream()
                .max(Map.Entry.<DocType, Long>comparingByValue())
                .map(Map.Entry::getKey)
                .flatMap(type -> mine.stream().filter(d -> d.getDocType() == type).findFirst())
                .map(Document::getId);
    }
}
