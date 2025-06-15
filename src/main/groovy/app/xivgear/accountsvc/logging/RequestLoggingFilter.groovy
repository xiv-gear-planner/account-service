package app.xivgear.accountsvc.logging

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.core.order.Ordered
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import org.slf4j.MDC

@CompileStatic
@Filter("/**")
@Slf4j
class RequestLoggingFilter implements Ordered, HttpServerFilter {

	@Inject
	IpAddressResolver ipAddressResolver

	@Override
	Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
		String ipAddress = ipAddressResolver.resolveIp request
		MDC.put "ip", ipAddress
		Publisher<MutableHttpResponse<?>> responsePublisher = chain.proceed request
		return Publishers.<MutableHttpResponse<?>, MutableHttpResponse<?>> map(responsePublisher, { response ->
			try {
				MDC.put "ip", ipAddress
				log.info("{} {}: {}",
						request.method,
						request.path,
						response.status.code)
				return response
			}
			finally {
				MDC.remove "ip"
			}
		})
	}

	final int order = -20
}