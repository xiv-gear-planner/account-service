package app.xivgear.accountsvc.dto;

import app.xivgear.accountsvc.models.UserAccount;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Objects;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
public final class AccountInfo {
	public int uid;
	public String email;
	public List<String> roles;
	public boolean verified;
	public String displayName;

	public AccountInfo(int uid, String email, List<String> roles, boolean verified, String displayName) {
		this.uid = uid;
		this.email = email;
		this.roles = roles;
		this.verified = verified;
		this.displayName = displayName;
	}

	public AccountInfo(UserAccount account) {
		this.uid = account.getId();
		this.email = account.getEmail();
		this.roles = account.getRoles();
		this.verified = account.isVerified();
		this.displayName = account.getDisplayName();
	}

	public int uid() {
		return uid;
	}

	public String email() {
		return email;
	}

	public List<String> roles() {
		return roles;
	}

	public boolean verified() {
		return verified;
	}

	@Override
	public String toString() {
		return "AccountInfo[" +
		       "uid=" + uid + ", " +
		       "email=" + email + ", " +
		       "roles=" + roles + ", " +
		       "verified=" + verified + ']';
	}

}
