package app.xivgear.accountsvc.controllers

import app.xivgear.accountsvc.dto.ValidationErrorResponse
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Context
import io.micronaut.core.annotation.Order
import io.micronaut.core.order.Ordered
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton
import jakarta.validation.ConstraintViolationException

@Context
@Singleton
@Order(Ordered.HIGHEST_PRECEDENCE)
@CompileStatic
class ConstraintViolationExceptionHandler implements ExceptionHandler<ConstraintViolationException, HttpResponse<?>> {

	@Override
	HttpResponse<ValidationErrorResponse> handle(HttpRequest request, ConstraintViolationException exception) {
		Map<String, String> errors = new HashMap<>()
		exception.constraintViolations.each {cv ->
			errors[cv.propertyPath.toString()] = cv.message
		}
		var out = new ValidationErrorResponse()
		out.errors = errors
		return HttpResponse.badRequest(out)
	}

}
