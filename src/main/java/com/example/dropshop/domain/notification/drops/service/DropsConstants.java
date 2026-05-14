package com.example.dropshop.domain.notification.drops.service;

import com.example.dropshop.domain.notification.drops.enums.DropsStatus;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** 드랍 도메인에서 공통으로 사용하는 상태 집합 상수. */
public final class DropsConstants {

  public static final Set<DropsStatus> ONGOING_DROP_STATUSES =
      Collections.unmodifiableSet(EnumSet.of(DropsStatus.SCHEDULED, DropsStatus.ACTIVE));

  public static final Set<DropsStatus> NON_DELETABLE_DROP_STATUSES =
      Collections.unmodifiableSet(EnumSet.allOf(DropsStatus.class));

  public static final Set<DropsStatus> PUBLIC_VISIBLE_STATUSES = NON_DELETABLE_DROP_STATUSES;

  private DropsConstants() {}
}
