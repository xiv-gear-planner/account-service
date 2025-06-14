package app.xivgear.accountsvc.controllers

import app.xivgear.accountsvc.nosql.RawNoSqlTable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.swagger.v3.oas.annotations.Operation
import jakarta.annotation.security.PermitAll

@Controller("/")
@CompileStatic
@Context
@PermitAll
@Slf4j
class HealthReadyCheck {
	@SuppressWarnings(['GrMethodMayBeStatic'])
	@Operation(summary = "Health Check")
	@Get("/healthz")
	@Produces(MediaType.TEXT_PLAIN)
	String healthCheck() {
		return "Healthy"
	}

	@SuppressWarnings(['GrMethodMayBeStatic', 'unused'])
	@Operation(summary = "Ready Check")
	@Get("/readyz")
	@Produces(MediaType.TEXT_PLAIN)
	HttpResponse<String> readyCheck(List<RawNoSqlTable> tables) {
		tables.forEach {
			if (!it.initialized) {
				log.info "Table ${it.tableName} not initialized"
				return HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
						.body("Not Ready")
			}
		}
		return HttpResponse.ok("Ready")
	}

}
