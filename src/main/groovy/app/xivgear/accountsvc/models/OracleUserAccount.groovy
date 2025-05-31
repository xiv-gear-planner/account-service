package app.xivgear.accountsvc.models

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.CompileStatic
import oracle.nosql.driver.NoSQLHandle
import oracle.nosql.driver.ops.PutRequest
import oracle.nosql.driver.values.MapValue

/**
 * Oracle NoSQL-backed user account
 */
@CompileStatic
class OracleUserAccount implements UserAccount {

	final int id
	private String email
	String displayName
	private boolean isVerified
	String passwordHash

	private final NoSQLHandle handle
	private final String tableName

	OracleUserAccount(NoSQLHandle handle, String tableName, MapValue value) {
		this.handle = handle
		this.tableName = tableName
		id = value.get("user_id").int
		email = value.get("email").getString()
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

	private void update(@DelegatesTo(MapValue) Closure<?> ops) {
		var req = new PutRequest().tap {
			tableName = this.tableName
			option = PutRequest.Option.IfPresent
			value = new MapValue().tap {
				ops.call(it)
			}
		}
		handle.put req
	}

	@Override
	void setDisplayName(String name) {
		update {
			put "display_name", name
		}
		displayName = name
	}

	@Override
	String getEmail() {
		return email
	}

	@Override
	void setEmail() {
		// TODO

	}

	@Override
	boolean isVerified() {
		return isVerified
	}

	@Override
	void setVerified(boolean verified) {
		update {
			put "is_verified", true
		}
		this.isVerified = true
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
		update {
			put "password_hash", hash
		}
		passwordHash = hash

	}
}
