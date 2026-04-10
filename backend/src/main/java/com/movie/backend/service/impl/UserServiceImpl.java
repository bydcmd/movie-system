package com.movie.backend.service.impl;

import com.movie.backend.common.UserStatus;
import com.movie.backend.dto.LoginDTO;
import com.movie.backend.dto.PublicUserVO;
import com.movie.backend.dto.RegisterDTO;
import com.movie.backend.dto.UpdateUserProfileDTO;
import com.movie.backend.dto.UserProfileVO;
import com.movie.backend.dto.UserVO;
import com.movie.backend.entity.User;
import com.movie.backend.mapper.CommentMapper;
import com.movie.backend.mapper.UserMapper;
import com.movie.backend.mapper.WatchedMapper;
import com.movie.backend.messaging.event.UserLoginEvent;
import com.movie.backend.messaging.event.UserRegisterEvent;
import com.movie.backend.messaging.outbox.OutboxPublisher;
import com.movie.backend.service.UserService;
import com.movie.backend.utils.JwtUtil;
import com.movie.backend.utils.PasswordUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private WatchedMapper watchedMapper;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Override
    public UserVO login(LoginDTO loginDTO) {
        User user = userMapper.selectById(loginDTO.getId());
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        // 检查用户状态
        if (UserStatus.isFrozen(user.getStatus())) {
            throw new IllegalArgumentException("账号已被禁用，请联系管理员");
        }
        if (UserStatus.isCancelled(user.getStatus())) {
            throw new IllegalArgumentException("该账号已注销，无法登录");
        }

        // 使用BCrypt验证密码
        if (!PasswordUtil.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("密码错误");
        }

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        
        // 确保 passwordVersion 有默认值
        Integer passwordVersion = user.getPasswordVersion() != null ? user.getPasswordVersion() : 1;
        
        // 生成 Access Token (短效) 和 Refresh Token (长效)
        userVO.setAccessToken(JwtUtil.generateAccessToken(user.getId(), user.getNickname(), user.getRole(), passwordVersion));
        userVO.setRefreshToken(JwtUtil.generateRefreshToken(user.getId(), user.getNickname(), user.getRole(), passwordVersion));

        UserLoginEvent event = new UserLoginEvent(user.getId(), null);
        outboxPublisher.publishUserLoginEvent(event);

        return userVO;
    }

    @Override
    public void register(RegisterDTO registerDTO) {
        User existing = userMapper.selectById(registerDTO.getId());
        if (existing != null) {
            throw new RuntimeException("User ID already exists");
        }

        User user = new User();
        BeanUtils.copyProperties(registerDTO, user);
        // 使用BCrypt加密密码
        user.setPassword(PasswordUtil.encode(registerDTO.getPassword()));
        user.setRole(1); // Default is 1 (User), 0 is Admin
        user.setStatus(UserStatus.ACTIVE); // 默认状态为正常
        user.setPasswordVersion(1); // 初始密码版本为 1
        user.setCreateTime(new Date());
        user.setUpdateTime(new Date());

        userMapper.insert(user);

        UserRegisterEvent event = new UserRegisterEvent(user.getId(), null);
        outboxPublisher.publishUserRegisterEvent(event);
    }

    @Override
    public void updateAvatar(String userId, String avatarUrl) {
        User user = new User();
        user.setId(userId);
        user.setAvatar(avatarUrl);
        user.setUpdateTime(new Date());
        userMapper.update(user);
    }

    @Override
    public PublicUserVO getPublicUserInfo(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null || UserStatus.isCancelled(user.getStatus())) {
            return null;
        }
        
        // 将用户信息转换为公共信息
        PublicUserVO publicUserVO = new PublicUserVO();
        publicUserVO.setId(user.getId());
        publicUserVO.setNickname(user.getNickname());
        publicUserVO.setAvatar(user.getAvatar());
        publicUserVO.setUrl(user.getUrl());
        
        return publicUserVO;
    }

    @Override
    public PublicUserVO getPublicUserInfoWithStats(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null || UserStatus.isCancelled(user.getStatus())) {
            return null;
        }
        
        // 将用户信息转换为公共信息
        PublicUserVO publicUserVO = new PublicUserVO();
        publicUserVO.setId(user.getId());
        publicUserVO.setNickname(user.getNickname());
        publicUserVO.setAvatar(user.getAvatar());
        publicUserVO.setUrl(user.getUrl());
        
        // 获取用户的统计数据
        Integer receivedLikes = commentMapper.getTotalReceivedLikes(userId);
        Integer commentCount = commentMapper.getCommentCount(userId);
        Integer watchedCount = watchedMapper.countByUserId(userId);
        
        // 设置统计数据
        publicUserVO.setReceivedLikes(receivedLikes != null ? receivedLikes : 0);
        publicUserVO.setCommentCount(commentCount != null ? commentCount : 0);
        
        return publicUserVO;
    }

    @Override
    public UserVO getCurrentUserInfoWithStats(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        
        // 将用户信息转换为完整用户信息
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        
        // 获取用户的统计数据
        Integer receivedLikes = commentMapper.getTotalReceivedLikes(userId);
        Integer commentCount = commentMapper.getCommentCount(userId);
        Integer watchedCount = watchedMapper.countByUserId(userId);
        
        // 设置统计数据
        userVO.setReceivedLikes(receivedLikes != null ? receivedLikes : 0);
        userVO.setCommentCount(commentCount != null ? commentCount : 0);
        userVO.setWatchedCount(watchedCount != null ? watchedCount : 0);
        
        return userVO;
    }

    @Override
    public void changePassword(String userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        // 验证旧密码
        if (!PasswordUtil.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("旧密码错误");
        }

        // 更新密码和版本号
        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setPassword(PasswordUtil.encode(newPassword));

        // 递增密码版本号，使所有旧 Token 失效
        Integer currentVersion = user.getPasswordVersion() != null ? user.getPasswordVersion() : 1;
        updateUser.setPasswordVersion(currentVersion + 1);
        updateUser.setUpdateTime(new Date());

        userMapper.update(updateUser);
    }

    @Override
    public void cancelAccount(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        User updateUser = new User();
        updateUser.setId(userId);
        // 设置状态为 2 (已注销)
        updateUser.setStatus(UserStatus.CANCELLED);
        updateUser.setUpdateTime(new Date());

        // 【可选策略】如果希望注销后在评论区隐藏真实身份，可以取消下面两行的注释
         updateUser.setNickname("已注销用户");
        // updateUser.setAvatar("http://localhost:8080/images/default_avatar.png");

        userMapper.update(updateUser);
    }

    @Override
    public UserProfileVO getMyProfile(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }

        UserProfileVO profileVO = new UserProfileVO();
        profileVO.setId(user.getId());
        profileVO.setNickname(user.getNickname());
        profileVO.setAvatar(user.getAvatar());
        profileVO.setUrl(user.getUrl());
        profileVO.setEmail(user.getEmail());
        return profileVO;
    }

    @Override
    public void updateMyProfile(String userId, UpdateUserProfileDTO updateDTO) {
        User existing = userMapper.selectById(userId);
        if (existing == null) {
            throw new RuntimeException("用户不存在");
        }
        if (updateDTO == null) {
            throw new IllegalArgumentException("更新内容不能为空");
        }

        String nickname = normalizeText(updateDTO.getNickname());
        String avatar = normalizeText(updateDTO.getAvatar());
        String url = normalizeText(updateDTO.getUrl());
        String email = normalizeText(updateDTO.getEmail());

        if (nickname != null && nickname.length() > 50) {
            throw new IllegalArgumentException("昵称长度不能超过50个字符");
        }
        if (email != null) {
            User emailOwner = userMapper.selectByEmail(email);
            if (emailOwner != null && !userId.equals(emailOwner.getId())) {
                throw new IllegalArgumentException("邮箱已被占用");
            }
        }

        User updateUser = new User();
        updateUser.setId(userId);
        boolean hasChanges = false;

        if (nickname != null) {
            updateUser.setNickname(nickname);
            hasChanges = true;
        }
        if (avatar != null) {
            updateUser.setAvatar(avatar);
            hasChanges = true;
        }
        if (url != null) {
            updateUser.setUrl(url);
            hasChanges = true;
        }
        if (email != null) {
            updateUser.setEmail(email);
            hasChanges = true;
        }

        if (!hasChanges) {
            throw new IllegalArgumentException("至少需要提供一个可更新字段");
        }

        updateUser.setUpdateTime(new Date());
        userMapper.update(updateUser);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
