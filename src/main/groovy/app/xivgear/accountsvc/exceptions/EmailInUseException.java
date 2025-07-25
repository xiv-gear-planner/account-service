package app.xivgear.accountsvc.exceptions;

public class EmailInUseException extends RuntimeException {
	public EmailInUseException() {
	}

	public EmailInUseException(String message) {
		super(message);
	}

	public EmailInUseException(String message, Throwable cause) {
		super(message, cause);
	}

	public EmailInUseException(Throwable cause) {
		super(cause);
	}

	public EmailInUseException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}
