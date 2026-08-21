package br.com.condominio.feature.featureflag;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * O que está ligado neste ambiente. Público: a tela de login precisa saber se oferece o cadastro de
 * proprietário, antes de existir sessão. Expõe apenas nomes de módulo — nenhum dado de morador.
 */
@RestController
@RequestMapping("/api/features")
@RequiredArgsConstructor
public class FeatureController {

  private final FeatureFlags flags;

  @GetMapping
  public Map<String, Boolean> list() {
    return flags.asMap();
  }
}
