package com.eventmanagement.security;

import com.eventmanagement.entity.User;
import com.eventmanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserDetailsServiceImplTest {

    private UserRepository userRepository;
    private UserDetailsServiceImpl userDetailsService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userDetailsService = new UserDetailsServiceImpl(userRepository);
    }

    @Test
    void loadUserByUsername_buildsUserDetailsWithRolePrefixedAuthority() {
        User user = User.builder()
                .id(7L)
                .email("organizer@example.com")
                .passwordHash("hashed-password")
                .role(User.Role.ORGANIZER)
                .build();
        when(userRepository.findByEmail("organizer@example.com")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("organizer@example.com");

        assertThat(details.getUsername()).isEqualTo("organizer@example.com");
        assertThat(details.getPassword()).isEqualTo("hashed-password");
        assertThat(details.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ORGANIZER");
    }

    @Test
    void loadUserByUsername_throwsWhenNoUserWithThatEmail() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("ghost@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
