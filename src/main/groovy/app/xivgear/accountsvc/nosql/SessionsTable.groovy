package app.xivgear.accountsvc.nosql

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Property
import jakarta.inject.Singleton
import oracle.nosql.driver.NoSQLHandle
import oracle.nosql.driver.ops.TableRequest
import oracle.nosql.driver.ops.TableResult
import oracle.nosql.driver.values.FieldValue
import oracle.nosql.driver.values.StringValue

import static app.xivgear.accountsvc.nosql.SessionCol.*

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
	protected void initTable() {
		String ddl = """CREATE TABLE IF NOT EXISTS ${tableName} ( 
${session_key} String , 
${owner_uid} Integer DEFAULT -1 NOT NULL , 
Primary Key( Shard( ${session_key} ) ) ) USING TTL 365 days
"""

//		String incides = """
//CREATE INDEX IF NOT EXISTS email_index ON ${tableName}(${email});
//CREATE INDEX IF NOT EXISTS display_name_index ON ${tableName}(${display_name});
//"""

		var tr = new TableRequest().tap {
			statement = ddl
		}
		TableResult result = handle.tableRequest(tr)
		result.waitForCompletion(handle, 30_000, 500)
	}
}
