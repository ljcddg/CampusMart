package com.campusmart.user.service;

import com.campusmart.user.dto.LoginDTO;
import com.campusmart.user.dto.LoginVO;
import com.campusmart.user.dto.RegisterDTO;
import com.campusmart.user.dto.UserVO;

public interface UserService {

    Long register(RegisterDTO dto);

    LoginVO login(LoginDTO dto);

    UserVO getById(Long userId);
}
