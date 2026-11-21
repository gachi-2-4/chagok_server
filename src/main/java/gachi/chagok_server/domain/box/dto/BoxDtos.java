package gachi.chagok_server.domain.box.dto;

import gachi.chagok_server.domain.box.entity.BoxColor;
import gachi.chagok_server.domain.box.entity.BoxIcon;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 박스 API 요청·응답 DTO.
 */
public final class BoxDtos {

    private BoxDtos() {
    }

    public enum Sort { CREATED, RECENT_OPENED }

    /** 박스 안 서류의 완료하지 않은 기한 중 가장 가까운 것 (박스 배지 "D-13") */
    public record NearestDeadline(LocalDate date, long dDay) {
    }

    public record CreateRequest(@NotNull String name, @NotNull BoxColor color, @NotNull BoxIcon icon) {
    }

    public record UpdateRequest(String name, BoxColor color, BoxIcon icon) {
    }

    /** 박스 목록의 한 줄 */
    public record Item(Long boxId, String name, BoxColor color, BoxIcon icon, long documentCount,
                       NearestDeadline nearestDeadline, OffsetDateTime lastOpenedAt) {
    }

    public record ListResponse(List<Item> content) {
    }

    /** 박스 만들기·수정 응답 */
    public record Created(Long boxId, String name, BoxColor color, BoxIcon icon, long documentCount,
                          NearestDeadline nearestDeadline, OffsetDateTime createdAt) {
    }

    public record Detail(Long boxId, String name, BoxColor color, BoxIcon icon, long documentCount,
                         NearestDeadline nearestDeadline, OffsetDateTime lastOpenedAt, OffsetDateTime createdAt) {
    }

    public record Deleted(Long boxId, int deletedDocumentCount) {
    }
}
