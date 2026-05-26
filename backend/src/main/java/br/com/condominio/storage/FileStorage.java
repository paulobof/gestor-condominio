package br.com.condominio.storage;

import java.io.InputStream;
import java.time.Duration;

public interface FileStorage {

  /** Upload byte stream to bucket. Returns the storage key (UUID-based). */
  String upload(String bucket, InputStream content, long contentLength, String contentType);

  /** Generate a presigned GET URL valid for ttl seconds. */
  String presignedGetUrl(String bucket, String objectKey, Duration ttl);

  /** Delete an object (used for LGPD anonymization). */
  void delete(String bucket, String objectKey);

  /** Ensure bucket exists; create if not. Idempotent. */
  void ensureBucketExists(String bucket);
}
