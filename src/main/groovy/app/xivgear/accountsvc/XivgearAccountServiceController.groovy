package app.xivgear.accountsvc

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller("/xivgear-account-service")
class XivgearAccountServiceController {

	@Get(uri = "/", produces = "text/plain")
	String index() {
		"Example Response"
	}
}