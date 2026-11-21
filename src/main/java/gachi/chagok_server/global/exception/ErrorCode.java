package gachi.chagok_server.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 서비스 전체에서 사용하는 에러 코드.
 * 도메인별 에러 코드도 이 enum에 섹션을 나눠 추가한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    INVALID_TYPE(HttpStatus.BAD_REQUEST, "요청 값의 타입이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    CONFLICT(HttpStatus.CONFLICT, "요청이 현재 상태와 충돌합니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // 박스
    BOX_NOT_FOUND(HttpStatus.NOT_FOUND, "박스를 찾을 수 없어요."),
    INVALID_BOX_NAME(HttpStatus.BAD_REQUEST, "박스 이름은 1자에서 20자 사이로 지어 주세요."),
    BOX_NAME_DUPLICATED(HttpStatus.CONFLICT, "같은 이름의 박스가 있어요."),
    BOX_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "박스는 30개까지 만들 수 있어요."),

    // 서류
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "서류를 찾을 수 없어요."),
    DOCUMENT_NOT_READY(HttpStatus.CONFLICT, "서류 분석이 끝나지 않았어요."),
    INVALID_TITLE(HttpStatus.BAD_REQUEST, "서류 이름은 1자에서 50자 사이로 지어 주세요."),
    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST, "JPG, PNG, HEIC, PDF 파일만 올릴 수 있어요."),
    ENCRYPTED_PDF(HttpStatus.BAD_REQUEST, "암호가 걸린 PDF는 암호를 해제한 후 다시 올려 주세요."),
    CORRUPTED_FILE(HttpStatus.BAD_REQUEST, "파일을 열 수 없어요. 다른 파일을 골라 주세요."),
    TOO_MANY_PAGES(HttpStatus.BAD_REQUEST, "PDF는 20페이지까지 올릴 수 있어요."),
    INVALID_FILE_COMBINATION(HttpStatus.BAD_REQUEST, "사진과 PDF는 함께 올릴 수 없어요."),
    FILE_TOO_LARGE(HttpStatus.valueOf(413), "파일은 20MB까지 올릴 수 있어요."),
    INVALID_PAGE(HttpStatus.BAD_REQUEST, "없는 페이지예요."),
    INVALID_FIELD_VALUE(HttpStatus.BAD_REQUEST, "항목 값을 확인해 주세요."),
    UNKNOWN_FIELD_KEY(HttpStatus.BAD_REQUEST, "이 서류에 없는 항목이에요."),
    CHECK_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "체크 항목을 찾을 수 없어요."),
    SUMMARY_CARD_NOT_READY(HttpStatus.CONFLICT, "AI 정리본을 만드는 중이에요. 잠시 후 다시 시도해 주세요."),

    // 기한·기한 알림
    DEADLINE_NOT_FOUND(HttpStatus.NOT_FOUND, "기한을 찾을 수 없어요."),
    INVALID_DEADLINE(HttpStatus.BAD_REQUEST, "기한 이름과 날짜를 확인해 주세요."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "기간은 1년까지 조회할 수 있어요."),
    REMINDER_NOT_FOUND(HttpStatus.NOT_FOUND, "기한 알림을 찾을 수 없어요."),
    REMINDER_ALREADY_EXISTS(HttpStatus.CONFLICT, "이 기한에는 이미 알림이 있어요."),
    DEADLINE_PASSED(HttpStatus.CONFLICT, "이미 지난 기한이에요."),
    INVALID_ADVANCE_DAYS(HttpStatus.BAD_REQUEST, "미리 알림은 0일에서 90일 사이로 1개에서 5개까지 골라 주세요."),
    INVALID_ALERT_TIME(HttpStatus.BAD_REQUEST, "알림 시각은 10분 단위로 골라 주세요."),

    // AI 서버 연동
    AI_ERROR(HttpStatus.BAD_GATEWAY, "AI 서버에서 오류가 났어요."),
    AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI 서버 응답이 너무 늦어요.");

    private final HttpStatus status;
    private final String message;
}
