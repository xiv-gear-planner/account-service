package app.xivgear.accountsvc

import app.xivgear.accountsvc.models.OracleUserAccount
import app.xivgear.accountsvc.models.UserAccount
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.micronaut.oraclecloud.core.OracleCloudAuthConfigurationProperties
import jakarta.inject.Singleton
import oracle.nosql.driver.NoSQLHandle
import oracle.nosql.driver.NoSQLHandleConfig
import oracle.nosql.driver.NoSQLHandleFactory
import oracle.nosql.driver.iam.SignatureProvider
import oracle.nosql.driver.ops.*
import oracle.nosql.driver.values.MapValue
import org.bouncycastle.util.encoders.Hex

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.security.SecureRandom
import java.security.spec.KeySpec

@Context
@Singleton
@CompileStatic
@Slf4j
class OracleNoSqlConnector {

	private final NoSQLHandle handle
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

	OracleNoSqlConnector(
			OracleCloudAuthConfigurationProperties auth,
			@Value('${oracle-nosql.endpoint}') URL endpoint,
			@Value('${oracle-nosql.compartment}') String compartmentId
	) {
		NoSQLHandleConfig config = new NoSQLHandleConfig(endpoint)
		SimpleAuthenticationDetailsProvider build = auth.builder.build()
		// TODO: easier way?
		SignatureProvider sp = new SignatureProvider(build.tenantId, build.userId, build.fingerprint, new String(build.privateKey.readAllBytes()), null)
		config.authorizationProvider = sp
		config.defaultCompartment = compartmentId
		handle = NoSQLHandleFactory.createNoSQLHandle config
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
		GetResult result = handle.get(req)
		return new OracleUserAccount(handle, usersTableName, result.value)
	}

	long count() {
		long total = 0;
		try (QueryRequest qr = new QueryRequest()) {
			qr.setStatement("SELECT count(*) AS ct FROM ${usersTableName}")
			do {
				QueryResult res = handle.query(qr);
				// Only non-empty batches will contain the aggregate row
				for (MapValue row : res.getResults()) {
					total = row.get("ct").getLong();
				}
			} while (!qr.isDone());
		}
		log.info("Results: {}", total);
		return total;
	}
}
