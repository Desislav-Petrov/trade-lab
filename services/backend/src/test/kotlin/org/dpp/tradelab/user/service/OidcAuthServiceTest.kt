package org.dpp.tradelab.user.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.user.exception.OidcAuthenticationException
import org.dpp.tradelab.user.messaging.UserRegisteredEvent
import org.dpp.tradelab.user.model.ExternalIdentityProvider
import org.dpp.tradelab.user.model.ProviderType
import org.dpp.tradelab.user.model.User
import org.dpp.tradelab.user.model.UserStatus
import org.dpp.tradelab.user.repository.ExternalIdentityProviderRepository
import org.dpp.tradelab.user.repository.UserRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.Optional
import java.util.UUID

class OidcAuthServiceTest : FunSpec() {

    private val userRepository = mock<UserRepository>()
    private val externalIdentityProviderRepository = mock<ExternalIdentityProviderRepository>()
    private val userSettingsService = mock<UserSettingsService>()
    private val jwtService = mock<JwtService>()
    private val eventPublisher = mock<ApplicationEventPublisher>()

    private val oidcAuthService = OidcAuthService(
        userRepository = userRepository,
        externalIdentityProviderRepository = externalIdentityProviderRepository,
        userSettingsService = userSettingsService,
        jwtService = jwtService,
        eventPublisher = eventPublisher
    )

    init {
        beforeTest {
            reset(
                userRepository,
                externalIdentityProviderRepository,
                userSettingsService,
                jwtService,
                eventPublisher
            )
        }

        test("handleCallback_knownProvider_returnsJwtWithoutCreatingUser") {
            val providerId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val providerType = ProviderType.GOOGLE
            val subId = "google-sub-123"
            val email = "user@example.com"
            val expectedToken = "jwt-token-123"

            val existingProvider = ExternalIdentityProvider(
                id = providerId,
                userId = userId,
                providerType = providerType,
                subId = subId,
                email = email,
                lastAccessedAt = Instant.now()
            )

            whenever(externalIdentityProviderRepository.findByProviderTypeAndSubId(providerType, subId))
                .thenReturn(Optional.of(existingProvider))
            whenever(jwtService.issueToken(userId)).thenReturn(expectedToken)

            val token = oidcAuthService.handleCallback(
                providerType = providerType,
                subId = subId,
                email = email,
                firstName = "John",
                lastName = "Doe"
            )

            token shouldBe expectedToken
            verify(externalIdentityProviderRepository, times(1)).save(any())
            verify(userRepository, never()).save(any())
            verify(userSettingsService, never()).createDefaultSettings(any())
            verify(eventPublisher, never()).publishEvent(any())
        }

        test("handleCallback_unknownProviderExistingUser_linksProviderAndReturnsJwt") {
            val userId = UUID.randomUUID()
            val providerType = ProviderType.GOOGLE
            val subId = "google-sub-456"
            val email = "existing@example.com"
            val expectedToken = "jwt-token-456"

            val existingUser = User(
                id = userId,
                firstName = "Jane",
                lastName = "Doe",
                address = null,
                email = email,
                status = UserStatus.ACTIVE
            )

            whenever(externalIdentityProviderRepository.findByProviderTypeAndSubId(providerType, subId))
                .thenReturn(Optional.empty())
            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser))
            whenever(jwtService.issueToken(userId)).thenReturn(expectedToken)

            val token = oidcAuthService.handleCallback(
                providerType = providerType,
                subId = subId,
                email = email,
                firstName = "John",
                lastName = "Doe"
            )

            token shouldBe expectedToken
            verify(externalIdentityProviderRepository, times(1)).save(any())
            verify(userRepository, never()).save(any())
            verify(userSettingsService, never()).createDefaultSettings(any())
            verify(eventPublisher, never()).publishEvent(any())
        }

        test("handleCallback_unknownProviderNewUser_createsUserAndEmitsEvent") {
            val providerType = ProviderType.GOOGLE
            val subId = "google-sub-789"
            val email = "newuser@example.com"
            val firstName = "John"
            val lastName = "Smith"
            val expectedToken = "jwt-token-789"

            whenever(externalIdentityProviderRepository.findByProviderTypeAndSubId(providerType, subId))
                .thenReturn(Optional.empty())
            whenever(userRepository.findByEmail(email)).thenReturn(Optional.empty())

            val newUserId = UUID.randomUUID()
            val savedUser = User(
                id = newUserId,
                firstName = firstName,
                lastName = lastName,
                address = null,
                email = email,
                status = UserStatus.ACTIVE
            )

            whenever(userRepository.save(any())).thenReturn(savedUser)
            whenever(jwtService.issueToken(newUserId)).thenReturn(expectedToken)

            val token = oidcAuthService.handleCallback(
                providerType = providerType,
                subId = subId,
                email = email,
                firstName = firstName,
                lastName = lastName
            )

            token shouldBe expectedToken
            verify(userRepository, times(1)).save(any())
            verify(userSettingsService, times(1)).createDefaultSettings(newUserId)

            val eventCaptor = argumentCaptor<UserRegisteredEvent>()
            verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture())
            val event = eventCaptor.firstValue
            event.userId shouldBe newUserId
            event.email shouldBe email
        }

        test("handleCallback_repositoryException_wrapsInOidcAuthenticationException") {
            val providerType = ProviderType.GOOGLE
            val subId = "google-sub-error"
            val email = "error@example.com"

            whenever(externalIdentityProviderRepository.findByProviderTypeAndSubId(providerType, subId))
                .thenThrow(RuntimeException("DB error"))

            shouldThrow<OidcAuthenticationException> {
                oidcAuthService.handleCallback(
                    providerType = providerType,
                    subId = subId,
                    email = email,
                    firstName = "John",
                    lastName = "Doe"
                )
            }
        }

        test("handleCallback_newUserSettingsServiceException_propagatesAsOidcAuthenticationException") {
            val providerType = ProviderType.GOOGLE
            val subId = "google-sub-settings-error"
            val email = "settings-error@example.com"

            whenever(externalIdentityProviderRepository.findByProviderTypeAndSubId(providerType, subId))
                .thenReturn(Optional.empty())
            whenever(userRepository.findByEmail(email)).thenReturn(Optional.empty())

            val newUserId = UUID.randomUUID()
            val savedUser = User(
                id = newUserId,
                firstName = "John",
                lastName = "Doe",
                address = null,
                email = email,
                status = UserStatus.ACTIVE
            )

            whenever(userRepository.save(any())).thenReturn(savedUser)
            whenever(userSettingsService.createDefaultSettings(newUserId))
                .thenThrow(RuntimeException("Settings error"))

            shouldThrow<OidcAuthenticationException> {
                oidcAuthService.handleCallback(
                    providerType = providerType,
                    subId = subId,
                    email = email,
                    firstName = "John",
                    lastName = "Doe"
                )
            }
        }
    }
}
