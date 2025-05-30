package app.aaps.database.transactions

import app.aaps.database.entities.OfflineEvent
import app.aaps.database.entities.interfaces.end

class InsertAndCancelCurrentOfflineEventTransaction(
    private val offlineEvent: OfflineEvent
) : Transaction<InsertAndCancelCurrentOfflineEventTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val current = database.offlineEventDao.getOfflineEventActiveAtLegacy(offlineEvent.timestamp)
        if (current != null) {
            current.end = offlineEvent.timestamp
            database.offlineEventDao.updateExistingEntry(current)
            result.updated.add(current)
        }
        database.offlineEventDao.insertNewEntry(offlineEvent)
        result.inserted.add(offlineEvent)
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<OfflineEvent>()
        val updated = mutableListOf<OfflineEvent>()
    }
}