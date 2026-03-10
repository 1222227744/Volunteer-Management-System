package com.volunteer.vms.auth;

import com.volunteer.vms.user.User;
import com.volunteer.vms.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenSessionService {
    private final ConcurrentHashMap<String, Long> tokenToUserId = new ConcurrentHashMap<>();
    private final UserRepository userRepository;

    public TokenSessionService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String createToken(User user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenToUserId.put(token, user.getId());
        return token;
    }

    public Optional<User> resolveUser(String token) {
        Long userId = tokenToUserId.get(token);
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findById(userId);
    }

    public void removeToken(String token) {
        tokenToUserId.remove(token);
    }
}
