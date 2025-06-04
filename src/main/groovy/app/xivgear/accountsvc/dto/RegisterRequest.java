package app.xivgear.accountsvc.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
public final class RegisterRequest {
	public @Email(message = "Must be a valid email") @NotBlank(message = "Email must not be blank")
	String email;
	public @Size(min = 8, message = "Password must be at least {min} characters")
	String password;
	public @Size(min = 2, max = 64, message = "Display Name must be between {min} and {max} characters")
	String displayName;

	public RegisterRequest(String email, String password, String displayName) {
		this.email = email;
		this.password = password;
		this.displayName = displayName;
	}

	public String email() {
		return email;
	}

	public String password() {
		return password;
	}

	public String displayName() {
		return displayName;
	}

	@Override
	public String toString() {
		return "RegisterRequest[" +
		       "email=" + email + ", " +
		       "password=" + password + ", " +
		       "displayName=" + displayName + ']';
	}

}