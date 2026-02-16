package com.movie.backend.service;

import com.movie.backend.dto.LoginDTO;
import com.movie.backend.dto.PublicUserVO;
import com.movie.backend.dto.RegisterDTO;
import com.movie.backend.dto.UpdateUserProfileDTO;
import com.movie.backend.dto.UserProfileVO;
import com.movie.backend.dto.UserVO;

public interface UserService {
    UserVO login(LoginDTO loginDTO);
    void register(RegisterDTO registerDTO);
    void updateAvatar(String userId, String avatarUrl);
    PublicUserVO getPublicUserInfo(String userId);
    
    /**
     * 获取用户的公开信息（包含统计数据）
     */
    PublicUserVO getPublicUserInfoWithStats(String userId);
    
    /**
     * 获取当前登录用户的完整信息（包含统计数据）
     */
    UserVO getCurrentUserInfoWithStats(String userId);
    
    /**
     * 修改密码（会递增 passwordVersion，使所有旧 Token 失效）
     */
    void changePassword(String userId, String oldPassword, String newPassword);

    /**
     * 注销账户（逻辑删除）
     */
    void cancelAccount(String userId);

    /**
     * 获取当前用户可编辑的个人资料
     */
    UserProfileVO getMyProfile(String userId);

    /**
     * 更新当前用户个人资料（增量更新）
     */
    void updateMyProfile(String userId, UpdateUserProfileDTO updateDTO);
}
