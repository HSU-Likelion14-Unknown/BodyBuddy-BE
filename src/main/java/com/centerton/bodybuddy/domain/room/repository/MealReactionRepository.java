package com.centerton.bodybuddy.domain.room.repository;

import com.centerton.bodybuddy.domain.room.entity.MealReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MealReactionRepository extends JpaRepository<MealReaction, String> {

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from MealReaction reaction
            where reaction.meal.mealId = :mealId
              and reaction.user.userId = :userId
            """)
    void deleteAllByMealIdAndUserId(
            @Param("mealId") String mealId,
            @Param("userId") String userId
    );

    List<MealReaction>
    findAllByMealMealIdAndUserUserIdOrderByEmojiTypeAsc(
            String mealId,
            String userId
    );

    @Query("""
            select
                reaction.emojiType,
                count(reaction)
            from MealReaction reaction
            where reaction.meal.mealId = :mealId
            group by reaction.emojiType
            order by reaction.emojiType
            """)
    List<Object[]> countByMealIdGroupByEmojiType(
            @Param("mealId") String mealId
    );
}