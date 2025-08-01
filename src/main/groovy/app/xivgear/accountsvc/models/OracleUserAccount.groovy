package app.xivgear.accountsvc.models

import app.xivgear.accountsvc.nosql.EmailCol
import app.xivgear.accountsvc.nosql.EmailsTable
import app.xivgear.accountsvc.nosql.UserCol
import app.xivgear.accountsvc.nosql.UsersTable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.core.annotation.Nullable
import oracle.nosql.driver.ops.GetResult
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
	@Nullable
	Integer passwordResetToken

	private final UsersTable usersTable
	private final EmailsTable emailsTable

	OracleUserAccount(MapValue value, UsersTable usersTable, EmailsTable emailsTable) {
		id = value.getInt(UserCol.user_id.name())
		this.email = value.getString(UserCol.email.name())
		passwordHash = value.getString(UserCol.password_hash.name())
		passwordResetToken = value.get(UserCol.password_reset_token.name()).with {
			it.isInteger() ? it.getInt() : null
		}
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

	/*
	Possible design for email change:
	1. Add new column to emails: "is_secondary" (boolean, default false) and a new column "pending_email" to user
	2. When verifying the secondary email, in this order:
		i. Set email.is_verified = true
		(if it fails here, then the user account will still have a pending_email)
		ii. Change email on the account, null out pending_email.
		(if it fails here, then the lack of is_secondary doesn't really matter)
		iii. Set email.is_secondary = false
		(if it fails here, there's db pollution from the old email, but we can always clean up later by cross-referencing the fact that the
		email's owner UID doesn't have it listed as an email).
		iv. Delete old email entry
	3. If the user never verifies the new email, it will never become their new email.
	4. If the user changes their email, then changes it again prior to verifying, then we have a junk entry in the DB.
	This entry can be identified by is_verified == false && is_secondary == true
	 */

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
		updateUser([
				(UserCol.password_hash)       : new StringValue(hash),
				// Also remove any password reset token
				(UserCol.password_reset_token): NullValue.instance
		])
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

	@Override
	@Nullable
	Integer getPasswordResetToken() {
		return this.passwordResetToken
	}

	@Override
	void setPasswordResetToken(int token) {
		updateUser([(UserCol.password_reset_token): new IntegerValue(token)])
		this.passwordResetToken = token
	}
}
