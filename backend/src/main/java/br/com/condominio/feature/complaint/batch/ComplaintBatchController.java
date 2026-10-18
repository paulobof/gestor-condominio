package br.com.condominio.feature.complaint.batch;

import br.com.condominio.feature.complaint.batch.dto.*;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/complaints/batches")
@RequiredArgsConstructor
public class ComplaintBatchController {

  private static final String CAN = "hasAuthority('COMPLAINT_MANAGE')";

  private final ComplaintBatchService service;

  @PostMapping("/preview")
  @PreAuthorize(CAN)
  public BatchPreview preview(@Valid @RequestBody BatchRequest body) {
    return service.preview(body.ids());
  }

  @PostMapping
  @PreAuthorize(CAN)
  @ResponseStatus(HttpStatus.CREATED)
  public ComplaintBatchView create(
      @Valid @RequestBody BatchRequest body,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return service.create(body.ids(), body.expectedText(), me.userId());
  }

  @GetMapping
  @PreAuthorize(CAN)
  public List<ComplaintBatchView> list() {
    return service.list();
  }
}
