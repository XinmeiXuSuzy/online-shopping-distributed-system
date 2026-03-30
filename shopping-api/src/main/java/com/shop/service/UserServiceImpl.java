package com.shop.service;

import com.shop.domain.User;
import com.shop.dto.request.CreateUserRequest;
import com.shop.exception.ResourceNotFoundException;
import com.shop.mapper.UserMapper;
import com.shop.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final SnowflakeIdGenerator idGenerator;
    // BCrypt is CPU-heavy by design; instantiated directly to avoid a Spring Security dep
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public User createUser(CreateUserRequest request) {
        User user = User.builder()
                .id(idGenerator.nextId())
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .status(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userMapper.insert(user);
        log.info("Created user id={}", user.getId());
        return user;
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return userMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userMapper.findAll();
    }

    @Override
    @Transactional
    public User updateUser(Long id, CreateUserRequest request) {
        User existing = getUserById(id);
        existing.setUsername(request.getUsername());
        existing.setEmail(request.getEmail());
        existing.setPhone(request.getPhone());
        existing.setUpdatedAt(LocalDateTime.now());
        userMapper.update(existing);
        return existing;
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        getUserById(id); // verify existence
        userMapper.softDelete(id);
    }
}
