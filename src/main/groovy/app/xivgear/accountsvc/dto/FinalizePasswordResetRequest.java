package app.xivgear.accountsvc.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
public class FinalizePasswordResetRequest {
	public @Email(message = "Must be a valid email") @NotBlank(message = "Email must not be blank")
	String email;
	public int token;
	public @Size(min = 8, message = "Password must be at least {min} characters")
	String newPassword;
}
