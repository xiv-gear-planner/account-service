package app.xivgear.accountsvc

import app.xivgear.accountsvc.models.UserAccount
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.http.HttpRequest
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.filters.AuthenticationFetcher
import jakarta.inject.Singleton
import org.reactivestreams.Publisher

/**
 * Checks SESSION cookie to find the associated account
 */
@Context
@Singleton
@CompileStatic
@Slf4j
class SessionAuthenticationFetcher implements AuthenticationFetcher<HttpRequest<?>> {

	private final SessionTokenStore<Integer> store
	private final AccountOperations ops

	SessionAuthenticationFetcher(SessionTokenStore store, AccountOperations ops) {
		this.store = store
		this.ops = ops
	}

	@Override
	Publisher<Authentication> fetchAuthentication(HttpRequest<?> request) {
		Optional<Authentication> opt = store.extractTokenFromRequest(request)
				.flatMap(store::validateSessionToken)
				.map({
					UserAccount user = ops.getById it
					Authentication.build it.toString(), user.roles,
							[
									'userData': user
							] as Map<String, Object>
				})
		if (opt.isPresent()) {
			return Publishers.just(opt.get())
		}
		else {
			return Publishers.empty()
		}
	}
}
