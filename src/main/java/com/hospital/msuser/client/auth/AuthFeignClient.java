package com.hospital.msuser.client.auth;

import com.hospital.msuser.config.FeignConfig;
import com.hospital.msuser.dto.auth.RegisterAuthRequestDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "ms-auth",
        configuration = FeignConfig.class
)
public interface AuthFeignClient {

    @PostMapping("/api/auth/register")
    void registerUserCredentials(@RequestBody RegisterAuthRequestDTO dto);
}
