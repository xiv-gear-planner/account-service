package app.xivgear.accountsvc

import app.xivgear.accountsvc.dto.*
import app.xivgear.accountsvc.models.UserAccount
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Context
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.cookie.Cookie
import io.micronaut.http.cookie.SameSite
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.rules.SecurityRule
import jakarta.annotation.security.PermitAll
import jakarta.inject.Singleton

import java.time.Duration

@Controller("/account")
@Context
@Singleton
//@TupleConstructor(includeFields = true)
@CompileStatic
class AccountController {

	private final AccountOperations accOps
	private final SessionTokenStore<Integer> sts
	private final CredentialValidator cv

	AccountController(AccountOperations accOps, SessionTokenStore sts, CredentialValidator cv) {
		this.accOps = accOps
		this.sts = sts
		this.cv = cv
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

	static Cookie createSessionCookie(String token, boolean isSecure) {
		return Cookie.of("SESSION", token).with {
			httpOnly true
			secure isSecure
			sameSite SameSite.Lax
			path "/"
			maxAge Duration.ofDays(90)
		}
	}

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

	@Post
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

	@Get("/info")
	@Secured(SecurityRule.IS_AUTHENTICATED)
	CheckAuthResponse checkAuth(Authentication authentication) {
		var user = authentication.attributes['userData'] as UserAccount
		return new CheckAuthResponse(user.id, user.email, user.roles, user.verified)
	}


}
