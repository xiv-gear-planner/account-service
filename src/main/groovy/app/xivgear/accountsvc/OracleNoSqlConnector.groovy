package app.xivgear.accountsvc


import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Value
import io.micronaut.oraclecloud.core.OracleCloudAuthConfigurationProperties
import jakarta.inject.Singleton
import oracle.nosql.driver.NoSQLHandle
import oracle.nosql.driver.NoSQLHandleConfig
import oracle.nosql.driver.NoSQLHandleFactory
import oracle.nosql.driver.iam.SignatureProvider

@Context
@Singleton
@CompileStatic
@Slf4j
@Factory
class OracleNoSqlConnector {

	private final NoSQLHandle handle

	OracleNoSqlConnector(
			OracleCloudAuthConfigurationProperties auth,
			@Value('${oracle-nosql.endpoint}') URL endpoint,
			@Value('${oracle-nosql.compartment}') String compartmentId
	) {
		NoSQLHandleConfig config = new NoSQLHandleConfig(endpoint)
		SimpleAuthenticationDetailsProvider build = auth.builder.build()
		// TODO: easier way?
		SignatureProvider sp = new SignatureProvider(build.tenantId, build.userId, build.fingerprint, new String(build.privateKey.readAllBytes()), null)
		config.authorizationProvider = sp
		config.defaultCompartment = compartmentId
		handle = NoSQLHandleFactory.createNoSQLHandle config
	}

	@Singleton
	NoSQLHandle getHandle() {
		return handle
	}
}
