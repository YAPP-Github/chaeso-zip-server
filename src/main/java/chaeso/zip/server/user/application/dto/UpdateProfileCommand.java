package chaeso.zip.server.user.application.dto;

import chaeso.zip.server.user.domain.Occupation;

public record UpdateProfileCommand(String companyName, Occupation occupation) {
}
