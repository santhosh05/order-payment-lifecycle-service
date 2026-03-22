package com.example.orderpayment.repository;

import com.example.orderpayment.entity.RestaurantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantRepository extends JpaRepository<RestaurantEntity, String> {}
