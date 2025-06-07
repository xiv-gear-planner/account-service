package app.xivgear

import app.xivgear.accountsvc.dto.*
import com.nimbusds.jwt.JWTParser
import com.nimbusds.jwt.SignedJWT
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.core.type.Argument
import io.micronaut.email.Email
import io.micronaut.email.EmailException
import io.micronaut.email.TransactionalEmailSender
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.netty.DefaultHttpClient
import io.micronaut.http.cookie.Cookie
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import jakarta.mail.Message
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

import java.util.function.Consumer

@MicronautTest
@CompileStatic
@Slf4j
class FullFlowTest {

	@Singleton
	@Inject
	EmbeddedServer server

	@Inject
	@Client
	DefaultHttpClient client

	@PostConstruct
	void configureClient() {
		client.configuration.exceptionOnErrorStatus = false
	}

	static void validateResponseHeaders(HttpResponse<?> resp) {
		Assertions.assertEquals "no-cache", resp.header("Cache-Control")
		Assertions.assertEquals "no-cache", resp.header("Pragma")
		Assertions.assertEquals "0", resp.header("Expires")
	}

	static <X> MutableHttpRequest<X> addHeaders(MutableHttpRequest<X> req) {
		req.header "xivgear-csrf", "1"
		req.header "Origin", "https://foo.bar.com/"
	}

	@Test
	void testRejectUnauth() {
		HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("account/info")).with {
			addHeaders it
		}
		HttpResponse<String> response = client.toBlocking().exchange(req, Argument.STRING, Argument.STRING)
		validateResponseHeaders response
		Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.status)
	}

	/**
	 * Test that if you don't supply the xivgear-csrf header, the request is rejected.
	 */
	@Test
	void testRejectMissingHeader() {
		HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("account/current"))
		HttpResponse<String> response = client.toBlocking().exchange(req, Argument.STRING, Argument.STRING)
		Assertions.assertEquals(HttpStatus.FORBIDDEN, response.status)
	}

	@Test
	void testFullFlow() {
		// Register
		String email = "foo+${(Math.random() * 1_000_000) as int}@bar.com"
		log.info "Generated email: ${email}"
		String password = "p@ssw0rd"
		int myUid
		Cookie sessionCookie
		{
			HttpRequest<RegisterRequest> req = HttpRequest.POST(
					server.URI.resolve("account/register"),
					new RegisterRequest(email, password, "My User")
			).with {
				addHeaders it
			}

			HttpResponse<RegisterResponse> response = client.toBlocking().exchange(req, RegisterResponse)
			validateResponseHeaders response

			myUid = response.body().uid()
			log.info "My UID: ${myUid}"
			sessionCookie = response.getCookie("SESSION").orElseThrow()
			log.info "Session cookie: ${sessionCookie}"
		}

		// Registration implicitly logs you in.
		// Check account details.
		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("account/info")).with {
				cookie sessionCookie
				addHeaders it
			}
			HttpResponse<AccountInfo> response = client.toBlocking().exchange req, AccountInfo
			validateResponseHeaders response
			Assertions.assertEquals HttpStatus.OK, response.status
			Assertions.assertEquals email, response.body().email()
			Assertions.assertEquals myUid, response.body().uid()
		}
		// Check the other account info endpoint
		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("account/current")).with {
				cookie sessionCookie
				addHeaders it
			}
			HttpResponse<AccountInfoResponse> response = client.toBlocking().exchange req, AccountInfoResponse
			validateResponseHeaders response
			Assertions.assertEquals HttpStatus.OK, response.status
			Assertions.assertTrue response.body().loggedIn
			Assertions.assertEquals email, response.body().accountInfo.email()
			Assertions.assertEquals myUid, response.body().accountInfo.uid()
		}
		// Check that email was sent
		int verificationCode
		{
			Assertions.assertEquals 1, emails.size()
			Email mail = emails[0]
			Assertions.assertTrue mail.subject.contains("Your Xivgear.app verification code is ")
			var matcher = mail.subject =~ ".*([0-9]{6})"
			Assertions.assertTrue matcher.matches()
			verificationCode = Integer.parseInt(matcher.group(1))
		}
		// Request resend
		{
			HttpRequest<?> req = HttpRequest.POST(server.URI.resolve("account/resendVerificationCode"), "").with {
				cookie sessionCookie
				addHeaders it
			}
			HttpResponse<String> response = client.toBlocking().exchange req, String
			validateResponseHeaders response
			Assertions.assertEquals HttpStatus.OK, response.status

			Assertions.assertEquals 2, emails.size()
			Email mail = emails[1]
			Assertions.assertTrue mail.subject.contains("Your Xivgear.app verification code is ")
			var matcher = mail.subject =~ ".*([0-9]{6})"
			Assertions.assertTrue matcher.matches()
			int resentVerificationCode = Integer.parseInt(matcher.group(1))
			// Verification code does not change upon resend
			Assertions.assertEquals verificationCode, resentVerificationCode
		}

		// Log out.
		{
			HttpRequest<?> req = HttpRequest.POST(server.URI.resolve("account/logout"), "").with {
				cookie sessionCookie
				addHeaders it
			}
			HttpResponse<String> response = client.toBlocking().exchange req, String
			validateResponseHeaders response
			Assertions.assertEquals HttpStatus.OK, response.status
			Cookie emptyCookie = response.getCookie("SESSION").orElseThrow()
			Assertions.assertEquals "", emptyCookie.value
		}

		// Verify logged out
		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("account/info")).with {
				addHeaders it
			}
			HttpResponse<String> response = client.toBlocking().exchange(req, Argument.STRING, Argument.STRING)
			validateResponseHeaders response
			Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.status)
		}
		// Verify in the other endpoint
		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("account/current")).with {
				cookie sessionCookie
				addHeaders it
			}
			HttpResponse<AccountInfoResponse> response = client.toBlocking().exchange req, AccountInfoResponse
			validateResponseHeaders response
			Assertions.assertEquals HttpStatus.OK, response.status
			Assertions.assertFalse response.body().loggedIn
			Assertions.assertNull response.body().accountInfo
		}

		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("account/jwt")).with {
				addHeaders it
			}
			HttpResponse<String> response = client.toBlocking().exchange(req, Argument.STRING, Argument.STRING)
			validateResponseHeaders response
			Assertions.assertEquals HttpStatus.UNAUTHORIZED, response.status
		}

		// Log in
		{
			HttpRequest<LoginRequest> req = HttpRequest.POST(
					server.URI.resolve("account/login"),
					new LoginRequest(email, password)
			).with {
				addHeaders it
			}

			HttpResponse<LoginResponse> response = client.toBlocking().exchange(req, LoginResponse)
			validateResponseHeaders response

			myUid = response.body().accountInfo.uid
			sessionCookie = response.getCookie("SESSION").orElseThrow()
		}
		// Check details again
		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("account/info")).with {
				cookie sessionCookie
				addHeaders it
			}
			HttpResponse<AccountInfo> response = client.toBlocking().exchange req, AccountInfo
			validateResponseHeaders response
			Assertions.assertEquals HttpStatus.OK, response.status
			Assertions.assertEquals email, response.body().email()
			Assertions.assertEquals myUid, response.body().uid()
		}

		// Get JWT token
		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("account/jwt")).with {
				cookie sessionCookie
				addHeaders it
			}
			HttpResponse<JwtResponse> response = client.toBlocking().exchange req, JwtResponse
			validateResponseHeaders response
			Assertions.assertEquals HttpStatus.OK, response.status
			String token = response.body().token
			Assertions.assertNotNull token
			Assertions.assertNotEquals "", token
			SignedJWT parsed = JWTParser.parse(token) as SignedJWT
			Map<String, Object> payload = parsed.payload.toJSONObject()
			Assertions.assertEquals myUid.toString(), payload['sub']
			Assertions.assertEquals myUid as int, payload['userId'] as int
			// Account is not verified, so should not have verified role
			Assertions.assertEquals([], payload['roles'])
		}

		// Fail to Verify email #1 - wrong code
		{
			HttpRequest<VerifyEmailRequest> req = HttpRequest.POST(
					server.URI.resolve("account/verify"),
					new VerifyEmailRequest().tap {
						code = 123456789
						it.email = email
					}
			).with {
				cookie sessionCookie
				addHeaders it
			}
			HttpResponse<VerifyEmailResponse> response = client.toBlocking().exchange req, VerifyEmailResponse

			VerifyEmailResponse body = response.body()
			Assertions.assertFalse body.verified
			Assertions.assertFalse body.accountInfo.verified()
			Assertions.assertEquals myUid, body.accountInfo.uid()
		}
		// TODO: add case for trying to verify someone else's email
		// Verify email successfully
		{
			HttpRequest<VerifyEmailRequest> req = HttpRequest.POST(
					server.URI.resolve("account/verify"),
					new VerifyEmailRequest().tap {
						code = verificationCode
						it.email = email
					}
			).with {
				cookie sessionCookie
				addHeaders it
			}
			HttpResponse<VerifyEmailResponse> response = client.toBlocking().exchange req, VerifyEmailResponse

			VerifyEmailResponse body = response.body()
			Assertions.assertTrue body.verified
			Assertions.assertTrue body.accountInfo.verified()
			Assertions.assertEquals myUid, body.accountInfo.uid()
		}

		// Get JWT token again, should list us as verified
		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("account/jwt")).with {
				cookie sessionCookie
				addHeaders it
			}
			HttpResponse<JwtResponse> response = client.toBlocking().exchange req, JwtResponse
			validateResponseHeaders response
			Assertions.assertEquals HttpStatus.OK, response.status
			String token = response.body().token
			Assertions.assertNotNull token
			Assertions.assertNotEquals "", token
			SignedJWT parsed = JWTParser.parse(token) as SignedJWT
			Map<String, Object> payload = parsed.payload.toJSONObject()
			Assertions.assertEquals myUid.toString(), payload['sub']
			Assertions.assertEquals myUid as int, payload['userId'] as int
			// Account is not verified, so should not have verified role
			Assertions.assertEquals(['verified'], payload['roles'])
		}

		// Log in again to give us a secondary session key
		Cookie sessionCookie2
		{
			HttpRequest<LoginRequest> req = HttpRequest.POST(
					server.URI.resolve("account/login"),
					new LoginRequest(email, password)
			).with {
				addHeaders it
			}

			HttpResponse<LoginResponse> response = client.toBlocking().exchange(req, LoginResponse)
			validateResponseHeaders response
			sessionCookie2 = response.getCookie("SESSION").orElseThrow()
		}

		// Fail to change password by supplying wrong existing password.
		{
			HttpRequest<ChangePasswordRequest> req = HttpRequest.POST(
					server.URI.resolve("account/changePassword"),
					new ChangePasswordRequest().tap {
						existingPassword = "wrong"
						newPassword = "p@ssw0rd2"
					}
			).with {
				addHeaders it
				cookie sessionCookie

			}
			HttpResponse<ChangePasswordResponse> response = client.toBlocking().exchange req, Argument.of(ChangePasswordResponse), Argument.of(ChangePasswordResponse)
			validateResponseHeaders response
			Assertions.assertFalse response.body().passwordCorrect
		}
		// Fail to change password by supplying invalid new password
		{
			HttpRequest<ChangePasswordRequest> req = HttpRequest.POST(
					server.URI.resolve("account/changePassword"),
					new ChangePasswordRequest().tap {
						existingPassword = "p@ssw0rd"
						newPassword = "2short"
					}
			).with {
				addHeaders it
				cookie sessionCookie
			}
			HttpResponse<String> responseRaw = client.toBlocking().exchange req, Argument.of(String), Argument.of(String)
			log.info("Raw: ${responseRaw.body()}")
			HttpResponse<ValidationErrorResponse> response = client.toBlocking().exchange req, Argument.of(ValidationErrorResponse), Argument.of(ValidationErrorResponse)
			validateResponseHeaders response
			List<ValidationErrorSingle> errors = response.body().validationErrors
			Assertions.assertEquals 1, errors.size()
			Assertions.assertEquals 'Password must be at least 8 characters', errors[0].message
			Assertions.assertEquals 'changePassword.req.newPassword', errors[0].path
			Assertions.assertEquals 'newPassword', errors[0].field
		}
		Cookie sessionCookie3
		// Change password. Old token should no longer work.
		{
			HttpRequest<ChangePasswordRequest> req = HttpRequest.POST(
					server.URI.resolve("account/changePassword"),
					new ChangePasswordRequest().tap {
						existingPassword = "p@ssw0rd"
						newPassword = "p@ssw0rd2"
					}
			).with {
				addHeaders it
				cookie sessionCookie
			}
			HttpResponse<ChangePasswordResponse> response = client.toBlocking().exchange(req, ChangePasswordResponse)
			validateResponseHeaders response
			Assertions.assertTrue response.body().passwordCorrect
			sessionCookie3 = response.getCookie("SESSION").orElseThrow()
		}

		// Verify that the old cookie is no longer valid
		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("account/current")).with {
				cookie sessionCookie
				addHeaders it
			}
			HttpResponse<AccountInfoResponse> response = client.toBlocking().exchange req, AccountInfoResponse
			validateResponseHeaders response
			Assertions.assertEquals HttpStatus.OK, response.status
			Assertions.assertFalse response.body().loggedIn
			Assertions.assertNull response.body().accountInfo
		}
		// Verify that the other old cookie is no longer valid
		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("account/current")).with {
				cookie sessionCookie2
				addHeaders it
			}
			HttpResponse<AccountInfoResponse> response = client.toBlocking().exchange req, AccountInfoResponse
			validateResponseHeaders response
			Assertions.assertEquals HttpStatus.OK, response.status
			Assertions.assertFalse response.body().loggedIn
			Assertions.assertNull response.body().accountInfo
		}
		// Verify that the new cookie is valid
		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("account/current")).with {
				cookie sessionCookie3
				addHeaders it
			}
			HttpResponse<AccountInfoResponse> response = client.toBlocking().exchange req, AccountInfoResponse
			validateResponseHeaders response
			Assertions.assertEquals HttpStatus.OK, response.status
			Assertions.assertTrue response.body().loggedIn
			Assertions.assertEquals email, response.body().accountInfo.email()
			Assertions.assertEquals myUid, response.body().accountInfo.uid()
		}

		// TODO: forgot password reset


	}

	@Test
	void testRegistrationValidation() {
		// Use an invalid email
		String email = "foo_not_an_email"
		String password = "p@ssw0rd"
		// Also use an invalid display name
		String displayName = "M"
		int myUid
		{
			HttpRequest<RegisterRequest> req = HttpRequest.POST(
					server.URI.resolve("account/register"),
					new RegisterRequest(email, password, displayName)
			).with {
				addHeaders it
			}

			HttpResponse<ValidationErrorResponse> response = client.toBlocking().exchange(req, Argument.of(ValidationErrorResponse), Argument.of(ValidationErrorResponse))

			Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.status())
			ValidationErrorResponse body = response.body()

			List<ValidationErrorSingle> errors = body.validationErrors
			errors.sort { it.path }
			Assertions.assertEquals 2, errors.size()
			Assertions.assertEquals 'Display Name must be between 2 and 64 characters', errors[0].message
			Assertions.assertEquals 'register.regRequest.displayName', errors[0].path
			Assertions.assertEquals 'displayName', errors[0].field
			Assertions.assertEquals 'Must be a valid email', errors[1].message
			Assertions.assertEquals 'register.regRequest.email', errors[1].path
			Assertions.assertEquals 'email', errors[1].field
			validateResponseHeaders response
		}
	}

	List<Email> emails = []


	@MockBean(TransactionalEmailSender.class)
	@Named("mock")
	TransactionalEmailSender<Message, Void> mockSender() {
		return new TransactionalEmailSender() {

			final String name = 'test'

			@Override
			Void send(Email email, Consumer emailRequest) throws EmailException {
				emails << email
				log.info "Email subject: ${email.subject}"
				return null
			}
		}
	}
}
