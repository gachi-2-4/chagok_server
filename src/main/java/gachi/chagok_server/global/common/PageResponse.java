package gachi.chagok_server.global.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 목록 API 공통 페이지 응답. page는 0부터 시작한다.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.hasNext());
    }

    /** 이미 메모리에서 정렬·필터링한 목록을 잘라서 페이지 응답으로 만든다. */
    public static <T> PageResponse<T> ofList(List<T> all, int page, int size) {
        int from = (int) Math.min((long) page * size, all.size());
        int to = Math.min(from + size, all.size());
        return new PageResponse<>(all.subList(from, to), page, size, all.size(), to < all.size());
    }
}
