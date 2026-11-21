package gachi.chagok_server.domain.ai.client;

import gachi.chagok_server.domain.ai.dto.AiChatDtos.ChatAnswer;
import gachi.chagok_server.domain.ai.dto.AiChatDtos.Suggestion;
import gachi.chagok_server.domain.ai.dto.AiComparisonDtos.Comparison;
import gachi.chagok_server.domain.ai.dto.AiComparisonDtos.InquiryMessage;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Analysis;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.SummaryCard;

import java.time.LocalDate;
import java.util.List;

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

    /**
     * 서류 기반 AI 채팅 답변. AI 서버가 BE 내부 API로 서류·최근 대화·사용자 정보를 직접 가져가므로
     * BE는 대화 ID, 사용자, 서류 ID(분석이 끝난 것만), 질문만 보낸다.
     */
    ChatAnswer chatAnswer(Long chatId, Long userId, List<Long> documentIds, String question);

    /** 자주 묻는 질문 칩 (질문은 20자 이하). */
    List<Suggestion> suggestions(Long userId, Long documentId, int count);

    /** 서류 비교. 계약서(기준)와 고지서·광고(비교 대상)를 견줘 약속과 다른 금액·조건을 찾는다. */
    Comparison compare(Long comparisonId, Long userId, Long baseDocumentId, Long targetDocumentId, String compareType);

    /** 비교 결과를 바탕으로 발급처에 보낼 확인 요청 문자를 만든다. */
    InquiryMessage inquiryMessage(Long comparisonId, Long userId, String tone, String extraRequest);
}
