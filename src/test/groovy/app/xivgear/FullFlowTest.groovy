package app.xivgear

import app.xivgear.accountsvc.dto.AccountInfo
import app.xivgear.accountsvc.dto.LoginRequest
import app.xivgear.accountsvc.dto.LoginResponse
import app.xivgear.accountsvc.dto.RegisterRequest
import app.xivgear.accountsvc.dto.RegisterResponse
import com.nimbusds.jwt.JWTParser
import com.nimbusds.jwt.SignedJWT
import groovy.transform.CompileStatic
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.netty.DefaultHttpClient
import io.micronaut.http.cookie.Cookie
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@MicronautTest
@CompileStatic
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
		String email = "foo+${Math.floor(Math.random() * 1_000_000)}@bar.com"
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
			// TODO: registering should also log you in
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

			myUid = response.body().uid()
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
			HttpResponse<String> response = client.toBlocking().exchange req, String
			validateResponseHeaders response
			Assertions.assertEquals HttpStatus.OK, response.status
			Cookie jwtCookie = response.getCookie("xivgear-jwt").orElseThrow()
			Assertions.assertNotEquals "", jwtCookie.value
			SignedJWT parsed = JWTParser.parse(jwtCookie.value) as SignedJWT
			Map<String, Object> payload = parsed.payload.toJSONObject()
			Assertions.assertEquals myUid.toString(), payload['sub']
			Assertions.assertEquals myUid as int, payload['userId'] as int
			// Account is not verified, so should not have verified role
			Assertions.assertEquals([], payload['roles'])
		}
	}
}
