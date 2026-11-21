package gachi.chagok_server.domain.document.event;

/**
 * 서류 분석을 시작해야 할 때 발행한다 (업로드 직후, 서류 종류를 바꿨을 때).
 * 트랜잭션이 커밋된 뒤 비동기로 AI 서버를 부른다.
 *
 * @param ocrResult   앱 기기 안 OCR 결과 JSON (없으면 null)
 * @param docTypeHint 사용자가 고른 서류 종류 (없으면 null)
 */
public record DocumentAnalysisRequestedEvent(Long documentId, Long userId, String ocrResult, String docTypeHint) {
}
