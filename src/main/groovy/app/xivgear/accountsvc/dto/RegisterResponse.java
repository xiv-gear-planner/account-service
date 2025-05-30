package app.xivgear.accountsvc.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Introspected
@Serdeable.Serializable
@Serdeable.Deserializable
public record RegisterResponse(int uid) {
}
