package com.shop.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private String phone;
    private Integer status;
    private LocalDateTime createdAt;
}
