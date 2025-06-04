package app.xivgear.accountsvc.models;

import java.util.List;

public interface UserAccount {
	// col user_id
	int getId();
	// col display_name
	String getDisplayName();
	void setDisplayName(String name);

	// col email
	String getEmail();
	void setEmail(String email);

	// col verified
	boolean isVerified();
	// verification code col verification_code
	void setVerified(boolean verified);

	// col roles
	List<String> getRoles();

	String getPasswordHash();
	void setPasswordHash(String hash);

	boolean verifyEmail(String email, int code);
}
