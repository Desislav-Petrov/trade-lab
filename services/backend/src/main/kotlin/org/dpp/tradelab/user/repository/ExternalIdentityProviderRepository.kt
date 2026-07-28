package org.dpp.tradelab.user.repository

import org.dpp.tradelab.user.model.ExternalIdentityProvider
import org.dpp.tradelab.user.model.ProviderType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface ExternalIdentityProviderRepository : JpaRepository<ExternalIdentityProvider, UUID> {
    fun findByProviderTypeAndSubId(providerType: ProviderType, subId: String): Optional<ExternalIdentityProvider>
    fun findByUserIdAndProviderType(userId: UUID, providerType: ProviderType): Optional<ExternalIdentityProvider>
}
