package cf.nirvandil.cibot.service

import cf.nirvandil.cibot.log
import cf.nirvandil.cibot.model.BuildResult
import cf.nirvandil.cibot.props.CiProperties
import me.ivmg.telegram.Bot
import me.ivmg.telegram.entities.ChatAction
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.util.concurrent.ArrayBlockingQueue

class BotService(private val bambooClient: WebClient, private val appClient: WebClient,
                 private val bot: Bot, private val props: CiProperties) {

    private val queue = ArrayBlockingQueue<Long>(42)
    private val devChat: Long = props.devChat.toLong()

    fun addToQueue(item: Mono<Long>) {
        item.subscribe { queue.add(it) }
    }

    fun sendMessage(message: Mono<String>) {
        message.subscribe { bot.sendToDevChat(it) }
    }

    @Scheduled(fixedDelay = THIRTY_SECONDS)
    fun processDescribes() {
        log.info("Start scheduled processing.")
        val taskNumber = queue.poll()
        if (taskNumber != null) {
            log.info("Found task number {} for explain. Sleeping for 10 seconds to take time for Bamboo.", taskNumber)
            Thread.sleep(TEN_SECONDS)
            log.info("Start checking and sending message.")
            bot.sendChatAction(devChat, ChatAction.TYPING)
            bambooClient.get().uri("/latest/result/EGIP-BACK/$taskNumber?expand=changes.change.files&os_authType=basic")
                    .retrieve().bodyToMono<BuildResult>()
                    .doOnNext { bot.sendToDevChat(it) }
                    .doOnError { bot.sendToDevChat("⛔ Не удалось проверить статус сборки:\n $it") }
                    .then(appClient.get().uri(props.appCheckUrl)
                            .retrieve().bodyToMono<String>()
                            .doOnNext(this::parseCheck)
                            .doOnError(this::describeFail)
                    )
                    .subscribe()
        }
    }

    private fun describeFail(err: Throwable) {
        bot.sendToDevChat("⛔ Похоже, запуск проекта завершился неудачно: \n $err")
    }

    private fun <T> Bot.sendToDevChat(message: T) {
        val text = if (message is String) message else message.toString()
        log.debug("Sending message $text to chat $devChat")
        sendMessage(devChat, text)
    }

    private fun parseCheck(result: String) {
        log.info("Result of checking: $result")
        if (result.contains("OK"))
            bot.sendToDevChat("Приложение находится в запущенном состоянии. ✅")
        else
            bot.sendToDevChat("⛔ Похоже, приложение не запущено: \n $result")
    }

    companion object {
        private const val TEN_SECONDS = 10_000L
        private const val THIRTY_SECONDS = 3 * TEN_SECONDS
    }
}