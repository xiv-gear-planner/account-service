package app.xivgear.accountsvc.nosql

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Property
import jakarta.inject.Singleton
import oracle.nosql.driver.NoSQLHandle
import oracle.nosql.driver.ops.TableLimits
import oracle.nosql.driver.ops.TableRequest
import oracle.nosql.driver.ops.TableResult
import oracle.nosql.driver.values.FieldValue
import oracle.nosql.driver.values.IntegerValue

import static app.xivgear.accountsvc.nosql.UserCol.*

@Context
@Singleton
@CompileStatic
class UsersTable extends RawNoSqlTable<UserCol, Integer> {
	/*
	CREATE TABLE users_test (
		user_id integer GENERATED ALWAYS AS IDENTITY,
		display_name string,
		email string,
		is_verified boolean DEFAULT false NOT NULL,
		roles json,
		password_hash string,
		PRIMARY KEY ( SHARD ( user_id ) )
	)
	*/


	UsersTable(@Property(name = 'oracle-nosql.tables.users.name') String tableName,
			   NoSQLHandle handle) {
		super(tableName, user_id, handle)
	}

	@Override
	protected FieldValue pkToFieldValue(Integer pk) {
		return new IntegerValue(pk)
	}

	@Override
	protected String getTableDdl() {
		return """CREATE TABLE IF NOT EXISTS ${tableName} (
${user_id} INTEGER GENERATED ALWAYS AS IDENTITY ( START WITH 1 INCREMENT BY 1 MAXVALUE 2147483647 MINVALUE -2147483648 CACHE 1000 ), 
${display_name} STRING, 
${email} STRING, 
${is_verified} BOOLEAN NOT NULL DEFAULT false, 
${roles} JSON, 
${password_hash} STRING, 
PRIMARY KEY(SHARD(${user_id}))
)
"""
	}

	@Override
	protected List<String> getTableIndicesDdl() {
		return ["CREATE INDEX IF NOT EXISTS email_index ON ${tableName}(${email})".toString(),
				"CREATE INDEX IF NOT EXISTS display_name_index ON ${tableName}(${display_name})".toString()]
	}

	@Override
	protected TableLimits getTableLimits() {
		return new TableLimits(25, 5, 1)
	}

}
