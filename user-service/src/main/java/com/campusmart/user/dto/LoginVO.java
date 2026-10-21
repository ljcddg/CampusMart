package com.campusmart.user.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginVO {

    private String token;

    private Long userId;

    private String username;

    private String nickname;

    /** token 有效期（秒） */
    private Long expiresIn;
}
