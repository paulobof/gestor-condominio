package br.com.condominio.storage;

import io.minio.*;
import io.minio.http.Method;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioFileStorage implements FileStorage {

  private final MinioClient client;
  private final MinioProperties props;

  @Override
  public String upload(String bucket, InputStream content, long contentLength, String contentType) {
    String key = UUID.randomUUID().toString();
    try {
      client.putObject(
          PutObjectArgs.builder().bucket(bucket).object(key).stream(content, contentLength, -1)
              .contentType(contentType)
              .build());
      return key;
    } catch (Exception e) {
      throw new IllegalStateException("MinIO upload failed for bucket " + bucket, e);
    }
  }

  @Override
  public String presignedGetUrl(String bucket, String objectKey, Duration ttl) {
    try {
      return client.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(Method.GET)
              .bucket(bucket)
              .object(objectKey)
              .expiry((int) ttl.toSeconds(), TimeUnit.SECONDS)
              .build());
    } catch (Exception e) {
      throw new IllegalStateException("MinIO presigned URL failed", e);
    }
  }

  @Override
  public byte[] getObject(String bucket, String objectKey) {
    try (GetObjectResponse response =
        client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
      return response.readAllBytes();
    } catch (Exception e) {
      throw new IllegalStateException("MinIO getObject failed", e);
    }
  }

  @Override
  public void delete(String bucket, String objectKey) {
    try {
      client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
    } catch (Exception e) {
      throw new IllegalStateException("MinIO delete failed", e);
    }
  }

  @Override
  public void ensureBucketExists(String bucket) {
    try {
      boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
      if (!exists) {
        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        log.info("Created MinIO bucket: {}", bucket);
      }
    } catch (Exception e) {
      throw new IllegalStateException("MinIO bucket check/create failed for " + bucket, e);
    }
  }

  @Configuration
  @EnableConfigurationProperties(MinioProperties.class)
  static class MinioClientConfig {
    @Bean
    public MinioClient minioClient(MinioProperties props) {
      return MinioClient.builder()
          .endpoint(props.getEndpoint())
          .credentials(props.getAccessKey(), props.getSecretKey())
          .build();
    }
  }
}
