package app.xivgear.accountsvc.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Objects;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
public final class LoginRequest {
	public String email;
	public String password;

	public LoginRequest(String email, String password) {
		this.email = email;
		this.password = password;
	}

	public String email() {
		return email;
	}

	public String password() {
		return password;
	}

	@Override
	public String toString() {
		return "LoginRequest[" +
		       "email=" + email + ", " +
		       "password=" + password + ']';
	}

}
