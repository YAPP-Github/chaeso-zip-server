package chaeso.zip.server.recommendation.domain;

import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 광고 목적 사이의 인접 관계
 */
public final class ObjectiveAffinity {

  private static final Map<CampaignObjective, Set<CampaignObjective>> ADJACENT =
      adjacencyTable();

  private ObjectiveAffinity() {
  }

  /**
   * 두 목적이 서로를 부분적으로 대신할 수 있는지
   */
  public static boolean isAdjacent(CampaignObjective objective, CampaignObjective other) {
    if (objective == null || other == null || objective == other) {
      return false;
    }
    return ADJACENT.getOrDefault(objective, Set.of()).contains(other);
  }

  /**
   * 퍼널 단계별 인접 목적
   */
  private static Map<CampaignObjective, Set<CampaignObjective>> adjacencyTable() {
    Map<CampaignObjective, Set<CampaignObjective>> table =
        new EnumMap<>(CampaignObjective.class);
    link(table, CampaignObjective.AWARENESS, CampaignObjective.VIDEO_VIEW);
    link(table, CampaignObjective.VIDEO_VIEW, CampaignObjective.TRAFFIC);
    link(table, CampaignObjective.TRAFFIC, CampaignObjective.LEAD);
    link(table, CampaignObjective.TRAFFIC, CampaignObjective.CONVERSION);
    link(table, CampaignObjective.LEAD, CampaignObjective.CONVERSION);
    link(table, CampaignObjective.APP_INSTALL, CampaignObjective.IN_APP_ACTION);
    link(table, CampaignObjective.APP_INSTALL, CampaignObjective.CONVERSION);
    link(table, CampaignObjective.IN_APP_ACTION, CampaignObjective.CONVERSION);
    return table;
  }

  private static void link(Map<CampaignObjective, Set<CampaignObjective>> table,
      CampaignObjective one, CampaignObjective other) {
    table.computeIfAbsent(one, key -> EnumSet.noneOf(CampaignObjective.class))
        .add(other);
    table.computeIfAbsent(other, key -> EnumSet.noneOf(CampaignObjective.class))
        .add(one);
  }
}
