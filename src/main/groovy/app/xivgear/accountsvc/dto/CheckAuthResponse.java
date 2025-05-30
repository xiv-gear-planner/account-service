package app.xivgear.accountsvc.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Introspected
@Serdeable.Serializable
@Serdeable.Deserializable
public record CheckAuthResponse(int uid, String email, List<String> roles, boolean verified) {
}
