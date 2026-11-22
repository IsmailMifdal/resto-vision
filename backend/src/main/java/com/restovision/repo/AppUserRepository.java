package com.restovision.repo;

import com.restovision.domain.AppUser;
import com.restovision.domain.Restaurant;
import com.restovision.domain.Role;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmail(String email);

    List<AppUser> findByRestaurantAndRoleIn(Restaurant restaurant, Collection<Role> roles);
}
