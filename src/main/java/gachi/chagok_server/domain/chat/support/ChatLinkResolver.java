package gachi.chagok_server.domain.chat.support;

import gachi.chagok_server.domain.box.entity.Box;
import gachi.chagok_server.domain.box.repository.BoxRepository;
import gachi.chagok_server.domain.chat.dto.ChatDtos.BoxView;
import gachi.chagok_server.domain.chat.dto.ChatDtos.DocumentView;
import gachi.chagok_server.domain.chat.dto.ChatDtos.LinkStatus;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.entity.DocumentStatus;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 대화에 넣은 서류·박스를 검사하고, 박스를 서류로 펼친다. 읽기 전용 트랜잭션 안에서 부른다.
 */
@Component
@RequiredArgsConstructor
public class ChatLinkResolver {

    /** AI 채팅이 한 번에 받는 서류는 지금 5개까지다 (명세상 목표는 20개 — AI 명세 변경 전까지 5개로 막는다). */
    public static final int MAX_DOCUMENTS = 5;
    public static final String DELETED_TITLE = "삭제된 서류";

    private final DocumentRepository documents;
    private final BoxRepository boxes;

    /**
     * 연결된 서류 하나.
     *
     * @param status null이면 삭제된 서류
     */
    public record Linked(Long documentId, String title, DocumentStatus status, Long viaBoxId) {

        public boolean isDeleted() {
            return status == null;
        }

        public LinkStatus linkStatus() {
            return status == DocumentStatus.ANALYZING ? LinkStatus.PENDING : LinkStatus.LINKED;
        }
    }

    /** 넣을 서류·박스를 검사한다: 내 것이어야 하고, 분석에 실패한 서류는 안 되며, 펼친 서류가 5개를 넘으면 안 된다. */
    public void validate(Long userId, Set<Long> documentIds, Set<Long> boxIds) {
        for (Long documentId : documentIds) {
            Document document = documents.findByIdAndUserId(documentId, userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
            if (document.getStatus() == DocumentStatus.FAILED) {
                throw new BusinessException(ErrorCode.DOCUMENT_ANALYSIS_FAILED);
            }
        }
        for (Long boxId : boxIds) {
            boxes.findByIdAndUserId(boxId, userId).orElseThrow(() -> new BusinessException(ErrorCode.BOX_NOT_FOUND));
        }
        long count = expand(userId, documentIds, boxIds).stream().filter(l -> !l.isDeleted()).count();
        if (count > MAX_DOCUMENTS) {
            throw new BusinessException(ErrorCode.TOO_MANY_DOCUMENTS);
        }
    }

    /**
     * 직접 넣은 서류와 박스 안의 서류를 합친다 (중복은 직접 넣은 쪽이 우선).
     * 지워진 서류는 삭제 표시로, 분석에 실패한 서류는 빼고 준다.
     */
    public List<Linked> expand(Long userId, Set<Long> documentIds, Set<Long> boxIds) {
        Map<Long, Linked> result = new LinkedHashMap<>();

        Map<Long, Document> direct = documents.findAllById(documentIds).stream()
                .filter(d -> d.getUser().getId().equals(userId))
                .collect(Collectors.toMap(Document::getId, Function.identity()));
        for (Long documentId : documentIds) {
            Document document = direct.get(documentId);
            if (document == null) {
                result.put(documentId, new Linked(documentId, DELETED_TITLE, null, null));
            } else if (document.getStatus() != DocumentStatus.FAILED) {
                result.put(documentId, new Linked(documentId, document.getTitle(), document.getStatus(), null));
            }
        }
        for (Long boxId : boxIds) {
            if (boxes.findByIdAndUserId(boxId, userId).isEmpty()) {
                continue; // 지워진 박스
            }
            for (Document document : documents.findByBoxId(boxId)) {
                if (document.getStatus() != DocumentStatus.FAILED) {
                    result.putIfAbsent(document.getId(), new Linked(document.getId(), document.getTitle(), document.getStatus(), boxId));
                }
            }
        }
        return new ArrayList<>(result.values());
    }

    public List<DocumentView> documentViews(List<Linked> linked) {
        return linked.stream().filter(l -> !l.isDeleted())
                .map(l -> new DocumentView(l.documentId(), l.title(), l.linkStatus(), l.viaBoxId()))
                .toList();
    }

    /** 통째로 넣은 박스와 박스 안의 (분석 실패를 뺀) 서류 수 */
    public List<BoxView> boxViews(Long userId, Set<Long> boxIds) {
        List<BoxView> views = new ArrayList<>();
        for (Long boxId : new LinkedHashSet<>(boxIds)) {
            Box box = boxes.findByIdAndUserId(boxId, userId).orElse(null);
            if (box != null) {
                views.add(new BoxView(box.getId(), box.getName(),
                        documents.countByBoxIdAndStatusNot(boxId, DocumentStatus.FAILED)));
            }
        }
        return views;
    }
}
