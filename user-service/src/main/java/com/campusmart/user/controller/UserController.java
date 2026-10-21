package com.campusmart.user.controller;

import com.campusmart.common.constant.AuthConstants;
import com.campusmart.common.exception.BizException;
import com.campusmart.common.result.Result;
import com.campusmart.common.result.ResultCode;
import com.campusmart.user.dto.LoginDTO;
import com.campusmart.user.dto.LoginVO;
import com.campusmart.user.dto.RegisterDTO;
import com.campusmart.user.dto.UserVO;
import com.campusmart.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口。网关未做 StripPrefix，因此下游路径与网关一致（/user/**）。
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.success("注册成功", userService.register(dto));
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success("登录成功", userService.login(dto));
    }

    @GetMapping("/info")
    public Result<UserVO> info(@RequestHeader(value = AuthConstants.HEADER_USER_ID, required = false) Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return Result.success(userService.getById(userId));
    }
}
