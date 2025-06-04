package app.xivgear.accountsvc.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Email;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
public final class VerifyEmailRequest {
	public @Email String email;
	public int code;
}
