package app.xivgear.accountsvc.auth

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Context
import jakarta.inject.Singleton
import org.bouncycastle.util.encoders.Hex

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.security.SecureRandom
import java.security.spec.KeySpec

@Context
@Singleton
@CompileStatic
class PasswordHasher {

	String saltAndHash(String password) {

		SecureRandom random = new SecureRandom()
		byte[] salt = new byte[32]
		random.nextBytes salt

		KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256)
		SecretKeyFactory factory = SecretKeyFactory.getInstance "PBKDF2WithHmacSHA512"

		byte[] hash = factory.generateSecret(spec).encoded

		return "${new String(Hex.encode(salt))}:${new String(Hex.encode(hash))}"
	}

	boolean verifyPassword(String password, String storedSaltedHash) {
		List<byte[]> parts = storedSaltedHash.split(':').collect { Hex.decode(it) }
		var storedSalt = parts[0]
		var storedHash = parts[1]

		KeySpec spec = new PBEKeySpec(password.toCharArray(), storedSalt, 65536, 256)
		SecretKeyFactory factory = SecretKeyFactory.getInstance "PBKDF2WithHmacSHA512"
		byte[] hashToCheck = factory.generateSecret(spec).encoded

		return Arrays.equals(hashToCheck, storedHash)
	}

}
