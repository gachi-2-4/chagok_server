package gachi.chagok_server.domain.document.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 중요도. rank가 클수록 중요하다 (정렬용). */
@Getter
@RequiredArgsConstructor
public enum PriorityLevel {
    HIGH(3), MEDIUM(2), LOW(1);

    private final int rank;
}
