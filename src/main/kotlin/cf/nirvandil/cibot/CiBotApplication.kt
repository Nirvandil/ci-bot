package cf.nirvandil.cibot

import cf.nirvandil.cibot.model.BuildType
import cf.nirvandil.cibot.model.Description
import cf.nirvandil.cibot.props.CiProperties
import cf.nirvandil.cibot.service.BotService
import me.ivmg.telegram.bot
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.support.GenericApplicationContext
import org.springframework.context.support.beans
import org.springframework.http.HttpHeaders.ACCEPT
import org.springframework.http.HttpHeaders.AUTHORIZATION
import org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.server.ServerResponse.ok
import org.springframework.web.reactive.function.server.bodyToMono
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Mono

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties
class CiBotApplication {
    @Autowired
    fun initializeContext(ctx: GenericApplicationContext) {
        beans().initialize(ctx)
    }
}

val log: Logger = LoggerFactory.getLogger("CIGostGroup")

fun main(args: Array<String>) {
    runApplication<CiBotApplication>(*args) {
        addInitializers(beans())
    }
}

fun beans() = beans {

    bean("CiBot") {
        val bot = bot { token = ref<CiProperties>().botToken }
        bot.startPolling()
        bot
    }

    bean {
        router {
            "/api".nest {
                val botService = ref<BotService>()
                POST("/message") {
                    botService.sendMessage(it.bodyToMono())
                    ok().build()
                }
                POST("/explain/{buildNumber}/{buildType}") {
                    val description = Description(it.pathVariable("buildNumber").toInt(), BuildType.valueOf(it.pathVariable("buildType")))
                    botService.addToQueue(Mono.just(description))
                    ok().build()
                }
            }
        }
    }

    bean("Bamboo") {
        val props = ref<CiProperties>()
        WebClient.builder()
                .baseUrl("${props.ciBaseUrl}/rest/api")
                .defaultHeader(AUTHORIZATION, "Basic ${props.ciCredentials}")
                .defaultHeader(ACCEPT, APPLICATION_JSON_UTF8_VALUE)
                .build()
    }

    bean("AppClient") {
        WebClient.builder()
                .baseUrl(ref<CiProperties>().appBaseUrl)
                .defaultHeader(ACCEPT, APPLICATION_JSON_UTF8_VALUE)
                .build()
    }

    bean { BotService(ref("Bamboo"), ref("AppClient"), ref(), ref()) }
    bean<CiProperties>()
}