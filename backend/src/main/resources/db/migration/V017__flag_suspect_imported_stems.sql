update questions
set review_status = 'NEEDS_REVIEW',
    ability_tags = case
        when '超长题干' = any(ability_tags) then ability_tags
        else ability_tags || array['超长题干']
    end
where deleted_at is null and length(stem) > 5000;

update questions
set review_status = 'NEEDS_REVIEW',
    ability_tags = case
        when '解析混入题干' = any(ability_tags) then ability_tags
        else ability_tags || array['解析混入题干']
    end
where deleted_at is null
  and (stem like '%【解答】%' or stem like '%【解析】%' or stem like '%故选%');
