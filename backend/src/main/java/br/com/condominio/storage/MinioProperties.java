package br.com.condominio.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.storage.minio")
public class MinioProperties {
  private String endpoint;
  private String accessKey;
  private String secretKey;
  private String bucketProofs = "residence-proofs";
  private String bucketClassifieds = "classifieds";
  private String bucketRecommendations = "recommendations";
  private int presignedTtlProofsSeconds = 300;
  private int presignedTtlPhotosSeconds = 600;
}
