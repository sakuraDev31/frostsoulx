/* Local cache for optional recommendation knowledge providers. */
package dev.vxs.frostsoulx.db.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "recommendation_knowledge",
    primaryKeys = ["provider", "knowledgeType", "lookupKey"],
    indices = [Index(value = ["expiresAtMs"]), Index(value = ["updatedAtMs"])],
)
data class RecommendationKnowledgeEntity(
    val provider: String,
    val knowledgeType: String,
    val lookupKey: String,
    val payload: String,
    val fetchedAtMs: Long,
    val expiresAtMs: Long,
    val updatedAtMs: Long = fetchedAtMs,
)
