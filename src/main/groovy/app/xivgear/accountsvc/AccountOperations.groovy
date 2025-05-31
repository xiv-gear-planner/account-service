package app.xivgear.accountsvc

import app.xivgear.accountsvc.models.OracleUserAccount
import app.xivgear.accountsvc.models.UserAccount
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.micronaut.core.annotation.Nullable
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator
import jakarta.inject.Singleton
import oracle.nosql.driver.NoSQLHandle
import oracle.nosql.driver.ops.*
import oracle.nosql.driver.values.MapValue
import oracle.nosql.driver.values.StringValue
import org.bouncycastle.util.encoders.Hex

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.security.SecureRandom
import java.security.spec.KeySpec

/**
 * Higher-level operations for accounts, such as account creation and login verification.
 */
@Context
@Singleton
@CompileStatic
@Slf4j
class AccountOperations implements CredentialValidator {

	/*
	CREATE TABLE users_test (
		user_id integer GENERATED ALWAYS AS IDENTITY,
		display_name string,
		email string,
		is_verified boolean DEFAULT false NOT NULL,
		roles json,
		password_hash string,
		PRIMARY KEY ( SHARD ( user_id ) )
	)
	*/
	public static final String usersTableName = "users_test"
	// Uses a separate table of emails so that we can enforce uniqueness. Oracle NoSQL does not offer a "unqiue index"
	// feature, and normal indices are only atomic within a shard.
	/*
	CREATE TABLE emails_test (
		email string,
		owner_uid integer DEFAULT -1 NOT NULL,
		verified boolean DEFAULT false NOT NULL,
		verification_code integer,
		PRIMARY KEY ( SHARD ( email ) )
	)
	// TODO: make index for owner_uid
	 */
	public static final String emailsTableName = "emails_test"

	private final NoSQLHandle handle

	AccountOperations(NoSQLHandle handle) {
		this.handle = handle
	}

	static String saltAndHash(String password) {

		SecureRandom random = new SecureRandom()
		byte[] salt = new byte[32]
		random.nextBytes salt

		KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256)
		SecretKeyFactory factory = SecretKeyFactory.getInstance "PBKDF2WithHmacSHA512"

		byte[] hash = factory.generateSecret(spec).encoded

		return "${new String(Hex.encode(salt))}:${new String(Hex.encode(hash))}"
	}

	static boolean verifyPassword(String password, String storedSaltedHash) {
		List<byte[]> parts = storedSaltedHash.split(':').collect { Hex.decode(it) }
		var storedSalt = parts[0]
		var storedHash = parts[1]

		KeySpec spec = new PBEKeySpec(password.toCharArray(), storedSalt, 65536, 256)
		SecretKeyFactory factory = SecretKeyFactory.getInstance "PBKDF2WithHmacSHA512"
		byte[] hashToCheck = factory.generateSecret(spec).encoded

		return Arrays.equals(hashToCheck, storedHash)
	}


	// returns generated user id
	int addUser(String email, String displayName, String password) {

		password = saltAndHash(password)
		// Store email first to make sure it is not in use
		var emailReq = new PutRequest().tap {
			tableName = emailsTableName
			setValue(new MapValue().tap {
				put "email", email
			})
		}
		handle.put emailReq
		var userReq = new PutRequest().tap {
			tableName = usersTableName
			setValue(new MapValue().tap {
				put "display_name", displayName
				put "email", email
				put "password_hash", password
			})
		}
		PutResult userResult = handle.put userReq
		int uid = userResult.generatedValue.asInteger().value
		var emailUpdateReq = new PutRequest().tap {
			tableName = emailsTableName
			setValue(new MapValue().tap {
				put "email", email
				put "owner_uid", uid
				put "verification_code", Math.abs(new SecureRandom().nextInt())
			})
		}
		handle.put emailUpdateReq
		return uid
	}

	@Nullable
	UserAccount getById(long id) {
		var req = new GetRequest().tap {
			tableName = usersTableName
			setKey(new MapValue().tap {
				put "user_id", id
			})
		}
		GetResult result = handle.get req
		return new OracleUserAccount(handle, usersTableName, result.value)
	}

	@Nullable
	UserAccount getByEmail(String email) {
		PrepareRequest pr = new PrepareRequest().tap {
			statement = "select * from ${usersTableName} as c where c.email = ?"
		}
		PrepareResult prepare = handle.prepare pr
		prepare.preparedStatement.setVariable 1, new StringValue(email)
		try (QueryRequest qr = new QueryRequest()) {
			qr.setPreparedStatement prepare
			QueryIterableResult iterable = handle.queryIterable qr
			for (MapValue row : iterable) {
				return new OracleUserAccount(handle, usersTableName, row)
			}
		}
		return null
	}

	long count() {
		long total = 0
		try (QueryRequest qr = new QueryRequest()) {
			qr.statement = "SELECT count(*) AS ct FROM ${usersTableName}"
			do {
				QueryResult res = handle.query qr
				// Only non-empty batches will contain the aggregate row
				for (MapValue row : res.getResults()) {
					total = row.get("ct").getLong()
				}
			} while (!qr.isDone())
		}
		log.info "Results: {}", total
		return total
	}

//	@Nullable
//	Object login(String email, String password) {
//		UserAccount user = getByEmail(email)
//		if (!user) {
//			return null
//		}
//
//		if (!verifyPassword(password, user.passwordHash)) {
//			return null
//		}
//		Authentication.build(user.id.toString(), user.roles)
//		return jwtGen.generateToken(Collections.singletonMap("sub", user.id.toString()))
//	}

	@Override
	Optional<UserAccount> validateCredentials(String email, String password) {
		UserAccount user = getByEmail(email)
		if (!user) {
			return Optional.empty()
		}
		if (!verifyPassword(password, user.passwordHash)) {
			return Optional.empty()
		}
		return Optional.of(user)
	}
}
