package org.dpp.tradelab.user.service

import org.dpp.tradelab.user.exception.OidcAuthenticationException
import org.dpp.tradelab.user.model.ExternalIdentityProvider
import org.dpp.tradelab.user.model.ProviderType
import org.dpp.tradelab.user.model.User
import org.dpp.tradelab.user.model.UserStatus
import org.dpp.tradelab.user.repository.ExternalIdentityProviderRepository
import org.dpp.tradelab.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class OidcAuthService(
    private val userRepository: UserRepository,
    private val externalIdentityProviderRepository: ExternalIdentityProviderRepository,
    private val userSettingsService: UserSettingsService,
    private val jwtService: JwtService
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
            // Look up provider
            val provider = externalIdentityProviderRepository.findByProviderTypeAndSubId(providerType, subId)

            if (provider.isPresent) {
                // Provider found: update lastAccessedAt and issue JWT
                val existingProvider = provider.get()
                existingProvider.lastAccessedAt = Instant.now()
                externalIdentityProviderRepository.save(existingProvider)
                jwtService.issueToken(existingProvider.userId)
            } else {
                // Provider not found: check if user exists by email
                val userByEmail = userRepository.findByEmail(email)

                val user = if (userByEmail.isPresent) {
                    // User found: link provider to existing user
                    userByEmail.get()
                } else {
                    // User not found: create new user
                    val newUser = User(
                        id = UUID.randomUUID(),
                        firstName = firstName,
                        lastName = lastName,
                        address = null,
                        email = email,
                        status = UserStatus.ACTIVE
                    )
                    val savedUser = userRepository.save(newUser)
                    userSettingsService.createDefaultSettings(savedUser.id)
                    savedUser
                }

                // Create external identity provider record
                val newProvider = ExternalIdentityProvider(
                    id = UUID.randomUUID(),
                    userId = user.id,
                    providerType = providerType,
                    subId = subId,
                    email = email,
                    lastAccessedAt = Instant.now()
                )
                externalIdentityProviderRepository.save(newProvider)

                // Issue JWT
                jwtService.issueToken(user.id)
            }
        } catch (e: Exception) {
            throw OidcAuthenticationException("OIDC callback handling failed: ${e.message}", e)
        }
    }
}
