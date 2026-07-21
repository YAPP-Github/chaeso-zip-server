package chaeso.zip.server.auth.application.dto;

import chaeso.zip.server.auth.domain.AuthProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 로그인 수단 조회 응답 DTO.
 */
@Schema(description = "로그인 수단 조회 응답")
public record LoginMethodsResponse(
    @Schema(description = "이 계정으로 로그인할 수 있는 방법. 비어 있으면 미가입",
        example = "[\"LOCAL\", \"GOOGLE\"]",
        requiredMode = Schema.RequiredMode.REQUIRED)
    List<AuthProvider> methods) {
}
