package app.xivgear

import app.xivgear.accountsvc.dto.CheckAuthResponse
import app.xivgear.accountsvc.dto.RegisterRequest
import app.xivgear.accountsvc.dto.RegisterResponse
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

	@Test
	void testRejectInfo() {
		HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("account/info"))
		HttpResponse<String> response = client.toBlocking().exchange(req, Argument.STRING, Argument.STRING)
		Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.status)
	}

	@Test
	void testCreateLoginLogout() {
		String email = "foo+${Math.floor(Math.random() * 1_000_000)}@bar.com"
		Cookie sessionCookie
		{
			HttpRequest<RegisterRequest> req = HttpRequest.POST(
					server.URI.resolve("account/register"),
					new RegisterRequest(email, "p@ssw0rd", "My User"))

			HttpResponse<RegisterResponse> response = client.toBlocking().exchange(req, RegisterResponse)

			int myUid = response.body().uid()
			sessionCookie = response.getCookie("SESSION").orElseThrow()
			// TODO: registering should also log you in
		}

		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("account/info")).with {
				cookie sessionCookie
			}
			HttpResponse<CheckAuthResponse> response = client.toBlocking().exchange(req, CheckAuthResponse)
			Assertions.assertEquals(HttpStatus.OK, response.status)
			response.body().email()
		}
	}
}
