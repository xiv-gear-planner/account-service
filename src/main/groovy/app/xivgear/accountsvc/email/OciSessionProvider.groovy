package app.xivgear.accountsvc.email

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Property
import io.micronaut.core.annotation.NonNull
import io.micronaut.email.javamail.sender.MailPropertiesProvider
import io.micronaut.email.javamail.sender.SessionProvider
import jakarta.inject.Singleton
import jakarta.mail.Authenticator
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session

@Singleton
@CompileStatic
class OciSessionProvider implements SessionProvider {

	private final Properties properties;
	private final String user;
	private final String password;

	OciSessionProvider(MailPropertiesProvider provider,
					   @Property(name = "smtp.user") String user,
					   @Property(name = "smtp.password") String password) {
		this.properties = provider.mailProperties()
		this.user = user
		this.password = password
	}

	@Override
	@NonNull
	Session session() {
		return Session.getInstance(properties, new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(user, password)
			}
		});
	}

}
