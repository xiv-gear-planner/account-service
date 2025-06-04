package app.xivgear.accountsvc.email

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Context
import io.micronaut.email.Email
import io.micronaut.email.EmailSender
import jakarta.inject.Singleton

@Singleton
@CompileStatic
@Context
class EmailVerificationCodeSender implements VerificationCodeSender {

	private final EmailSender<?, ?> emailSender

	EmailVerificationCodeSender(EmailSender<?, ?> emailSender) {
		this.emailSender = emailSender
	}

	@Override
	void sendVerificationCode(String email, String code) {
		emailSender.send(Email.builder().with {
			to email
			subject "Your Xivgear.app verification code is ${code}"
			body("""\
			Welcome to Xivgear.app

			Your verification code is ${code}. You can enter this code on the account management screen.

			Happy Gearing!
			""".stripIndent())
		})
	}
}
