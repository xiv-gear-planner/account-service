package app.xivgear.accountsvc

import io.micronaut.core.annotation.NonNull
import io.micronaut.http.HttpRequest
import io.micronaut.http.cookie.Cookie

interface SessionTokenStore<X> {

	/**
	 * Validates the session token and returns the associated username or user ID.
	 *
	 * @param token The session token (e.g., from a cookie)
	 * @return Optional containing the user identity if the token is valid
	 */
	@NonNull
	Optional<X> validateSessionToken(@NonNull String token);

	/**
	 * Creates and stores a new session token for the given user.
	 *
	 * @param username The user identity
	 * @return The new session token to be stored in a cookie
	 */
	@NonNull
	String createSessionToken(@NonNull X user);

	/**
	 * Invalidates a session token (e.g., for logout).
	 *
	 * @param token The session token to invalidate
	 */
	void invalidateSessionToken(@NonNull String token);

	/**
	 * Optional hook to extract the session token from a request (e.g., cookie lookup).
	 * You can implement this or handle it separately via filters.
	 */
	default Optional<String> extractTokenFromRequest(@NonNull HttpRequest<?> request) {
		return request.getCookies()
				.findCookie("SESSION")
				.map(Cookie::getValue)
				.filter(val -> !val.empty)
	}
}