import commands.make.*
import discoart.Client
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import net.dv8tion.jda.api.JDA

class QueueDispatcher(val jda: JDA) {
    val queue = FairQueue(config.maxEntriesPerOwner)
    private var queueStarted = false
    val channel: ManagedChannel = ManagedChannelBuilder.forAddress(config.grpcServer.host, config.grpcServer.port)
        .maxInboundMessageSize(1024 * 1024 * 1024).usePlaintext().build()
    val client = Client(channel)

    suspend fun startQueueDispatcher() {
        queueStarted = true
        while (queueStarted) {
            val entry = queue.next()
            if (entry != null) {
                try {
                    dispatch(entry)
                } catch (e: StatusException) {
                    entry.progressDelete()
                    entry.getChannel()
                        .sendMessage("${entry.getMember().asMention} Connection to the DiscoArt server has failed, it's likely that the bot is offline, we're sorry, please try again later!")
                        .queue()
                } catch (e: Exception) {
                    entry.progressDelete()
                    entry.getChannel().sendMessage("${entry.getMember().asMention} There's an internal error: $e")
                        .queue()
                }
            }
            delay(1000)
        }
    }

    private suspend fun dispatch(entry: FairQueueEntry) {
        var inProgress: MutableList<CreateArtParameters> = mutableListOf()
        val prompts = entry.getHumanReadablePrompts()
        val replyText = "**${entry.description}**\n> *$prompts*\n"
        entry.progressUpdate(replyText)
        val batch = entry.parameters
        coroutineScope {
            var finalImages: List<ByteArray>? = null
            async {
                for (params in batch) {
                    if (entry.type == FairQueueType.Create || entry.type == FairQueueType.Variate) {
                        client.createArt(params)
                    } else if (entry.type == FairQueueType.Upscale) {
                        client.upscaleArt(params)
                    }
                    inProgress.add(params)
                    while (inProgress.size >= config.hostConstraints.maxSimultaneousMakeRequests) {
                        delay(1000)
                    }
                    // Make sure GPU is cleaned up
                    delay(1000 * 5)
                }
            }
            val imageProgress = async {
                while (true) {
                    val newImages = mutableListOf<ByteArray>()
                    var completedCount = 0
                    for (params in batch) {
                        val (images, completed) = client.retrieveArt(params.artID)
                        if (completed) {
                            completedCount++
                            inProgress.remove(params)
                        }
                        if (images.isNotEmpty()) {
                            // For now, we use one image per batch to make sure we can let users experiment with different variables simultaneously
                            newImages.add(images.first())
                        }
                    }
                    if (completedCount == batch.size) {
                        finalImages = newImages
                        break
                    }
                    if (newImages.isNotEmpty()) {
                        val quilt = makeQuiltFromByteArrayList(newImages)
                        entry.progressUpdate(replyText, quilt, "${config.botName}_progress.jpg")
                    }
                    delay(1000 * 5)
                }
            }

            imageProgress.await()
            entry.progressDelete()
            if (finalImages != null) {
                val quilt = makeQuiltFromByteArrayList(finalImages!!)
                val (upscaleRow, variateRow) = getEditButtons(client, jda, entry.getMember().user, batch)
                var finishMsg = entry.getChannel()
                    .sendMessage("${entry.getMember().asMention}, we finished your image!\n> *${prompts}*")
                    .addFile(quilt, "${config.botName}_final.jpg")
                if (entry.type != FairQueueType.Upscale) {
                    finishMsg = finishMsg.setActionRows(upscaleRow, variateRow)
                }
                finishMsg.queue()
            }
        }
    }
}