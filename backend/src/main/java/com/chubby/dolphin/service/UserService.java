package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.AuditLog;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.repository.AuditLogRepository;
import com.chubby.dolphin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class UserService implements UserDetailsService {

    private final UserRepository userRepo;
    private final AuditLogRepository auditRepo;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepo, AuditLogRepository auditRepo, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.auditRepo = auditRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    public User findByEmail(String email) {
        return userRepo.findByEmail(email).orElseThrow();
    }

    public void recordLogin(String email, String ip) {
        userRepo.findByEmail(email).ifPresent(u -> {
            u.setLastLogin(LocalDateTime.now());
            userRepo.save(u);
        });
        audit(email, "LOGIN", "USER", email, "Successful login from " + ip, ip);
    }

    public void audit(String email, String action, String type, String resourceId, String details, String ip) {
        AuditLog log = new AuditLog();
        log.setUserEmail(email);
        log.setAction(action);
        log.setResourceType(type);
        log.setResourceId(resourceId);
        log.setDetails(details);
        log.setIpAddress(ip);
        auditRepo.save(log);
    }
}
