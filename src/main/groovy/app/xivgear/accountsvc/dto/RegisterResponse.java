package app.xivgear.accountsvc.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Objects;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
public final class RegisterResponse {
	public int uid;

	public RegisterResponse(int uid) {
		this.uid = uid;
	}

	public int uid() {
		return uid;
	}

	@Override
	public String toString() {
		return "RegisterResponse[" +
		       "uid=" + uid + ']';
	}

}
