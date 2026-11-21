package gachi.chagok_server.domain.document.support;

import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Analysis;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Field;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Party;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 분석 결과의 항목 값을 읽고 검사하는 도우미.
 */
public final class DocumentFieldValues {

    static final int MAX_TEXT_LENGTH = 200;
    private static final Pattern KOREAN_AMOUNT = Pattern.compile("^(\\d+(?:\\.\\d+)?)(억|천만|백만|만|천)?(?:원)?$");

    private DocumentFieldValues() {
    }

    public static Optional<Field> find(Analysis analysis, String key) {
        if (analysis == null || analysis.fields() == null || key == null) {
            return Optional.empty();
        }
        return analysis.fields().stream().filter(f -> key.equals(f.key())).findFirst();
    }

    /** 금액 항목의 값을 원 단위 정수로 읽는다. 금액이 아니거나 값이 없으면 비어 있다. */
    public static Optional<Long> amountOf(Analysis analysis, String fieldKey) {
        return find(analysis, fieldKey)
                .filter(f -> "AMOUNT".equals(f.valueType()))
                .map(Field::value)
                .filter(Number.class::isInstance)
                .map(v -> ((Number) v).longValue());
    }

    /**
     * 사용자가 보낸 새 값이 valueType에 맞는지 검사하고 저장할 값으로 돌려준다.
     * AMOUNT는 0 이상 정수, DATE는 YYYY-MM-DD, TEXT는 200자 이하다. null은 값을 비운다는 뜻이다.
     */
    public static Object validate(String valueType, Object value) {
        if (value == null) {
            return null;
        }
        String type = valueType == null ? "TEXT" : valueType;
        switch (type) {
            case "AMOUNT" -> {
                if (value instanceof Number n && n.doubleValue() >= 0 && n.doubleValue() == Math.floor(n.doubleValue())) {
                    return n.longValue();
                }
                throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE);
            }
            case "DATE" -> {
                try {
                    return LocalDate.parse(String.valueOf(value)).toString();
                } catch (DateTimeParseException e) {
                    throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE);
                }
            }
            case "NUMBER" -> {
                if (value instanceof Number) {
                    return value;
                }
                throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE);
            }
            default -> {
                // TEXT, PENALTY: 문장(200자 이하)을 받는다. PENALTY는 AI가 준 객체 모양도 허용한다.
                if (value instanceof String s && s.length() <= MAX_TEXT_LENGTH) {
                    return s;
                }
                if ("PENALTY".equals(type) && value instanceof Map<?, ?>) {
                    return value;
                }
                throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE);
            }
        }
    }

    /** 키워드 검색용으로 서류 이름·요약·당사자·항목 값을 이어 붙인다. 금액은 쉼표 없는 숫자로 넣고, 비교는 소문자로 맞춰 저장한다. */
    public static String searchText(String title, Analysis analysis) {
        StringBuilder text = new StringBuilder();
        append(text, title);
        if (analysis != null) {
            append(text, analysis.summary());
            if (analysis.parties() != null) {
                for (Party party : analysis.parties()) {
                    append(text, party.name());
                }
            }
            if (analysis.fields() != null) {
                for (Field field : analysis.fields()) {
                    append(text, field.label());
                    append(text, field.value() == null ? null : String.valueOf(field.value()));
                }
            }
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private static void append(StringBuilder text, String value) {
        if (value != null && !value.isBlank()) {
            text.append(value).append('\n');
        }
    }

    /**
     * 검색어를 금액으로 읽는다. "500000", "500,000", "50만", "2억"처럼 숫자로 바꿀 수 있으면 원 단위 값을 준다.
     */
    public static Optional<Long> parseAmountKeyword(String keyword) {
        if (keyword == null) {
            return Optional.empty();
        }
        String compact = keyword.replace(",", "").replace(" ", "");
        Matcher matcher = KOREAN_AMOUNT.matcher(compact);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        double number = Double.parseDouble(matcher.group(1));
        long unit = switch (matcher.group(2) == null ? "" : matcher.group(2)) {
            case "억" -> 100_000_000L;
            case "천만" -> 10_000_000L;
            case "백만" -> 1_000_000L;
            case "만" -> 10_000L;
            case "천" -> 1_000L;
            default -> 1L;
        };
        long amount = Math.round(number * unit);
        return amount > 0 ? Optional.of(amount) : Optional.empty();
    }

    public static boolean anyNeedsReview(List<Field> fields) {
        return fields != null && fields.stream().anyMatch(Field::isNeedsReview);
    }
}
