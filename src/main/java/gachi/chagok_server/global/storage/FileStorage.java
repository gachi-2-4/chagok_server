package gachi.chagok_server.global.storage;

import java.time.Duration;

/**
 * 서류 원본·페이지 이미지 저장소. 운영에서는 S3를 쓴다.
 */
public interface FileStorage {

    void put(String key, byte[] data, String contentType);

    byte[] get(String key);

    /**
     * 내려받을 수 있는 임시(Presigned) URL.
     *
     * @param downloadFileName 지정하면 브라우저·앱이 이 이름으로 저장하도록 한다 (없으면 null)
     */
    String presignedGetUrl(String key, Duration validFor, String downloadFileName);

    /** 접두사로 시작하는 파일을 모두 지운다. */
    void deleteByPrefix(String prefix);
}
