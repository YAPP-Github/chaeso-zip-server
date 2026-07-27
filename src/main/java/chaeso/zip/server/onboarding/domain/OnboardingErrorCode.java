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
  TOO_MANY_AD_HISTORY(HttpStatus.BAD_REQUEST, "ONB-004", "집행 내역은 최대 50건까지 입력할 수 있습니다."),
  INVALID_AD_PERIOD(HttpStatus.BAD_REQUEST, "ONB-005", "집행 종료일은 시작일보다 빠를 수 없습니다."),
  CONCURRENT_SUBMISSION(HttpStatus.CONFLICT, "ONB-006", "동시에 제출된 요청이 있어 처리할 수 없습니다. 다시 시도해주세요."),
  ONBOARDING_NOT_FOUND(HttpStatus.NOT_FOUND, "ONB-007", "온보딩 정보가 없습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
