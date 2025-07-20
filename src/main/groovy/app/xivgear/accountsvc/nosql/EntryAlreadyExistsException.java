package app.xivgear.accountsvc.nosql;

public class EntryAlreadyExistsException extends RuntimeException {
	public EntryAlreadyExistsException() {
	}

	public EntryAlreadyExistsException(String message) {
		super(message);
	}

	public EntryAlreadyExistsException(String message, Throwable cause) {
		super(message, cause);
	}

	public EntryAlreadyExistsException(Throwable cause) {
		super(cause);
	}

	public EntryAlreadyExistsException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}
