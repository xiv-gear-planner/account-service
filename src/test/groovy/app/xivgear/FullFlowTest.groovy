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
	}

	@Test
	void testRejectUnauth() {
		HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("account/info"))
		HttpResponse<String> response = client.toBlocking().exchange(req, Argument.STRING, Argument.STRING)
		validateResponseHeaders response
		Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.status)
	}

	@Test
	void testCreateLoginLogout() {
		// Register
		String email = "foo+${(Math.random() * 1_000_000) as int}@bar.com"
		log.info "Generated email: ${email}"
		String password = "p@ssw0rd"
		int myUid
		Cookie sessionCookie
		{
			HttpRequest<RegisterRequest> req = HttpRequest.POST(
					server.URI.resolve("account/register"),
					new RegisterRequest(email, password, "My User"))

			HttpResponse<RegisterResponse> response = client.toBlocking().exchange(req, RegisterResponse)
			validateResponseHeaders response

			myUid = response.body().uid()
			sessionCookie = response.getCookie("SESSION").orElseThrow()
		}

		// Registration implicitly logs you in.
		// Check account details.
		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("account/info")).with {
				cookie sessionCookie
			}
			HttpResponse<AccountInfo> response = client.toBlocking().exchange req, AccountInfo
			validateResponseHeaders response
			Assertions.assertEquals HttpStatus.OK, response.status
			Assertions.assertEquals email, response.body().email()
			Assertions.assertEquals myUid, response.body().uid()
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

		// Log out.
		{
			HttpRequest<?> req = HttpRequest.POST(server.URI.resolve("account/logout"), "").with {
				cookie sessionCookie
			}
			HttpResponse<String> response = client.toBlocking().exchange req, String
			validateResponseHeaders response
			Assertions.assertEquals HttpStatus.OK, response.status
		}

		// Verify logged out
		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("account/info"))
			HttpResponse<String> response = client.toBlocking().exchange(req, Argument.STRING, Argument.STRING)
			validateResponseHeaders response
			Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.status)
		}

		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("account/jwt"))
			HttpResponse<String> response = client.toBlocking().exchange(req, Argument.STRING, Argument.STRING)
			validateResponseHeaders response
			Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.status)
		}

		// Log in
		{
			HttpRequest<LoginRequest> req = HttpRequest.POST(
					server.URI.resolve("account/login"),
					new LoginRequest(email, password))

			HttpResponse<LoginResponse> response = client.toBlocking().exchange(req, LoginResponse)
			validateResponseHeaders response

			myUid = response.body().accountInfo.uid
			sessionCookie = response.getCookie("SESSION").orElseThrow()
		}
		// Check details again
		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("account/info")).with {
				cookie sessionCookie
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
					})
					.with {
						cookie sessionCookie
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
					})
					.with {
						cookie sessionCookie
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
					new RegisterRequest(email, password, displayName))

			HttpResponse<ValidationErrorResponse> response = client.toBlocking().exchange(req, Argument.of(ValidationErrorResponse), Argument.of(ValidationErrorResponse))

			Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.status())
			ValidationErrorResponse body = response.body()
			Assertions.assertEquals(2, body.errors.size())
			Assertions.assertEquals("Must be a valid email", body.errors['register.regRequest.email'])
			Assertions.assertEquals("Display Name must be between 2 and 64 characters", body.errors['register.regRequest.displayName'])
			validateResponseHeaders response
		}
	}

	List<Email> emails = []


	@MockBean(TransactionalEmailSender.class)
	@Named("mock")
	TransactionalEmailSender<Message, Void> mockSender() {
		return new TransactionalEmailSender() {

			@Override
			String getName() {
				return "test"
			}

			@Override
			Void send(Email email, Consumer emailRequest) throws EmailException {
				emails.add email
				log.info "Email subject: ${email.subject}"
				return null
			}
		}
	}
}
