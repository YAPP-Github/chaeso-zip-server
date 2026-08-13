package chaeso.zip.server.onboarding.domain;

import chaeso.zip.server.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 온보딩 도메인 전용 에러 코드.
 */
@Getter
@RequiredArgsConstructor
public enum OnboardingErrorCode implements ErrorCode {

  INVALID_BUDGET_RANGE(HttpStatus.BAD_REQUEST, "ONB-001", "최소 예산은 최대 예산보다 클 수 없습니다."),
  OBJECTIVE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "ONB-002", "선택한 서비스 형태에서 사용할 수 없는 광고 목표입니다."),
  AD_EXPERIENCE_MISMATCH(HttpStatus.BAD_REQUEST, "ONB-003", "집행 경험 여부와 입력한 집행 내역이 일치하지 않습니다."),
  CONCURRENT_SUBMISSION(HttpStatus.CONFLICT, "ONB-006", "동시에 제출된 요청이 있어 처리할 수 없습니다. 다시 시도해주세요."),
  ONBOARDING_NOT_FOUND(HttpStatus.NOT_FOUND, "ONB-007", "온보딩 정보가 없습니다."),
  PERFORMANCE_FILE_INVALID(HttpStatus.BAD_REQUEST, "ONB-008", "첨부한 성과파일을 확인할 수 없습니다."),
  TOO_FEW_MANUAL_FIELDS(HttpStatus.BAD_REQUEST, "ONB-010", "직접 입력한 집행 내역은 예산/집행기간/노출수/클릭수/전환수 중 2개 이상을 입력해야 합니다."),
  INVALID_AGE_BAND_SELECTION(HttpStatus.BAD_REQUEST, "ONB-011", "잘 모르겠어요는 다른 연령대와 함께 선택할 수 없습니다."),
  PERIOD_REQUIRED(HttpStatus.BAD_REQUEST, "ONB-012", "집행 기간은 필수입니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
