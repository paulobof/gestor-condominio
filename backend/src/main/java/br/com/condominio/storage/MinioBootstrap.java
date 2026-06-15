package br.com.condominio.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class MinioBootstrap {

  private final FileStorage storage;
  private final MinioProperties props;

  @Bean
  public ApplicationRunner ensureBuckets() {
    return args -> {
      for (String bucket :
          new String[] {
            props.getBucketProofs(),
            props.getBucketClassifieds(),
            props.getBucketRecommendations(),
            props.getBucketDocuments()
          }) {
        try {
          storage.ensureBucketExists(bucket);
        } catch (Exception e) {
          log.error("MinioBootstrap: falhou criando/verificando bucket {}", bucket, e);
        }
      }
    };
  }
}
