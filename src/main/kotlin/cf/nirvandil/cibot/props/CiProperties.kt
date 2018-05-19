package cf.nirvandil.cibot.props

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("ci")
class CiProperties {
    var ciBaseUrl: String = ""
    var ciCredentials: String = ""
    var appBaseUrl: String = ""
    var appCheckUrl: String = ""
    var botToken: String = ""
    var devChat: String = ""
}