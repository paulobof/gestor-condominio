package br.com.condominio.feature.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TagServiceTest {

  private TagRepository repo;
  private TagService service;

  @BeforeEach
  void setUp() {
    repo = mock(TagRepository.class);
    service = new TagService(repo);
    when(repo.save(any(Tag.class))).thenAnswer(i -> i.getArgument(0));
  }

  @Test
  void getOrCreate_existingSlug_reuses() {
    Tag existing = Tag.create("encanador", "Encanador", null);
    when(repo.findBySlug("encanador")).thenReturn(Optional.of(existing));
    Tag result = service.getOrCreate("Encanador", null);
    assertThat(result).isSameAs(existing);
    verify(repo, never()).save(any());
  }

  @Test
  void getOrCreate_newSlug_creates() {
    when(repo.findBySlug("eletricista")).thenReturn(Optional.empty());
    Tag result = service.getOrCreate("Eletricista", null);
    assertThat(result.getSlug()).isEqualTo("eletricista");
    assertThat(result.getLabel()).isEqualTo("Eletricista");
    verify(repo).save(any(Tag.class));
  }
}
