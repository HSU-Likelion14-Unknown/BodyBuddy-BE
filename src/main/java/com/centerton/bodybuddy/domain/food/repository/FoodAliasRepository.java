package com.centerton.bodybuddy.domain.food.repository;

import com.centerton.bodybuddy.domain.food.entity.FoodAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FoodAliasRepository extends JpaRepository<FoodAlias, String> {

    Optional<FoodAlias> findByNormalizedAliasAndFoodActiveTrue(String normalizedAlias);
}
