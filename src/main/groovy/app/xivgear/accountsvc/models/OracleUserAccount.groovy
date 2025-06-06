package app.xivgear.accountsvc.models


import app.xivgear.accountsvc.nosql.EmailCol
import app.xivgear.accountsvc.nosql.EmailsTable
import app.xivgear.accountsvc.nosql.UserCol
import app.xivgear.accountsvc.nosql.UsersTable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
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

	private final UsersTable usersTable
	private final EmailsTable emailsTable

	OracleUserAccount(MapValue value, UsersTable usersTable, EmailsTable emailsTable) {
		id = value.getInt(UserCol.user_id.name())
		this.email = value.getString(UserCol.email.name())
		passwordHash = value.getString(UserCol.password_hash.name())
		isVerified = value.getBoolean("is_verified")
		this.usersTable = usersTable
		this.emailsTable = emailsTable
	}

	private void updateUser(Map<UserCol, ? extends FieldValue> keyValues) {
		usersTable.updateByPk(id, keyValues)
	}

	private void updateEmail(String email, Map<EmailCol, ? extends FieldValue> keyValues) {
		emailsTable.updateByPk(email, keyValues)
	}

	@Override
	void setDisplayName(String name) {
		updateUser([(UserCol.user_id): new StringValue(name)])
		displayName = name
	}

	@Override
	String getEmail() {
		return this.email
	}

	@Override
	void setEmail(String email) {
		throw new RuntimeException("not implemented")
		// TODO
	}

	@Override
	boolean isVerified() {
		return isVerified
	}

	@Override
	void setVerified(boolean verified) {
		updateUser([(UserCol.is_verified): BooleanValue.getInstance(verified)])
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
		updateUser([(UserCol.password_hash): new StringValue(hash)])
		passwordHash = hash
	}

	@Override
	boolean verifyEmail(String email, int code) {
		GetResult result = emailsTable.get email
		MapValue foundEmail = result.value
		int ownerUid = foundEmail.getInt('owner_uid')
		if (ownerUid != id) {
			log.warn "User tried to verify someone else's email! email '${email}' owned by ${ownerUid} but verified by ${id}"
			return false
		}
		int expectedCode = foundEmail.getInt 'verification_code'
		if (expectedCode != code) {
			return false
		}
		updateEmail email, [(EmailCol.verified): BooleanValue.getInstance(true)]
		verified = true
	}
}
