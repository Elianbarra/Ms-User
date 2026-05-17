package com.hospital.msuser.repository;

import com.hospital.msuser.entity.User;
import com.hospital.msuser.entity.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Patron Repository: abstrae el acceso a datos desacoplando la persistencia del resto de la logica
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    boolean existsByDocumentNumber(String documentNumber);

    Optional<User> findByEmail(String email);

    List<User> findByIsActive(Boolean isActive);

    List<User> findByRole(UserRole role);
}
