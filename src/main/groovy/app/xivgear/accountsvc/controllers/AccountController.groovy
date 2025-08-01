package app.xivgear.accountsvc.controllers

import app.xivgear.accountsvc.AccountOperations
import app.xivgear.accountsvc.auth.CredentialValidator
import app.xivgear.accountsvc.auth.PasswordHasher
import app.xivgear.accountsvc.dto.*
import app.xivgear.accountsvc.exceptions.EmailInUseException
import app.xivgear.accountsvc.exceptions.UserAccountNotFoundException
import app.xivgear.accountsvc.models.UserAccount
import app.xivgear.accountsvc.session.SessionTokenStore
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Property
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.cookie.Cookie
import io.micronaut.http.cookie.SameSite
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.rules.SecurityRule
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.annotation.security.PermitAll
import jakarta.inject.Singleton
import jakarta.validation.Valid

import java.time.Duration

/**
 * Endpoints for account management, including login and registration.
 *
 * TODO: input validation for all endpoints
 */
@Controller("/account")
@Context
@Singleton
@CompileStatic
@ExecuteOn(TaskExecutors.BLOCKING)
@SecurityRequirement(name = 'AntiCsrfHeaderAuth')
@Slf4j
class AccountController {

	private final @Nullable
	String cookieDomain
	private final AccountOperations accOps
	private final SessionTokenStore<Integer> sts
	private final CredentialValidator cv
	private final JwtTokenGenerator jwtGen
	private final PasswordHasher passwordHasher

	AccountController(
			@Nullable @Property(name = 'xivgear.accountService.cookieDomain') String cookieDomain,
			AccountOperations accOps,
			SessionTokenStore sts,
			CredentialValidator cv,
			JwtTokenGenerator jwtGen,
			PasswordHasher passwordHasher
	) {
		this.cookieDomain = cookieDomain
		this.accOps = accOps
		this.sts = sts
		this.cv = cv
		this.jwtGen = jwtGen
		this.passwordHasher = passwordHasher
	}
//
//	@PermitAll
//	@Get("/count")
//	String count() {
//		return accOps.count().toString()
//	}

	/**
	 * Creates a session cookie with the desired properties
	 *
	 * @param token The token string
	 * @param isSecure Whether the connection is secure
	 * @return The cookie
	 */
	Cookie createSessionCookie(String token, String origin) {
		return createAuthCookie("SESSION", token, origin)
	}

	Cookie createAuthCookie(String key, String value, String origin) {
		return Cookie.of(key, value).with {
			httpOnly true
			secure true
			sameSite SameSite.Lax
			path "/"
			maxAge Duration.ofDays(90)
			domain(cookieDomain ?: origin)
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
	@ApiResponse(
			responseCode = "200",
			description = "Successful registration",
			content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = RegisterResponse.class)
			)
	)
	@ApiResponse(
			responseCode = "400",
			description = "Validation error",
			content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = ValidationErrorResponse.class)
			)
	)
	HttpResponse<?> register(@Body @Valid RegisterRequest regRequest, HttpRequest<?> req) {

		String origin = getOrigin req
		if (origin == null) {
			return HttpResponse.badRequest()
		}
		int user
		try {
			user = accOps.addUser regRequest.email(), regRequest.displayName(), regRequest.password()
		}
		catch (EmailInUseException ignored) {
			return HttpResponse.badRequest(new ValidationErrorResponse().tap {
				validationErrors = [
						new ValidationErrorSingle().tap {
							field = "email"
							message = "This email is already in use"
							path = "register.regRequest.email"
						}
				]
			})
		}
		String token = sts.createSessionToken user
		Cookie sessionCookie = createSessionCookie token, origin
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

		String origin = getOrigin request
		if (origin == null) {
			return HttpResponse.badRequest()
		}

		Optional<UserAccount> userMaybe = cv.validateCredentials loginRequest.email(), loginRequest.password()

		if (!userMaybe.present) {
			return HttpResponse.unauthorized()
		}

		UserAccount user = userMaybe.get()
		String token = sts.createSessionToken user.id

		Cookie sessionCookie = createSessionCookie token, origin

		return HttpResponse.ok(new LoginResponse(new AccountInfo(user), "Login successful"))
				.cookie(sessionCookie)
	}

	/**
	 * Forgot password (password reset request)
	 *
	 * This is part 1 - you request that a code be sent.
	 */
	@PermitAll
	@Post("/initiatePasswordReset")
	HttpResponse<?> initiatePasswordReset(@Body @Valid InitiatePasswordResetRequest body, HttpRequest<?> request) {

		String origin = getOrigin request
		if (origin == null) {
			return HttpResponse.badRequest()
		}
		try {
			accOps.initiatePasswordReset body.email
		}
		catch (UserAccountNotFoundException ignored) {
			return HttpResponse.notFound()
		}
		return HttpResponse.ok()
	}

	/**
	 * Forgot password (password reset request)
	 *
	 * This is part 2 - user receives email with code, this is how they enter the code.
	 */
	@ApiResponse(
			responseCode = "200",
			description = "Successful reset"
	)
	@ApiResponse(
			responseCode = "400",
			description = "Validation error",
			content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = ValidationErrorResponse.class)
			)
	)
	@PermitAll
	@Post("/finalizePasswordReset")
	HttpResponse<?> finalizePasswordReset(@Body @Valid FinalizePasswordResetRequest body, HttpRequest<?> request) {

		String origin = getOrigin request
		if (origin == null) {
			return HttpResponse.badRequest()
		}
		boolean result
		try {
			result = accOps.finalizePasswordReset body.email, body.token, body.newPassword
		}
		catch (UserAccountNotFoundException ignored) {
			return HttpResponse.notFound()
		}
		if (result) {
			return HttpResponse.ok()
		}
		else {
			return HttpResponse.badRequest(new ValidationErrorResponse().tap {
				validationErrors = [
				        new ValidationErrorSingle().tap {
							message = 'Incorrect Code'
							field = 'token'
							path = 'finalizePasswordReset.body.token'
						}
				]
			})
		}
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
		if (token.present) {
			sts.invalidateSessionToken token.get()
			return HttpResponse.status(HttpStatus.OK)
					.cookie(Cookie.of("SESSION", "").with {
						httpOnly true
						secure true
						sameSite SameSite.Lax
						path "/"
						maxAge Duration.ZERO
					})
		}
		else {
			return HttpResponse.unauthorized()
		}
	}

	/**
	 * Get information about the currently logged-in account. Returns 401 unauthorized if not logged in.
	 * @param auth
	 * @return
	 */
	@Get("/info")
	@Secured(SecurityRule.IS_AUTHENTICATED)
	AccountInfo accountInfo(Authentication auth) {
		var user = auth.attributes['userData'] as UserAccount
		return new AccountInfo(user)
	}

	/**
	 * Get information about the currently logged-in account. Returns a response where loggedIn == false and
	 * accountInfo == null if not logged in.
	 *
	 * @param auth
	 * @return
	 */
	@Get("/current")
	@PermitAll
	AccountInfoResponse currentAccount(@Nullable Authentication auth) {
		return new AccountInfoResponse().tap {
			if (auth == null) {
				loggedIn = false
			}
			else {
				var user = auth.attributes['userData'] as UserAccount
				loggedIn = true
				accountInfo = new AccountInfo(user)
			}
		}
	}

	@Get("/jwt")
	@Secured(SecurityRule.IS_AUTHENTICATED)
	HttpResponse<JwtResponse> getJwt(Authentication auth) {
		var user = auth.attributes['userData'] as UserAccount
		// The normal auth contains the user as an attribute, but the UserData object can't necessarily be
		// serialized. Thus we need to create a more minimal version.
		var minAuth = Authentication.build auth.name, auth.roles, [
				'userId': user.id,
		] as Map<String, Object>
		Optional<String> token = jwtGen.generateToken minAuth, 30 * 60
		if (token.present) {
			return HttpResponse.ok(new JwtResponse(token.get()))
		}
		else {
			return HttpResponse.unauthorized()
		}
	}

	@Post("/verify")
	@Secured(SecurityRule.IS_AUTHENTICATED)
	VerifyEmailResponse verifyEmail(@Body VerifyEmailRequest req, Authentication auth) {
		var user = auth.attributes['userData'] as UserAccount
		boolean verified = user.verifyEmail req.email, req.code
		return new VerifyEmailResponse().tap {
			it.verified = verified
			it.accountInfo = new AccountInfo(user)
		}
	}

	@Post("/resendVerificationCode")
	@Secured(SecurityRule.IS_AUTHENTICATED)
	void resendVerificationCode(Authentication auth) {
		var user = auth.attributes['userData'] as UserAccount
		accOps.resendVerificationCode(user, user.email)
	}

	@Post("/changePassword")
	@Secured(SecurityRule.IS_AUTHENTICATED)
	HttpResponse<ChangePasswordResponse> changePassword(@Body @Valid ChangePasswordRequest req, Authentication auth, HttpRequest<?> request) {
		String origin = getOrigin(request)
		if (origin == null) {
			return HttpResponse.badRequest()
		}
		var user = auth.attributes['userData'] as UserAccount
		boolean existingCorrect = passwordHasher.verifyPassword req.existingPassword, user.passwordHash
		if (existingCorrect) {
			user.passwordHash = passwordHasher.saltAndHash req.newPassword
			// Invalidate old token then get new token
			sts.invalidateAllForUser user.id
			String token = sts.createSessionToken user.id

			Cookie sessionCookie = createSessionCookie token, origin

			return HttpResponse.ok(new ChangePasswordResponse(true))
					.cookie(sessionCookie)
		}
		else {
			return HttpResponse.<ChangePasswordResponse> unauthorized().body(new ChangePasswordResponse(false))
		}
	}

	private static @Nullable
	String getOrigin(HttpRequest<?> req) {
		if (req.origin.present) {
			String rawOrigin = req.origin.get()
			String host = new URI(rawOrigin).host
			if (host == null) {
				log.warn "Bad origin: ${rawOrigin} for ${req.method} ${req.uri}"
			}
			return host
		}
		log.warn "Missing origin header for ${req.method} ${req.uri}"
		return null
	}
}
