package org.dpp.tradelab.user.service

import org.dpp.tradelab.user.exception.OidcAuthenticationException
import org.dpp.tradelab.user.messaging.UserRegisteredEvent
import org.dpp.tradelab.user.model.ExternalIdentityProvider
import org.dpp.tradelab.user.model.ProviderType
import org.dpp.tradelab.user.model.User
import org.dpp.tradelab.user.model.UserStatus
import org.dpp.tradelab.user.repository.ExternalIdentityProviderRepository
import org.dpp.tradelab.user.repository.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class OidcAuthService(
    private val userRepository: UserRepository,
    private val externalIdentityProviderRepository: ExternalIdentityProviderRepository,
    private val userSettingsService: UserSettingsService,
    private val jwtService: JwtService,
    private val eventPublisher: ApplicationEventPublisher
) {

    @Transactional
    fun handleCallback(
        providerType: ProviderType,
        subId: String,
        email: String,
        firstName: String,
        lastName: String
    ): String {
        return try {
            val providerOpt = externalIdentityProviderRepository.findByProviderTypeAndSubId(providerType, subId)

            if (providerOpt.isPresent) {
                // Known provider — update lastAccessedAt and issue JWT
                val provider = providerOpt.get()
                provider.lastAccessedAt = Instant.now()
                externalIdentityProviderRepository.save(provider)
                jwtService.issueToken(provider.userId)
            } else {
                // Unknown provider — find or create user
                val userByEmail = userRepository.findByEmail(email)
                val user: User

                if (userByEmail.isPresent) {
                    // Existing user: link this provider to their profile
                    user = userByEmail.get()
                } else {
                    // New user: create profile, settings, and emit event
                    val newUser = User(
                        id = UUID.randomUUID(),
                        firstName = firstName,
                        lastName = lastName,
                        address = null,
                        email = email,
                        status = UserStatus.ACTIVE
                    )
                    user = userRepository.save(newUser)
                    userSettingsService.createDefaultSettings(user.id)
                    eventPublisher.publishEvent(
                        UserRegisteredEvent(
                            userId = user.id,
                            email = user.email,
                            timestamp = Instant.now()
                        )
                    )
                }

                // Create the provider record regardless of new/existing user
                val newProvider = ExternalIdentityProvider(
                    id = UUID.randomUUID(),
                    userId = user.id,
                    providerType = providerType,
                    subId = subId,
                    email = email,
                    lastAccessedAt = Instant.now()
                )
                externalIdentityProviderRepository.save(newProvider)

                jwtService.issueToken(user.id)
            }
        } catch (e: OidcAuthenticationException) {
            throw e
        } catch (e: Exception) {
            throw OidcAuthenticationException("OIDC callback handling failed: ${e.message}", e)
        }
    }
}
