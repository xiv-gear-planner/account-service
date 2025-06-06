package app.xivgear.accountsvc.auth;

import app.xivgear.accountsvc.models.UserAccount;

import java.util.Optional;

public interface CredentialValidator {
	Optional<UserAccount> validateCredentials(String email, String password);
}
