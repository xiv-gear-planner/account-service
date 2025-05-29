package app.xivgear.accountsvc.models

import groovy.transform.CompileStatic
import oracle.nosql.driver.NoSQLHandle
import oracle.nosql.driver.ops.PutRequest
import oracle.nosql.driver.values.MapValue

@CompileStatic
class OracleUserAccount implements UserAccount {

	final int id
	private String email
	String displayName
	private boolean isVerified

	private final NoSQLHandle handle
	private final String tableName

	OracleUserAccount(NoSQLHandle handle, String tableName, MapValue value) {
		this.handle = handle
		this.tableName = tableName
		id = value.get("user_id").int
		email = value.get("email").int
		// TODO: remove from schema
//		verificationCode = value.get("verification_code").with {
//			if (isNull()) {
//				return null
//			}
//			return asInteger().value
//		}
		isVerified = value.get("is_verified").boolean
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
		return []
	}
	// TODO: add method for enumerating all emails associated with a user
}
