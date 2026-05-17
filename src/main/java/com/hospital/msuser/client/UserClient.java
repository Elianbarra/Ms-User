package com.hospital.msuser.client;

import com.hospital.msuser.client.auth.AuthFeignClient;
import com.hospital.msuser.dto.auth.RegisterAuthRequestDTO;
import com.hospital.msuser.dto.request.CreateUserRequestDTO;
import com.hospital.msuser.dto.request.UpdateUserRequestDTO;
import com.hospital.msuser.dto.response.UserResponseDTO;
import com.hospital.msuser.entity.User;
import com.hospital.msuser.exception.UserAlreadyExistsException;
import com.hospital.msuser.exception.UserNotFoundException;
import com.hospital.msuser.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.UUID;

/*
 * Patron Facade: oculta la complejidad de coordinar el repositorio, el mapeo de DTOs
 * y la comunicacion con MS-AUTH detras de una interfaz simple para el Controller.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserClient {

    private final UserRepository userRepository;
    private final AuthFeignClient authFeignClient;

    public UserResponseDTO registerUser(CreateUserRequestDTO dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new UserAlreadyExistsException("El email ya esta registrado: " + dto.getEmail());
        }
        if (userRepository.existsByDocumentNumber(dto.getDocumentNumber())) {
            throw new UserAlreadyExistsException("El documento ya esta registrado: " + dto.getDocumentNumber());
        }

        User user = User.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .documentType(dto.getDocumentType())
                .documentNumber(dto.getDocumentNumber())
                .role(dto.getRole())
                .build();

        User saved = userRepository.save(user);
        log.info("Usuario creado con id: {}", saved.getId());

        RegisterAuthRequestDTO authRequest = RegisterAuthRequestDTO.builder()
                .email(dto.getEmail())
                .password(dto.getPassword())
                .role(dto.getRole().name())
                .userId(saved.getId())
                .build();

        authFeignClient.registerUserCredentials(authRequest);
        log.info("Credenciales enviadas a MS-AUTH para: {}", dto.getEmail());

        return toResponse(saved);
    }

    public UserResponseDTO getUserById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    public List<UserResponseDTO> getAllActiveUsers() {
        return userRepository.findByIsActive(true)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public UserResponseDTO updateUser(UUID id, UpdateUserRequestDTO dto) {
        User user = findOrThrow(id);

        if (dto.getFirstName() != null)    user.setFirstName(dto.getFirstName());
        if (dto.getLastName() != null)     user.setLastName(dto.getLastName());
        if (dto.getPhone() != null)        user.setPhone(dto.getPhone());
        if (dto.getDocumentType() != null) user.setDocumentType(dto.getDocumentType());
        if (dto.getDocumentNumber() != null) user.setDocumentNumber(dto.getDocumentNumber());
        if (dto.getRole() != null)         user.setRole(dto.getRole());

        return toResponse(userRepository.save(user));
    }

    public void deactivateUser(UUID id) {
        User user = findOrThrow(id);
        user.setIsActive(false);
        userRepository.save(user);
        log.info("Usuario desactivado: {}", id);
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado con id: " + id));
    }

    private UserResponseDTO toResponse(User user) {
        return UserResponseDTO.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .documentType(user.getDocumentType())
                .documentNumber(user.getDocumentNumber())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
