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

import static app.xivgear.accountsvc.nosql.UserCol.display_name
import static app.xivgear.accountsvc.nosql.UserCol.display_name
import static app.xivgear.accountsvc.nosql.UserCol.email
import static app.xivgear.accountsvc.nosql.UserCol.email
import static app.xivgear.accountsvc.nosql.UserCol.is_verified
import static app.xivgear.accountsvc.nosql.UserCol.password_hash
import static app.xivgear.accountsvc.nosql.UserCol.roles
import static app.xivgear.accountsvc.nosql.UserCol.user_id
import static app.xivgear.accountsvc.nosql.UserCol.user_id

@Context
@Singleton
@CompileStatic
class EmailsTable extends RawNoSqlTable<EmailCol, String> {

	// Uses a separate table of emails so that we can enforce uniqueness. Oracle NoSQL does not offer a "unqiue index"
	// feature, and normal indices are only atomic within a shard.
	/*
	CREATE TABLE emails_test (
		email string,
		owner_uid integer DEFAULT -1 NOT NULL,
		verified boolean DEFAULT false NOT NULL,
		verification_code integer,
		PRIMARY KEY ( SHARD ( email ) )
	)
	// TODO: make index for owner_uid
	 */

	EmailsTable(
			@Property(name = 'oracle-nosql.tables.emails.name') String tableName,
			NoSQLHandle handle
	) {
		super(tableName, EmailCol.email, handle)
	}

	@Override
	protected FieldValue pkToFieldValue(String pk) {
		return new StringValue(pk)
	}

	@Override
	protected void initTable() {
		String ddl = """CREATE TABLE IF NOT EXISTS ${tableName} (
email STRING, owner_uid INTEGER NOT NULL DEFAULT -1, verified BOOLEAN NOT NULL DEFAULT false, verification_code INTEGER, 
PRIMARY KEY(SHARD(email)))
"""

		String incides = """
CREATE INDEX IF NOT EXISTS email_index ON ${tableName}(${email});
CREATE INDEX IF NOT EXISTS display_name_index ON ${tableName}(${display_name});
"""

		var tr = new TableRequest().tap {
			statement = ddl
		}
		TableResult result = handle.tableRequest(tr)
		result.waitForCompletion(handle, 30_000, 500)
	}
}
