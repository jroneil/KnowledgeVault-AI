package com.kva.document_service.configuration;

import com.kva.document_service.roles.Role;
import com.kva.document_service.roles.RoleRepository;
import com.kva.document_service.users.User;
import com.kva.document_service.users.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        initializeRoles();
        initializeAdminUser();
    }

    private void initializeRoles() {
        List<String> roleNames = List.of("ADMIN", "CONTRIBUTOR", "VIEWER");
        
        for (String roleName : roleNames) {
            if (roleRepository.findByName(roleName).isEmpty()) {
                Role role = Role.builder()
                        .name(roleName)
                        .description("Default " + roleName + " role")
                        .build();
                roleRepository.save(role);
                log.info("Created role: {}", roleName);
            } else {
                log.debug("Role already exists: {}", roleName);
            }
        }
    }

    private void initializeAdminUser() {
        String adminUsername = "admin";
        String adminEmail = "admin@knowledgevault.ai";
        String adminPassword = "admin123"; // Change in production!

        // Check if admin user already exists
        if (userRepository.findByUsername(adminUsername).isPresent()) {
            log.info("Admin user already exists: {}", adminUsername);
            return;
        }

        // Create admin user
        User adminUser = User.builder()
                .username(adminUsername)
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .firstName("System")
                .lastName("Administrator")
                .status("ACTIVE")
                .build();

        User savedUser = userRepository.save(adminUser);
        log.info("Created admin user: {}", adminUsername);

        // Assign ADMIN role to admin user
        roleRepository.findByName("ADMIN").ifPresentOrElse(
                adminRole -> {
                    roleRepository.assignRoleToUser(savedUser.getId(), adminRole.getId());
                    log.info("Assigned ADMIN role to user: {}", adminUsername);
                },
                () -> log.error("ADMIN role not found when initializing admin user")
        );

        log.warn("Default admin credentials - Username: {}, Password: {}", adminUsername, adminPassword);
        log.warn("Please change the default admin password in production!");
    }
}