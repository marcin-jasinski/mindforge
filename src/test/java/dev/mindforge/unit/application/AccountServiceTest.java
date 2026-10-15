package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.mindforge.application.service.AccountService;
import dev.mindforge.domain.model.AccountException;
import dev.mindforge.domain.model.User;
import dev.mindforge.domain.port.PasswordHasher;
import dev.mindforge.domain.port.UserRepository;
import dev.mindforge.support.TestFixtures;

class AccountServiceTest {

    private final UserRepository users = makeUsers();
    private final PasswordHasher hasher = makeHasher();

    @Test
    void shouldRegisterWithALowercasedEmailAndAHashedPassword() {
        User user = new AccountService(users, hasher).register(" Alice\n", "Alice@Example.COM ", "secret-password");

        assertThat(user).extracting(User::displayName, User::email, User::passwordHash)
            .containsExactly("Alice", "alice@example.com", "hashed:secret-password");
    }

    @Test
    void shouldCountRegisteringAsTheFirstLogin() {
        User user = new AccountService(users, hasher).register("Alice", "alice@example.com", "secret-password");

        verify(users).recordLogin(any(UUID.class), any(Instant.class));
        assertThat(user.email()).isEqualTo("alice@example.com");
    }

    @Test
    void shouldRefuseASecondAccountForTheSameEmail() {
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(TestFixtures.makeUser(null, "alice@example.com")));

        assertThatExceptionOfType(AccountException.class)
            .isThrownBy(() -> new AccountService(users, hasher).register("Alice", "ALICE@example.com", "password-password"))
            .satisfies(e -> assertThat(e.conflict()).isTrue());
        verify(users, never()).insert(any());
    }

    @Test
    void shouldRefuseAWrongPasswordAndAnAccountWithoutOneAlike() {
        User withPassword = new User(UUID.randomUUID(), "Alice", "alice@example.com", "hashed:good", null, Instant.EPOCH,
            null);
        User providerOnly = new User(UUID.randomUUID(), "Olivia", "olivia@example.com", null, null, Instant.EPOCH, null);
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(withPassword));
        when(users.findByEmail("olivia@example.com")).thenReturn(Optional.of(providerOnly));
        AccountService accounts = new AccountService(users, hasher);

        assertThatExceptionOfType(AccountException.class).isThrownBy(() -> accounts.authenticate("alice@example.com", "wrong"))
            .satisfies(e -> assertThat(e.conflict()).isFalse());
        assertThatExceptionOfType(AccountException.class).isThrownBy(() -> accounts.authenticate("olivia@example.com", ""));
        assertThat(accounts.authenticate("ALICE@example.com", "good")).isEqualTo(withPassword);
        verify(users).recordLogin(any(), any());
    }

    @Test
    void shouldCreateAProviderAccountOnFirstSignInAndReuseItAfterwards() {
        AccountService accounts = new AccountService(users, hasher);

        User created = accounts.signInWithProvider("Olivia@Example.com", "Olivia", "https://example.org/a.png");
        when(users.findByEmail("olivia@example.com")).thenReturn(Optional.of(created));
        User again = accounts.signInWithProvider("olivia@example.com", "Olivia", null);

        assertThat(created).extracting(User::email, User::passwordHash, User::avatarUrl)
            .containsExactly("olivia@example.com", null, "https://example.org/a.png");
        assertThat(again).isEqualTo(created);
        verify(users).insert(any());
    }

    private static UserRepository makeUsers() {
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmail(any())).thenReturn(Optional.empty());
        when(users.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return users;
    }

    private static PasswordHasher makeHasher() {
        return new PasswordHasher() {
            @Override
            public String hash(String rawPassword) {
                return "hashed:" + rawPassword;
            }

            @Override
            public boolean matches(String rawPassword, String hash) {
                return hash.equals("hashed:" + rawPassword);
            }
        };
    }
}
