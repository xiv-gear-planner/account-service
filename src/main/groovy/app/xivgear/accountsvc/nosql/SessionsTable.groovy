package app.xivgear.accountsvc.nosql

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Property
import jakarta.inject.Singleton
import oracle.nosql.driver.NoSQLHandle
import oracle.nosql.driver.values.FieldValue
import oracle.nosql.driver.values.StringValue

@Context
@Singleton
@CompileStatic
class SessionsTable extends RawNoSqlTable<SessionCol, String> {

	// TODO: put DDL here
	// Just rely on table TTL for expiry

	SessionsTable(
			@Property(name = 'oracle-nosql.tables.sessions.name') String tableName,
			NoSQLHandle handle
	) {
		super(tableName, SessionCol.session_key, handle)
	}

	@Override
	protected FieldValue pkToFieldValue(String pk) {
		return new StringValue(pk)
	}
}
