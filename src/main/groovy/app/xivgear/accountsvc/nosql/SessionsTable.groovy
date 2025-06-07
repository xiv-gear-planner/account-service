package app.xivgear.accountsvc.nosql

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Property
import jakarta.inject.Singleton
import oracle.nosql.driver.NoSQLHandle
import oracle.nosql.driver.ops.TableLimits
import oracle.nosql.driver.values.FieldValue
import oracle.nosql.driver.values.StringValue

import static app.xivgear.accountsvc.nosql.SessionCol.owner_uid
import static app.xivgear.accountsvc.nosql.SessionCol.session_key

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
		super(tableName, session_key, handle)
	}

	@Override
	protected FieldValue pkToFieldValue(String pk) {
		return new StringValue(pk)
	}


	@Override
	protected TableLimits getTableLimits() {
		return new TableLimits(25, 5, 1)
	}

	@Override
	protected String getTableDdl() {
		return """CREATE TABLE IF NOT EXISTS ${tableName} ( 
${session_key} String , 
${owner_uid} Integer DEFAULT -1 NOT NULL , 
Primary Key( Shard( ${session_key} ) ) ) USING TTL 365 days
"""
	}

	@Override
	protected Map<String, SessionCol> getTableIndicesDdl() {
		return [uid_index: owner_uid]
	}
}
