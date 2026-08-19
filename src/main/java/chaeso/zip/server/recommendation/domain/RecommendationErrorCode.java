package chaeso.zip.server.recommendation.domain;

import chaeso.zip.server.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RecommendationErrorCode implements ErrorCode {

  RECOMMENDATION_NOT_FOUND(HttpStatus.NOT_FOUND, "REC-001", "존재하지 않는 추천입니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
