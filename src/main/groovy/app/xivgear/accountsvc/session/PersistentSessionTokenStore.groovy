package app.xivgear.accountsvc.session

import app.xivgear.accountsvc.nosql.SessionCol
import app.xivgear.accountsvc.nosql.SessionsTable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Primary
import io.micronaut.core.annotation.NonNull
import jakarta.inject.Singleton
import oracle.nosql.driver.ops.GetResult
import oracle.nosql.driver.values.IntegerValue
import org.bouncycastle.util.encoders.Hex

import java.security.SecureRandom

@Context
@Singleton
@CompileStatic
@Slf4j
class PersistentSessionTokenStore implements SessionTokenStore<Integer> {

	private final SessionsTable sessionsTable

	PersistentSessionTokenStore(SessionsTable sessionsTable) {
		this.sessionsTable = sessionsTable
	}

	@Override
	Optional<Integer> validateSessionToken(@NonNull String token) {
		GetResult result = sessionsTable.get token
		return Optional.ofNullable(result?.value?.getInt(SessionCol.owner_uid.name()))
	}

	@Override
	String createSessionToken(@NonNull Integer uid) {
		byte[] token = new byte[32]
		new SecureRandom().nextBytes(token)
		String sessionKey = new String(Hex.encode(token))
		sessionsTable.putByPK(sessionKey, [(SessionCol.owner_uid): new IntegerValue(uid)])
		return sessionKey
	}

	@Override
	void invalidateSessionToken(@NonNull String token) {
		sessionsTable.deleteByPk(token)
	}

	@Override
	void invalidateAllForUser(@NonNull int uid) {
		int count = sessionsTable.deleteMany([(SessionCol.owner_uid): new IntegerValue(uid)])
		log.info "Invalidated ${count} sessions for uid ${uid}"
	}
}
