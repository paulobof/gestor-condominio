package br.com.condominio.storage;

import java.io.InputStream;
import java.time.Duration;

public interface FileStorage {

  /** Upload byte stream to bucket. Returns the storage key (UUID-based). */
  String upload(String bucket, InputStream content, long contentLength, String contentType);

  /** Generate a presigned GET URL valid for ttl seconds. */
  String presignedGetUrl(String bucket, String objectKey, Duration ttl);

  /** Read an object's full content into memory. For small files (proofs/photos ≤5MB). */
  byte[] getObject(String bucket, String objectKey);

  /** Delete an object (used for LGPD anonymization). */
  void delete(String bucket, String objectKey);

  /** Ensure bucket exists; create if not. Idempotent. */
  void ensureBucketExists(String bucket);
}
