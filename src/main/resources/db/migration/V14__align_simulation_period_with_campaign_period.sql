-- 시뮬레이터 기간을 온보딩과 같은 CampaignPeriod 로 통일한다.
-- period 는 enum 이름을 담는 varchar 라 컬럼 정의는 그대로 두고 저장된 값만 옮긴다.
update budget_simulations
set period = case period
                 when 'W1' then 'LE_1W'
                 when 'W2' then 'W2_3'
                 when 'M3' then 'GE_3M'
                 else period
                 end
where period in ('W1', 'W2', 'M3');
