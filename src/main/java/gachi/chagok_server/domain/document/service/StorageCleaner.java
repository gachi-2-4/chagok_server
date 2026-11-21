package gachi.chagok_server.domain.document.service;

import gachi.chagok_server.global.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 지운 서류의 저장소 파일을 비동기로 지운다. DB에서는 바로 지우고 파일은 뒤따라 지운다 (명세).
 * 실패해도 서류 삭제는 이미 끝난 뒤이므로 로그만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageCleaner {

    private final FileStorage storage;

    @Async
    public void deleteByPrefix(String prefix) {
        try {
            storage.deleteByPrefix(prefix);
        } catch (RuntimeException e) {
            log.warn("저장소 파일을 지우지 못했어요: prefix={}", prefix, e);
        }
    }
}
