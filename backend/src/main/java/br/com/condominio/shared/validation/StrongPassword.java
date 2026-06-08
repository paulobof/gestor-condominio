package br.com.condominio.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Senha forte: 8-128 chars com maiúscula, minúscula, número e caractere especial. */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {
  String message() default
      "Senha não atende à política mínima (8+ com maiúscula, minúscula, número e caractere especial).";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
