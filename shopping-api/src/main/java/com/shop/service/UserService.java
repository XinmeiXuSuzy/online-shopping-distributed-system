package com.shop.service;

import com.shop.domain.User;
import com.shop.dto.request.CreateUserRequest;

import java.util.List;

public interface UserService {

    User createUser(CreateUserRequest request);

    User getUserById(Long id);

    List<User> getAllUsers();

    User updateUser(Long id, CreateUserRequest request);

    void deleteUser(Long id);
}
