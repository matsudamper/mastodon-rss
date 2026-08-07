package dev.matsudamper.mastodonrss

import dev.matsudamper.mastodonrss.activitypub.ActivityPubContentTypes
import dev.matsudamper.mastodonrss.json.AppJson
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val host = System.getenv("HOST") ?: "0.0.0.0"
    embeddedServer(CIO, port = port, host = host, module = Application::module).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(AppJson)

        // Mastodon はアクターを取りに来るときに Accept: application/activity+json を送る。
        // ここで登録しておかないと 406 になり、リモートからアクターを解決できない。
        register(ActivityPubContentTypes.ActivityJson, KotlinxSerializationConverter(AppJson))
        register(ActivityPubContentTypes.LdJson, KotlinxSerializationConverter(AppJson))
        register(ActivityPubContentTypes.JrdJson, KotlinxSerializationConverter(AppJson))
    }

    routing {
        get("/healthz") {
            call.respond(HealthResponse(status = "ok"))
        }
    }
}
