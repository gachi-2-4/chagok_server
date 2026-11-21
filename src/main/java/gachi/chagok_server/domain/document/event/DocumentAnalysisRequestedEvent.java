package gachi.chagok_server.domain.document.event;

import gachi.chagok_server.domain.document.entity.DocumentStatus;

/**
 * 서류 분석을 시작해야 할 때 발행한다 (업로드 직후, 서류 종류를 바꿨을 때).
 * 트랜잭션이 커밋된 뒤 비동기로 AI 서버를 부른다.
 *
 * @param ocrResult     앱 기기 안 OCR 결과 JSON (없으면 null)
 * @param docTypeHint   사용자가 고른 서류 종류 (없으면 null)
 * @param restoreStatus 지금 분석 결과를 지키면서 다시 분석할 때(AI가 서류를 잃어버려 복구하는 경우) 그 전의 서류 상태.
 *                      값이 있으면 사용자가 고친 값을 새 결과에 이어 붙이고, 끝나면 이 상태로 되돌리며,
 *                      실패해도 서류를 실패 처리하지 않고 이 상태로 되돌린다. 일반 분석이면 null.
 */
public record DocumentAnalysisRequestedEvent(Long documentId, Long userId, String ocrResult, String docTypeHint,
                                             DocumentStatus restoreStatus) {

    public DocumentAnalysisRequestedEvent(Long documentId, Long userId, String ocrResult, String docTypeHint) {
        this(documentId, userId, ocrResult, docTypeHint, null);
    }

    public boolean isRecovery() {
        return restoreStatus != null;
    }
}
