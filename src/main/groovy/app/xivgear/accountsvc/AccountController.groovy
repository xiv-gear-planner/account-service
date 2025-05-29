package app.xivgear.accountsvc

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Context
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import jakarta.inject.Singleton

@Controller("/account")
@Context
@Singleton
//@TupleConstructor(includeFields = true)
@CompileStatic
class AccountController {
	private final OracleNoSqlConnector conn

	AccountController(OracleNoSqlConnector conn) {
		this.conn = conn
	}

	@Get("/count")
	String count() {
		return conn.count().toString()
	}

	@Get("/demo")
	String demo() {
		var uid = conn.addUser("foo+${Math.floor(Math.random() * 1_000_000)}@bar.com", "My User", "p@ssw0rd")
		return uid
	}

}
