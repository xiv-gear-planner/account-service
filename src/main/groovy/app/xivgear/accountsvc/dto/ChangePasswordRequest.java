package app.xivgear.accountsvc.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Size;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
public final class ChangePasswordRequest {
	public String existingPassword;

	public @Size(min = 8, message = "Password must be at least {min} characters")
	String newPassword;
}
