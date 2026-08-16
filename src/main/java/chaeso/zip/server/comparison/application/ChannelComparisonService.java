package chaeso.zip.server.comparison.application;

import chaeso.zip.server.comparison.application.dto.ChannelComparisonResponse;
import chaeso.zip.server.comparison.application.dto.SavedChannelComparisonResponse;
import java.util.List;
import java.util.UUID;

/**
 * 선택한 채널을 카탈로그 정보와 온보딩 조건으로 비교한다.
 */
public interface ChannelComparisonService {

  /**
   * 선택한 채널을 비교한다.
   *
   * 온보딩 X: 대표 단가와 기본 태그를 반환. 비로그인은 카탈로그 상세 노출 X.
   * 온보딩 O: 맞춤 태그와 단가를 계산하고, 로그인한 요청에만 적합도와 예상 노출/클릭 수를 넣어 적합도순 반환
   *
   * @param channelIds   비교할 채널 식별자 목록
   * @param onboardingId 맞춤 지표 계산에 사용할 온보딩 식별자. 없으면 기본 정보를 반환한다.
   * @param requesterId  요청자 식별자. 비로그인 요청이면 {@code null}
   * @return 채널 비교 결과
   */
  ChannelComparisonResponse compare(List<UUID> channelIds, UUID onboardingId, UUID requesterId);

  /**
   * 비교 결과를 비교 시점 값 그대로 저장한다. 저장 횟수는 제한이 없다.
   *
   * @param userId       저장하는 사용자
   * @param channelIds   비교할 채널 식별자 목록 (2~3개)
   * @param onboardingId 비교 근거가 된 온보딩 (선택). 없으면 서비스명 추가 입력
   * @param serviceName  onboardingId가 없을 때만 쓰는 서비스명
   * @return 저장된 채널 비교 결과
   */
  SavedChannelComparisonResponse save(UUID userId, List<UUID> channelIds, UUID onboardingId, String serviceName);

  /**
   * 저장된 채널 비교를 조회한다. 본인이 저장한 것이 아니면 존재하지 않는 것과 동일하게 404로 응답한다.
   *
   * @param userId        조회하는 사용자
   * @param comparisonId  조회할 채널 비교 식별자
   * @return 저장 시점 스냅샷 그대로의 채널 비교 결과
   */
  SavedChannelComparisonResponse findComparison(UUID userId, UUID comparisonId);
}
