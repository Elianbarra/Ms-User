package com.hospital.msuser.dto.request;

import com.hospital.msuser.entity.enums.DocumentType;
import com.hospital.msuser.entity.enums.UserRole;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequestDTO {

    private String firstName;
    private String lastName;
    private String phone;
    private DocumentType documentType;
    private String documentNumber;
    private UserRole role;
}
