package chaeso.zip.server.user.application.dto;

public record LoginCommand(String email, String rawPassword) {
}
