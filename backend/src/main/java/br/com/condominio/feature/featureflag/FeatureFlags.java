package br.com.condominio.feature.featureflag;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Estado das feature flags do ambiente, em um lugar só. Lê exatamente as mesmas propriedades que
 * cada service consulta — não é uma segunda fonte de verdade, é a leitura consolidada delas para o
 * frontend saber o que montar no menu.
 *
 * <p>Sem a flag ligada, o backend responde 404 nas rotas do módulo; o menu precisa saber disso
 * antes de oferecer o link.
 */
@Component
public class FeatureFlags {

  @Value("${app.feature.announcements.enabled:true}")
  private boolean announcements;

  @Value("${app.feature.classifieds.enabled:true}")
  private boolean classifieds;

  @Value("${app.feature.recommendations.enabled:true}")
  private boolean recommendations;

  @Value("${app.feature.faq.enabled:true}")
  private boolean faq;

  @Value("${app.feature.generalinfo.enabled:true}")
  private boolean generalinfo;

  @Value("${app.feature.documents.enabled:true}")
  private boolean documents;

  @Value("${app.feature.parkingrental.enabled:true}")
  private boolean parkingrental;

  @Value("${app.feature.accessmanagement.enabled:true}")
  private boolean accessmanagement;

  /** Mapa nome→ligada, na ordem em que os módulos aparecem para o morador. */
  public Map<String, Boolean> asMap() {
    Map<String, Boolean> m = new LinkedHashMap<>();
    m.put("announcements", announcements);
    m.put("generalinfo", generalinfo);
    m.put("faq", faq);
    m.put("documents", documents);
    m.put("recommendations", recommendations);
    m.put("classifieds", classifieds);
    m.put("parkingrental", parkingrental);
    m.put("accessmanagement", accessmanagement);
    return m;
  }
}
