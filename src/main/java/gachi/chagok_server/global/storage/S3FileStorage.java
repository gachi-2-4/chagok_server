package gachi.chagok_server.global.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class S3FileStorage implements FileStorage {

    private final S3Client s3;
    private final S3Presigner presigner;

    @Value("${chagok.storage.bucket}")
    private String bucket;

    @Override
    public void put(String key, byte[] data, String contentType) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(data));
    }

    @Override
    public byte[] get(String key) {
        return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
    }

    @Override
    public String presignedGetUrl(String key, Duration validFor, String downloadFileName) {
        GetObjectRequest.Builder request = GetObjectRequest.builder().bucket(bucket).key(key);
        if (downloadFileName != null) {
            String encoded = URLEncoder.encode(downloadFileName, StandardCharsets.UTF_8).replace("+", "%20");
            request.responseContentDisposition("attachment; filename*=UTF-8''" + encoded);
        }
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(validFor)
                .getObjectRequest(request.build())
                .build()).url().toString();
    }

    @Override
    public void deleteByPrefix(String prefix) {
        String token = null;
        do {
            ListObjectsV2Response page = s3.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucket).prefix(prefix).continuationToken(token).build());
            List<ObjectIdentifier> keys = page.contents().stream()
                    .map(S3Object::key)
                    .map(key -> ObjectIdentifier.builder().key(key).build())
                    .toList();
            if (!keys.isEmpty()) {
                s3.deleteObjects(DeleteObjectsRequest.builder().bucket(bucket)
                        .delete(d -> d.objects(keys).quiet(true)).build());
            }
            token = page.isTruncated() ? page.nextContinuationToken() : null;
        } while (token != null);
    }
}
