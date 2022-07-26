package commands.make

import config
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.interactions.InteractionHook

class MemberLimitExceededException(message: String): Exception(message)

data class FairQueueEntry(val what: String, val owner: String, val parameters: List<CreateArtParameters>, val progressHook: InteractionHook) {
    fun progressUpdate(message: String) {
        progressHook.editOriginal(message).queue()
    }
    fun progressUpdate(message: String, fileBytes: ByteArray, fileName: String) {
        progressHook.editOriginal(message).retainFiles(listOf()).addFile(fileBytes, fileName).queue()
    }
    fun progressDelete() {
        progressHook.deleteOriginal().queue()
    }
    fun getChannel(): TextChannel {
        return progressHook.interaction.textChannel
    }
    fun getMember(): Member {
        return progressHook.interaction.member!!
    }
}

class FairQueue(maxEntriesPerOwner: Int) {
    private val queue = mutableListOf<FairQueueEntry>()

    fun next(): FairQueueEntry? {
        if(queue.isNotEmpty()) {
            return queue.removeAt(0)
        }
        return null
    }

    fun entryCount(owner: String): Int {
        return queue.count {
            it.owner == owner
        }
    }

    fun addToQueue(entry: FairQueueEntry) {
        val count = entryCount(entry.owner)
        if (count >= config.maxEntriesPerOwner) {
            throw MemberLimitExceededException("${entry.owner} has currently $count items in the queue! Max is ${config.maxEntriesPerOwner}")
        }
        queue.add(entry)
    }
}