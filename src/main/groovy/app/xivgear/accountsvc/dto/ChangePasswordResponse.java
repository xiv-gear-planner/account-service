package app.xivgear.accountsvc.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
public final class ChangePasswordResponse {
	public boolean passwordCorrect;

	public ChangePasswordResponse(boolean passwordCorrect) {
		this.passwordCorrect = passwordCorrect;
	}
}
