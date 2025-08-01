package app.xivgear.accountsvc.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
public class InitiatePasswordResetRequest {
	public @Email(message = "Must be a valid email") @NotBlank(message = "Email must not be blank")
	String email;
}
