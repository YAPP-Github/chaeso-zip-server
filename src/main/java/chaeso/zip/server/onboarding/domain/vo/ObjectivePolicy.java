package chaeso.zip.server.onboarding.domain.vo;

import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 서비스 형태별로 선택 가능한 광고 목표를 정의한다.
 */
public final class ObjectivePolicy {

  private static final Set<CampaignObjective> COMMON = Collections.unmodifiableSet(EnumSet.of(
      CampaignObjective.AWARENESS,
      CampaignObjective.VIDEO_VIEW,
      CampaignObjective.TRAFFIC,
      CampaignObjective.LEAD,
      CampaignObjective.CONVERSION));

  private static final Set<CampaignObjective> ALL =
      Collections.unmodifiableSet(EnumSet.allOf(CampaignObjective.class));

  private static final Map<ServiceType, Set<CampaignObjective>> ALLOWED = Map.of(
      ServiceType.WEB, COMMON,
      ServiceType.MOBILE_APP, ALL,
      ServiceType.WEB_AND_APP, ALL,
      ServiceType.OTHER, ALL);

  private ObjectivePolicy() {
  }

  public static Set<CampaignObjective> allowedFor(ServiceType serviceType) {
    return ALLOWED.getOrDefault(serviceType, COMMON);
  }

  public static boolean allows(ServiceType serviceType, CampaignObjective objective) {
    return allowedFor(serviceType).contains(objective);
  }
}
