package app.xivgear.accountsvc.nosql

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Property
import jakarta.inject.Singleton
import oracle.nosql.driver.NoSQLHandle
import oracle.nosql.driver.values.FieldValue
import oracle.nosql.driver.values.IntegerValue

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

	UsersTable(
			@Property(name = 'oracle-nosql.tables.users.name') String tableName,
			NoSQLHandle handle
	) {
		super(tableName, UserCol.user_id, handle)
	}

	@Override
	protected FieldValue pkToFieldValue(Integer pk) {
		return new IntegerValue(pk)
	}
}
