package app.xivgear.accountsvc

import app.xivgear.accountsvc.auth.CredentialValidator
import app.xivgear.accountsvc.auth.PasswordHasher
import app.xivgear.accountsvc.email.VerificationCodeSender
import app.xivgear.accountsvc.models.OracleUserAccount
import app.xivgear.accountsvc.models.UserAccount
import app.xivgear.accountsvc.nosql.EmailCol
import app.xivgear.accountsvc.nosql.EmailsTable
import app.xivgear.accountsvc.nosql.UserCol
import app.xivgear.accountsvc.nosql.UsersTable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.micronaut.core.annotation.Nullable
import jakarta.inject.Singleton
import oracle.nosql.driver.ops.GetResult
import oracle.nosql.driver.ops.PutResult
import oracle.nosql.driver.values.IntegerValue
import oracle.nosql.driver.values.MapValue
import oracle.nosql.driver.values.StringValue

import java.security.SecureRandom

/**
 * Higher-level operations for accounts, such as account creation and login verification.
 */
@Context
@Singleton
@CompileStatic
@Slf4j
class AccountOperations implements CredentialValidator {

	private final VerificationCodeSender verifier
	private final UsersTable usersTable
	private final EmailsTable emailsTable
	private final PasswordHasher passwordHasher

	AccountOperations(VerificationCodeSender verifier, UsersTable usersTable, EmailsTable emailsTable, PasswordHasher passwordHasher) {
		this.verifier = verifier
		this.usersTable = usersTable
		this.emailsTable = emailsTable
		this.passwordHasher = passwordHasher
	}

	/**
	 * Add a user
	 *
	 * @param email
	 * @param displayName
	 * @param password
	 * @return The generated user ID
	 */
	int addUser(String email, String displayName, String password) {

		password = passwordHasher.saltAndHash password
		// Store email first to make sure it is not in use
		emailsTable.putByPK email, [:]
		PutResult userResult = usersTable.put([
				(UserCol.display_name) : new StringValue(displayName),
				(UserCol.email)        : new StringValue(email),
				(UserCol.password_hash): new StringValue(password),
		])
		int uid = userResult.generatedValue.asInteger().value
		int verificationCode = Math.abs(new SecureRandom().nextInt()) % 1_000_000
		emailsTable.putByPK email, [
				(EmailCol.owner_uid)        : new IntegerValue(uid),
				(EmailCol.verification_code): new IntegerValue(verificationCode),
		]
		verifier.sendVerificationCode email, String.format("%06d", verificationCode)
		return uid
	}

	@Nullable
	UserAccount getById(int id) {
		GetResult result = usersTable.get(id)
		if (result?.value == null) {
			return null
		}
		return new OracleUserAccount(result.value, usersTable, emailsTable)
	}

	@Nullable
	UserAccount getByEmail(String email) {
		MapValue row = usersTable.queryOne([(UserCol.email): new StringValue(email)])
		return new OracleUserAccount(row, usersTable, emailsTable)
	}

	@Override
	Optional<UserAccount> validateCredentials(String email, String password) {
		UserAccount user = getByEmail email
		if (!user) {
			return Optional.empty()
		}
		if (!passwordHasher.verifyPassword(password, user.passwordHash)) {
			return Optional.empty()
		}
		return Optional.of(user)
	}

	void resendVerificationCode(UserAccount userAccount, String email) {
		// TODO: resend throttle
		GetResult result = emailsTable.get email
		MapValue foundEmail = result.value

		int ownerUid = foundEmail.getInt 'owner_uid'
		int id = userAccount.id
		if (ownerUid != id) {
			throw new IllegalArgumentException("User tried to resend verification for someone else's email! email '${email}' owned by ${ownerUid} but verified by ${id}")
		}
		verifier.sendVerificationCode email, String.format("%06d", foundEmail.getInt("verification_code"))
	}
}
