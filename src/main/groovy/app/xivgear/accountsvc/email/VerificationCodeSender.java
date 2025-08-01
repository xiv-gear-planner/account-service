package app.xivgear.accountsvc.email;

public interface VerificationCodeSender {

	void sendVerificationCode(String email, String code);

	void sendResetCode(String email, String code);
}
