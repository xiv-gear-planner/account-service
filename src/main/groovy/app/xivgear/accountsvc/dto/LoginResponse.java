package app.xivgear.accountsvc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Objects;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
public final class LoginResponse {
	@JsonProperty
	public AccountInfo accountInfo;
	@JsonProperty
	public String message;

	public LoginResponse(AccountInfo accountInfo, String message) {
		this.accountInfo = accountInfo;
		this.message = message;
	}

}
