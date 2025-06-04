package app.xivgear.accountsvc.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
public class ValidationErrorResponse {
	public Map<String, String> errors;
}
