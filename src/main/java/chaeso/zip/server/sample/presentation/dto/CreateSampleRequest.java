package chaeso.zip.server.sample.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 샘플 생성 요청 DTO. 요청 검증은 표현 계층 DTO 에서 수행한다.
 */
public record CreateSampleRequest(
    @NotBlank(message = "이름은 필수입니다.")
    @Size(max = 100, message = "이름은 100자를 초과할 수 없습니다.")
    String name) {

}
