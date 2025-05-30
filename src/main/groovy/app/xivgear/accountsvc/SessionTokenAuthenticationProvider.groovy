package app.xivgear.accountsvc

import app.xivgear.accountsvc.models.UserAccount
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpRequest
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.provider.HttpRequestAuthenticationProvider

//@Context
//@Singleton
@CompileStatic
@Slf4j
class SessionTokenAuthenticationProvider implements HttpRequestAuthenticationProvider<Object> {

	private final SessionTokenStore<UserAccount> store

	SessionTokenAuthenticationProvider(SessionTokenStore store) {
		this.store = store
	}

	@Override
	AuthenticationResponse authenticate(@Nullable HttpRequest<Object> request, @NonNull AuthenticationRequest<String, String> authRequest) {
		Optional<AuthenticationResponse> out = store.extractTokenFromRequest(request)
				.flatMap(store::validateSessionToken)
				.map({
					AuthenticationResponse.success(it.id.toString(), it.roles)
				})
		if (out.isPresent()) {
			return out.get()
		}
		else {
			return AuthenticationResponse.failure()
		}
	}
}
