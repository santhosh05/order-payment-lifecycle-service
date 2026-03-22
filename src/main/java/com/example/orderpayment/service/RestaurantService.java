package com.example.orderpayment.service;

import com.example.orderpayment.dto.CreateRestaurantRequest;
import com.example.orderpayment.dto.RestaurantResponse;
import com.example.orderpayment.entity.RestaurantEntity;
import com.example.orderpayment.exception.ApiException;
import com.example.orderpayment.repository.RestaurantRepository;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestaurantService {
    private final RestaurantRepository restaurantRepository;

    public RestaurantService(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    @Transactional
    public RestaurantResponse createRestaurant(CreateRestaurantRequest request) {
        validateTimezone(request.timezone());
        String normalizedCurrency = normalizeCurrency(request.defaultCurrency());

        RestaurantEntity restaurant = RestaurantEntity.create(
                request.restaurantId(),
                request.name(),
                request.timezone(),
                normalizedCurrency);
        restaurantRepository.save(restaurant);
        return mapToRestaurantResponse(restaurant);
    }

    @Transactional(readOnly = true)
    public RestaurantResponse getRestaurant(String restaurantId) {
        return mapToRestaurantResponse(findRestaurantByIdOrThrow(restaurantId));
    }

    public RestaurantEntity findRestaurantByIdOrThrow(String restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Restaurant not found"));
    }

    private RestaurantResponse mapToRestaurantResponse(RestaurantEntity restaurant) {
        return new RestaurantResponse(
                restaurant.getRestaurantId(),
                restaurant.getName(),
                restaurant.getTimezone(),
                restaurant.getDefaultCurrency());
    }

    private void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid restaurant timezone");
        }
    }

    private String normalizeCurrency(String currency) {
        String normalizedCurrency = currency.toUpperCase(Locale.ROOT);
        if (normalizedCurrency.length() != 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Currency must be a 3-letter ISO code");
        }
        return normalizedCurrency;
    }
}
