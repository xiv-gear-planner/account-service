package app.xivgear.accountsvc

import app.xivgear.accountsvc.models.UserAccount
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpRequest
import io.micronaut.security.authentication.AuthenticationFailureReason
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.provider.HttpRequestAuthenticationProvider
import jakarta.inject.Singleton

//@Context
//@Singleton
//@CompileStatic
//@Slf4j
class AuthProvider<B> implements HttpRequestAuthenticationProvider<B> {
	private final AccountOperations ops

	AuthProvider(AccountOperations ops) {
		this.ops = ops
	}

	@Override
	AuthenticationResponse authenticate(@Nullable HttpRequest<B> requestContext, @NonNull AuthenticationRequest<String, String> authRequest) {
		UserAccount user = ops.getByEmail(authRequest.identity)
		if (user == null) {
			return AuthenticationResponse.failure(AuthenticationFailureReason.USER_NOT_FOUND)
		}
		if (ops.verifyPassword(authRequest.secret, user.passwordHash)) {
			return AuthenticationResponse.success(user.id.toString(), user.roles)
		}
		return AuthenticationResponse.failure(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH)
	}

}
