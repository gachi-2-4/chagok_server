package gachi.chagok_server.domain.ai.client;

import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Analysis;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.SummaryCard;

import java.time.LocalDate;

/**
 * BE → AI 서버 호출 창구 (X-API-Key 인증). 실패는 {@link AiClientException}으로 알린다.
 */
public interface AiServerClient {

    /**
     * 문서 분석. 요청당 파일 1개(PDF 또는 이미지, 20MB까지)만 받는다.
     *
     * @param docTypeHint 사용자가 미리 고른 서류 종류 (없으면 null)
     * @param ocrResult   앱 기기 안 OCR 결과 JSON 문자열 (없으면 null)
     */
    Analysis analyzeDocument(Long documentId, Long userId, byte[] file, String fileName, String docTypeHint,
                             String ocrResult, LocalDate baseDate);

    /** 쉬운말 요약 카드. AI 서버가 BE 서류 조회 내부 API로 최신 분석 결과를 가져가 만든다. */
    SummaryCard summaryCard(Long documentId, Long userId);
}
