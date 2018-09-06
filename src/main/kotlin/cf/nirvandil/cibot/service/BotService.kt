package cf.nirvandil.cibot.service

import cf.nirvandil.cibot.log
import cf.nirvandil.cibot.model.BuildResult
import cf.nirvandil.cibot.model.BuildType
import cf.nirvandil.cibot.model.Description
import cf.nirvandil.cibot.props.CiProperties
import me.ivmg.telegram.Bot
import me.ivmg.telegram.entities.ChatAction
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.util.concurrent.ArrayBlockingQueue

class BotService(private val bambooClient: WebClient, private val appClient: WebClient,
                 private val bot: Bot, private val props: CiProperties) {

    private val queue = ArrayBlockingQueue<Description>(42)
    private val devChat: Long = props.devChat.toLong()

    fun addToQueue(item: Mono<Description>) {
        item.subscribe { queue.add(it) }
    }

    fun sendMessage(message: Mono<String>) {
        message.subscribe { bot.sendToDevChat(it) }
    }

    @Scheduled(fixedDelay = THIRTY_SECONDS)
    fun processDescribes() {
        log.debug("Start scheduled processing.")
        val description = queue.poll()
        if (description != null) {
            log.info("Found task number {} with type {} for explain. Sleeping for 1 minute to take time for Bamboo.", description.buildNumber, description.buildType)
            Thread.sleep(ONE_MINUTE)
            log.info("Start checking and sending message.")
            bot.sendChatAction(devChat, ChatAction.TYPING)
            bambooClient.get().uri("/latest/result/${description.buildType.toProject()}/${description.buildNumber}?expand=changes.change.files&os_authType=basic")
                    .retrieve().bodyToMono<BuildResult>()
                    .doOnNext { bot.sendToDevChat(it) }
                    .doOnError { bot.sendToDevChat("⛔ Не удалось проверить статус сборки:\n $it") }
                    .subscribe()
            if (description.buildType == BuildType.BACKEND) {
                Thread.sleep(30_000) // wait for app run
                appClient.get().uri(props.appCheckUrl)
                        .retrieve().bodyToMono<String>()
                        .doOnNext(this::parseCheck)
                        .doOnError(this::describeFail)
                        .subscribe()
            }
        }
    }

    private fun describeFail(err: Throwable) {
        if (err is WebClientResponseException && err.statusCode == FORBIDDEN)
            bot.sendToDevChat("Приложение находится в запущенном состоянии. ✅")
        else
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
        private const val ONE_MINUTE = 2 * THIRTY_SECONDS
    }
}
