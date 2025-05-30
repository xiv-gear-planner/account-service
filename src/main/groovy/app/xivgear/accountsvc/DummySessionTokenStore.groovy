package app.xivgear.accountsvc

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.micronaut.core.annotation.NonNull
import jakarta.inject.Singleton
import org.bouncycastle.util.encoders.Hex

import java.security.SecureRandom

@Context
@Singleton
@CompileStatic
@Slf4j
class DummySessionTokenStore implements SessionTokenStore<Integer> {

	private final Map<String, Integer> sessionTokens = new HashMap<>()

	@Override
	Optional<Integer> validateSessionToken(@NonNull String token) {
		return Optional.ofNullable(sessionTokens[token])
	}

	@Override
	String createSessionToken(@NonNull Integer uid) {
		byte[] token = new byte[32]
		new SecureRandom().nextBytes(token)
		String out = new String(Hex.encode(token))
		sessionTokens[out] = uid
		return out
	}

	@Override
	void invalidateSessionToken(@NonNull String token) {
		sessionTokens.remove token
	}
}
