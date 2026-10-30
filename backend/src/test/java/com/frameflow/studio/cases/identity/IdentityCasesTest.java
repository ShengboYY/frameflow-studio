package com.frameflow.studio.cases.identity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import com.frameflow.studio.cases.identity.impl.AuthenticateAccountCaseImpl;
import com.frameflow.studio.cases.identity.impl.InitializeAccountsCaseImpl;
import com.frameflow.studio.cases.identity.impl.QueryAccountCaseImpl;
import com.frameflow.studio.cases.identity.model.InvalidCredentialsException;
import com.frameflow.studio.domain.identity.adapter.port.IPasswordVerifier;
import com.frameflow.studio.domain.identity.adapter.repository.IAccountRepository;
import com.frameflow.studio.domain.identity.model.entity.Account;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdentityCasesTest {
    private final IAccountRepository repository = mock(IAccountRepository.class);
    private final IPasswordVerifier passwords = mock(IPasswordVerifier.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final TransactionStatus status = mock(TransactionStatus.class);
    private final Instant now = Instant.parse("2026-10-30T00:00:00.123456Z");
    private final Account account = new Account(UUID.randomUUID(), "demo-a", "演示用户 A", "encoded", now);
    private InitializeAccountsCaseImpl initialize;

    @BeforeEach
    void prepare() {
        when(repository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(transactions.getTransaction(any())).thenReturn(status);
        when(passwords.encode(anyString())).thenReturn("encoded");
        initialize = new InitializeAccountsCaseImpl(repository, passwords,
                Clock.fixed(now, ZoneOffset.UTC), transactions);
    }

    @Test
    void authenticatesWithoutReturningTheHashAndQueriesStableIdentity() {
        when(repository.findByUsername("demo-a")).thenReturn(Optional.of(account));
        when(repository.findById(account.getAccountId())).thenReturn(Optional.of(account));
        when(passwords.matches(" untouched ", "encoded")).thenReturn(true);
        var result = new AuthenticateAccountCaseImpl(repository, passwords).authenticate("demo-a", " untouched ");
        assertThat(result.ownerId()).isEqualTo(account.getAccountId());
        assertThat(result.toString()).doesNotContain("encoded");
        assertThat(new QueryAccountCaseImpl(repository).findById(result.ownerId())).contains(result);
    }

    @Test
    void unknownUsernameAndWrongPasswordShareTheSameFailureAndComparisonPath() {
        var authentication = new AuthenticateAccountCaseImpl(repository, passwords);
        assertThatThrownBy(() -> authentication.authenticate("unknown", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Invalid credentials");
        verify(passwords).matches("wrong", null);
        when(repository.findByUsername("demo-a")).thenReturn(Optional.of(account));
        assertThatThrownBy(() -> authentication.authenticate("demo-a", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Invalid credentials");
        verify(passwords).matches("wrong", "encoded");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "中中"})
    void missingOrShortInitialPasswordsFailBeforeHashingOrWriting(String password) {
        assertThatThrownBy(() -> initialize.initialize("valid-initial", password))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("DEMO_B_PASSWORD");
        verifyNoInteractions(passwords, transactions);
        verify(repository, never()).insertIfAbsent(any());
    }

    @Test
    void enforcesUtf8BytesInsteadOfCharacterCount() {
        assertThatThrownBy(() -> initialize.initialize("中".repeat(25), "valid-initial"))
                .isInstanceOf(IllegalArgumentException.class);
        initialize.initialize("中".repeat(24), "八字节密");
        verify(passwords).encode("中".repeat(24));
        verify(repository, times(2)).insertIfAbsent(any());
    }

    @Test
    void hashesBeforeTheTransactionAndCommitsBothAccountsAsOneUnit() {
        AtomicBoolean transactionStarted = new AtomicBoolean();
        when(transactions.getTransaction(any())).thenAnswer(invocation -> {
            transactionStarted.set(true);
            return status;
        });
        when(passwords.encode(anyString())).thenAnswer(invocation -> {
            assertThat(transactionStarted).isFalse();
            return "encoded";
        });
        initialize.initialize("initial-a", "initial-b");
        ArgumentCaptor<Account> inserted = ArgumentCaptor.forClass(Account.class);
        verify(repository, times(2)).insertIfAbsent(inserted.capture());
        assertThat(inserted.getAllValues()).extracting(Account::getUsername).containsExactly("demo-a", "demo-b");
        assertThat(inserted.getAllValues()).allSatisfy(a -> assertThat(a.getCreatedAt()).isEqualTo(now));
        verify(transactions).commit(status);
    }

    @Test
    void neverRequiresSecretsOrWritesWhenBothPresetsExist() {
        when(repository.findByUsername(anyString())).thenReturn(Optional.of(account));
        initialize.initialize(null, null);
        verifyNoInteractions(passwords, transactions);
        verify(repository, never()).insertIfAbsent(any());
    }

    @Test
    void rollbackIsRequestedWhenAnInsertFails() {
        doThrow(new IllegalStateException("simulated write failure")).when(repository).insertIfAbsent(any());
        assertThatThrownBy(() -> initialize.initialize("initial-a", "initial-b"))
                .isInstanceOf(IllegalStateException.class);
        verify(transactions).rollback(status);
        verify(transactions, never()).commit(any());
    }
}
