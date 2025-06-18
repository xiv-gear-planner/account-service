package app.xivgear.accountsvc

import app.xivgear.logging.IpAddressResolver
import app.xivgear.logging.RequestLoggingFilter
import groovy.transform.CompileStatic
import io.micronaut.runtime.Micronaut
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityScheme

@OpenAPIDefinition(
		info = @Info(
				title = 'xivgear-account-service',
				version = '0.0'
		)
)
@SecurityScheme(
		name = 'AntiCsrfHeaderAuth',
		type = SecuritySchemeType.APIKEY,
		in = SecuritySchemeIn.HEADER,
		paramName = 'xivgear-csrf',
		description = 'Anti-CSRF header, the correct value is "1"'
)
@CompileStatic
class Application {
	static void main(String[] args) {
		Micronaut.build(args).with {
			it.args args
			packages 'app.xivgear.logging'
		}.start()
	}
}
