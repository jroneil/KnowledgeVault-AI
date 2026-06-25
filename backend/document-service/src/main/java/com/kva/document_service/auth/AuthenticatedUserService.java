package com.kva.document_service.auth;

import com.kva.document_service.common.exceptions.ResourceNotFoundException;
import com.kva.document_service.users.User;
import com.kva.document_service.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticatedUserService {

    private final UserRepository userRepository;

    public User requireUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResourceNotFoundException("Authenticated user not found");
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user not found: " + authentication.getName()
                ));
    }

    public Long requireUserId(Authentication authentication) {
        return requireUser(authentication).getId();
    }
}
