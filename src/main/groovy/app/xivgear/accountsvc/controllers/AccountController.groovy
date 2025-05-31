package app.xivgear.accountsvc.controllers

import app.xivgear.accountsvc.AccountOperations
import app.xivgear.accountsvc.CredentialValidator
import app.xivgear.accountsvc.SessionTokenStore
import app.xivgear.accountsvc.dto.*
import app.xivgear.accountsvc.models.UserAccount
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Context
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.cookie.Cookie
import io.micronaut.http.cookie.SameSite
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.rules.SecurityRule
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator
import jakarta.annotation.security.PermitAll
import jakarta.inject.Singleton

import java.time.Duration

/**
 * Endpoints for account management, including login and registration.
 */
@Controller("/account")
@Context
@Singleton
@CompileStatic
class AccountController {

	private final AccountOperations accOps
	private final SessionTokenStore<Integer> sts
	private final CredentialValidator cv
	private final JwtTokenGenerator jwtGen

	AccountController(AccountOperations accOps, SessionTokenStore sts, CredentialValidator cv, JwtTokenGenerator jwtGen) {
		this.accOps = accOps
		this.sts = sts
		this.cv = cv
		this.jwtGen = jwtGen
	}

	@PermitAll
	@Get("/count")
	String count() {
		return accOps.count().toString()
	}

	@PermitAll
	@Get("/demo")
	String demo() {
		var uid = accOps.addUser("foo+${Math.floor(Math.random() * 1_000_000)}@bar.com", "My User", "p@ssw0rd")
		return uid
	}

	/**
	 * Creates a session cookie with the desired properties
	 *
	 * @param token The token string
	 * @param isSecure Whether the connection is secure
	 * @return The cookie
	 */
	static Cookie createSessionCookie(String token, boolean isSecure) {
		return createAuthCookie("SESSION", token, isSecure)
	}

	static Cookie createAuthCookie(String key, String value, boolean isSecure) {
		return Cookie.of(key, value).with {
			httpOnly true
			secure isSecure
			sameSite SameSite.Lax
			path "/"
			maxAge Duration.ofDays(90)
		}
	}

	/**
	 * Register a new account
	 *
	 * @param regRequest
	 * @param req
	 * @return
	 */
	@Secured(SecurityRule.IS_ANONYMOUS)
	@Post("/register")
	HttpResponse<RegisterResponse> register(@Body RegisterRequest regRequest, HttpRequest<?> req) {
		int user = accOps.addUser regRequest.email(), regRequest.displayName(), regRequest.password()
		String token = sts.createSessionToken user
		Cookie sessionCookie = createSessionCookie token, req.secure
		return HttpResponse.ok(new RegisterResponse(user)).with {
			cookie sessionCookie
		}
	}

	/**
	 * Log in with email and password
	 *
	 * @param loginRequest
	 * @param request
	 * @return
	 */
	@PermitAll
	@Post("/login")
	HttpResponse<LoginResponse> login(@Body LoginRequest loginRequest, HttpRequest<?> request) {

		Optional<UserAccount> userMaybe = cv.validateCredentials loginRequest.email(), loginRequest.password()

		if (!userMaybe.isPresent()) {
			return HttpResponse.unauthorized()
		}

		UserAccount user = userMaybe.get()
		String token = sts.createSessionToken user.id

		Cookie sessionCookie = createSessionCookie token, request.secure

		return HttpResponse.ok(new LoginResponse(user.id, "Login successful"))
				.cookie(sessionCookie)
	}

	/**
	 * Log out
	 *
	 * @param request
	 * @return
	 */
	@Post("/logout")
	@Secured(SecurityRule.IS_AUTHENTICATED)
	HttpResponse<?> logout(HttpRequest<?> request) {
		Optional<String> token = sts.extractTokenFromRequest request
		if (token.isPresent()) {
			sts.invalidateSessionToken token.get()
			return HttpResponse.status(HttpStatus.OK)
		}
		else {
			return HttpResponse.unauthorized()
		}
	}

	/**
	 * Get information about the currently logged-in account
	 * @param auth
	 * @return
	 */
	@Get("/info")
	@Secured(SecurityRule.IS_AUTHENTICATED)
	AccountInfo accountInfo(Authentication auth) {
		var user = auth.attributes['userData'] as UserAccount
		return new AccountInfo(user.id, user.email, user.roles, user.verified)
	}

	@Get("/jwt")
	@Secured(SecurityRule.IS_AUTHENTICATED)
	HttpResponse<?> getJwt(Authentication auth, HttpRequest<?> request) {
		var user = auth.attributes['userData'] as UserAccount
		// The normal auth contains the user as an attribute, but the UserData object can't necessarily be
		// serialized. Thus we need to create a more minimal version.
		var minAuth = Authentication.build auth.name, auth.roles, [
				'userId': user.id,
		] as Map<String, Object>
		Optional<String> token = jwtGen.generateToken minAuth, 30 * 60
		if (token.isPresent()) {
			Cookie jwtCookie = createAuthCookie "xivgear-jwt", token.get(), request.secure
			return HttpResponse.ok()
					.cookie(jwtCookie)
		}
		else {
			return HttpResponse.unauthorized()
		}
	}

}
