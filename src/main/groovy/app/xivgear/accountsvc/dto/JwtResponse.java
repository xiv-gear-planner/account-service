package app.xivgear.accountsvc.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
public final class JwtResponse {
	public String token;

	public JwtResponse(String token) {
		this.token = token;
	}
}
