package chaeso.zip.server.channel.domain.repository;

import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.entity.QChannel;
import chaeso.zip.server.common.exception.BusinessException;
import chaeso.zip.server.common.exception.CommonErrorCode;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class ChannelRepositoryImpl implements ChannelRepositoryCustom {

  private static final Set<String> SORTABLE_FIELDS = Set.of("name", "createdAt");

  private final JPAQueryFactory queryFactory;

  @Override
  public Page<Channel> searchActiveChannels(String name, Pageable pageable) {
    QChannel channel = QChannel.channel;
    BooleanExpression activeOnly = channel.active.isTrue();
    BooleanExpression nameMatch = nameContainsIgnoreCase(channel, name);

    List<Channel> content = queryFactory
        .selectFrom(channel)
        .where(activeOnly, nameMatch)
        .orderBy(toOrderSpecifiers(pageable.getSort()))
        .offset(pageable.getOffset())
        .limit(pageable.getPageSize())
        .fetch();

    JPAQuery<Long> countQuery = queryFactory
        .select(channel.count())
        .from(channel)
        .where(activeOnly, nameMatch);

    return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
  }

  private BooleanExpression nameContainsIgnoreCase(QChannel channel, String name) {
    return StringUtils.hasText(name) ? channel.name.containsIgnoreCase(name.trim()) : null;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private OrderSpecifier<?>[] toOrderSpecifiers(Sort sort) {
    PathBuilder<Channel> entityPath = new PathBuilder<>(Channel.class, "channel");
    List<OrderSpecifier<?>> orders = new ArrayList<>();
    for (Sort.Order order : sort) {
      String property = order.getProperty();
      if (!SORTABLE_FIELDS.contains(property)) {
        throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE,
            "정렬할 수 없는 필드입니다: " + property);
      }
      orders.add(new OrderSpecifier(
          order.isAscending() ? Order.ASC : Order.DESC,
          entityPath.get(property)));
    }
    return orders.toArray(new OrderSpecifier[0]);
  }
}
