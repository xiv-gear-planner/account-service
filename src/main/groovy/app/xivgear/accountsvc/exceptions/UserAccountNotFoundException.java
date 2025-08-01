package app.xivgear.accountsvc.exceptions;

public class UserAccountNotFoundException extends RuntimeException {
	public UserAccountNotFoundException() {
	}

	public UserAccountNotFoundException(String message) {
		super(message);
	}

	public UserAccountNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

	public UserAccountNotFoundException(Throwable cause) {
		super(cause);
	}

	public UserAccountNotFoundException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}
