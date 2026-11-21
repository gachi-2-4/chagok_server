package gachi.chagok_server.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 클라이언트. 자격 증명은 기본 제공자 체인(환경 변수, 인스턴스 프로필 등)에서 읽으므로 코드·설정 파일에 두지 않는다.
 */
@Configuration
public class StorageConfig {

    @Bean(destroyMethod = "close")
    S3Client s3Client(@Value("${chagok.storage.region}") String region) {
        return S3Client.builder().region(Region.of(region)).build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(@Value("${chagok.storage.region}") String region) {
        return S3Presigner.builder().region(Region.of(region)).build();
    }
}
