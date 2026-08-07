alter table channel_recommendations
    add constraint uq_channel_recommendation_onboarding_rank unique (onboarding_id, rank);
