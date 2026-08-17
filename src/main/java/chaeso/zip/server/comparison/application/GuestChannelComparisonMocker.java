package chaeso.zip.server.comparison.application;

import chaeso.zip.server.comparison.application.dto.ChannelComparisonItemResponse;
import chaeso.zip.server.estimation.application.dto.CountRangeResponse;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 비로그인 채널 비교 응답용 MOCK 채널 상세정보와 적합도
 *
 * <p>실제 값 대신, 요청 순서대로 등급이 다른 MOCK 프로필을 매긴다.
 */
final class GuestChannelComparisonMocker {

  private static final List<MockProfile> PROFILES = List.of(
      new MockProfile("20~30대", List.of("배너", "네이티브"), List.of("키워드", "리타겟팅"), 92,
          new CountRangeResponse(40_000, 60_000), new CountRangeResponse(400, 600)),
      new MockProfile("30~40대", List.of("배너", "동영상"), List.of("관심사", "연령"), 81,
          new CountRangeResponse(25_000, 40_000), new CountRangeResponse(250, 400)),
      new MockProfile("전 연령", List.of("네이티브"), List.of("지역", "성별"), 68,
          new CountRangeResponse(10_000, 25_000), new CountRangeResponse(100, 250)));

  /** 목록 순서대로 {@link #PROFILES}를 매겨 MOCK 항목 목록을 만든다. */
  static List<ChannelComparisonItemResponse> mock(List<ChannelComparisonItemResponse> items) {
    return IntStream.range(0, items.size())
        .mapToObj(index -> {
          ChannelComparisonItemResponse item = items.get(index);
          MockProfile profile = PROFILES.get(index % PROFILES.size());
          return new ChannelComparisonItemResponse(
              item.channelId(),
              item.channelName(),
              item.previewImageUrl(),
              profile.audienceSummary(),
              profile.adFormats(),
              profile.targetingMethods(),
              item.minBudgetWon(),
              item.advantages(),
              item.tags(),
              item.cpcWon(),
              item.cpmWon(),
              profile.matchRate(),
              profile.estImpressions(),
              profile.estClicks());
        })
        .toList();
  }

  /** 등급별 MOCK 상세정보, 적합도, 예상 노출·클릭. */
  private record MockProfile(String audienceSummary, List<String> adFormats,
                              List<String> targetingMethods, int matchRate,
                              CountRangeResponse estImpressions, CountRangeResponse estClicks) {
  }
}
