package com.hospital.msuser.dto.response;

import com.hospital.msuser.entity.enums.DocumentType;
import com.hospital.msuser.entity.enums.UserRole;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {

    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private DocumentType documentType;
    private String documentNumber;
    private UserRole role;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
