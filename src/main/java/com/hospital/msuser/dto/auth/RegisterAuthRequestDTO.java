package com.hospital.msuser.dto.auth;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterAuthRequestDTO {

    private String email;
    private String password;
    private String role;
    private UUID userId;
}
