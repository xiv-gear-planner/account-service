package app.xivgear.accountsvc.logging

import groovy.transform.CompileStatic
import io.micronaut.http.HttpRequest
import jakarta.inject.Singleton

@Singleton
@CompileStatic
class IpAddressResolver {

	String resolveIp(HttpRequest<?> request) {
		return request.headers
				.get("CF-Connecting-IP")
				?: request.remoteAddress.address.hostAddress.toString()
	}

}