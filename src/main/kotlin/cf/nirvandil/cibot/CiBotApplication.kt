package cf.nirvandil.cibot

import cf.nirvandil.cibot.service.BotService
import cf.nirvandil.cibot.props.CiProperties
import me.ivmg.telegram.bot
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationContextInitializer
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

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties
class CiBotApplication

val log: Logger = LoggerFactory.getLogger("CIGostGroup")

fun main(args: Array<String>) {
    runApplication<CiBotApplication>(*args)
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
                POST("/explain") {
                    botService.addToQueue(it.bodyToMono())
                    ok().build()
                }
            }
        }
    }

    bean("Bamboo") {
        val props = ref<CiProperties>()
        WebClient.builder()
                .baseUrl(props.ciBaseUrl)
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

class BeansInitializer : ApplicationContextInitializer<GenericApplicationContext> {
    override fun initialize(context: GenericApplicationContext) = beans().initialize(context)
}