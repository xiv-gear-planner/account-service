package app.xivgear.accountsvc.models

import app.xivgear.accountsvc.AccountOperations
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import oracle.nosql.driver.NoSQLHandle
import oracle.nosql.driver.ops.*
import oracle.nosql.driver.values.*

/**
 * Oracle NoSQL-backed user account
 */
@CompileStatic
@Slf4j
class OracleUserAccount implements UserAccount {

	final int id
	private String email
	String displayName
	private boolean isVerified
	String passwordHash

	private final NoSQLHandle handle
	private final String usersTableName
	private final String emailTableName = AccountOperations.emailsTableName

	OracleUserAccount(NoSQLHandle handle, String usersTableName, MapValue value) {
		this.handle = handle
		this.usersTableName = usersTableName
		id = value.get("user_id").getInt()
		this.email = value.get("email").getString()
		passwordHash = value.get("password_hash").getString()
		// TODO: remove from schema
//		verificationCode = value.get("verification_code").with {
//			if (isNull()) {
//				return null
//			}
//			return asInteger().value
//		}
		isVerified = value.get("is_verified").getBoolean()
	}

//	private void updateUser(@DelegatesTo(MapValue) Closure<?> ops) {
//		var req = new PutRequest().tap {
//			tableName = this.tableName
//			option = PutRequest.Option.IfPresent
//			value = new MapValue().tap {
//				put "user_id", (id as int)
//				ops.setDelegate(it)
//				ops.call(it)
//			}
//		}
//		handle.put req
//	}

	private void updateUser(Map<String, ? extends FieldValue> keyValues) {
		var updates = keyValues.entrySet().collect {
			return "${it.key} = \$${it.key}"
		}.join(", ")
		PrepareRequest pr = new PrepareRequest().tap {
			statement = "UPDATE ${usersTableName} AS c SET ${updates} WHERE c.user_id = \$uid"
		}
		PrepareResult prepare = handle.prepare pr
		keyValues.entrySet().forEach {
			prepare.preparedStatement.setVariable "\$${it.key}", it.value
		}
		prepare.preparedStatement.setVariable '$uid', new IntegerValue(id)
		try (QueryRequest qr = new QueryRequest()) {
			qr.setPreparedStatement prepare
			handle.query qr
		}
	}

	private void updateEmail(String email, Map<String, ? extends FieldValue> keyValues) {
		var updates = keyValues.entrySet().collect {
			return "${it.key} = \$${it.key}"
		}.join(", ")
		PrepareRequest pr = new PrepareRequest().tap {
			statement = "UPDATE ${emailTableName} AS c SET ${updates} WHERE c.email = \$email"
		}
		PrepareResult prepare = handle.prepare pr
		keyValues.entrySet().forEach {
			prepare.preparedStatement.setVariable "\$${it.key}", it.value
		}
		prepare.preparedStatement.setVariable '$email', new StringValue(email)
		try (QueryRequest qr = new QueryRequest()) {
			qr.setPreparedStatement prepare
			handle.query qr
		}
	}

	@Override
	void setDisplayName(String name) {
		updateUser([display_name: new StringValue(name)])
		displayName = name
	}

	@Override
	String getEmail() {
		return this.email
	}

	@Override
	void setEmail(String email) {
		// TODO
	}

	@Override
	boolean isVerified() {
		return isVerified
	}

	@Override
	void setVerified(boolean verified) {
		updateUser([is_verified: BooleanValue.getInstance(verified)])
		this.isVerified = verified
	}

	@Override
	List<String> getRoles() {
		if (verified) {
			return ['verified']
		}
		else {
			return []
		}
	}

	@Override
	String getPasswordHash() {
		return passwordHash
	}

	@Override
	void setPasswordHash(String hash) {
		updateUser([password_hash: new StringValue(hash)])
		passwordHash = hash
	}

	@Override
	boolean verifyEmail(String email, int code) {
		var getEmailReq = new GetRequest().tap {
			tableName = AccountOperations.emailsTableName
			key = new MapValue().tap {
				put "email", email
			}
		}
		GetResult result = handle.get getEmailReq
		MapValue foundEmail = result.value
		int ownerUid = foundEmail.get('owner_uid').getInt()
		if (ownerUid != id) {
			log.warn "User tried to verify someone else's email! email '${email}' owned by ${ownerUid} but verified by ${id}"
			return false
		}
		int expectedCode = foundEmail.get('verification_code').getInt()
		if (expectedCode != code) {
			return false
		}
		updateEmail email, [verified: BooleanValue.getInstance(true)]
		verified = true
	}
}
