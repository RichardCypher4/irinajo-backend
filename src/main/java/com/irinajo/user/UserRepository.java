package com.irinajo.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByPhone(String phone);
    Optional<User> findByGoogleSub(String googleSub);
    Optional<User> findByAppleSub(String appleSub);
}
