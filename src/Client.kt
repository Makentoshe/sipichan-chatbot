package com.makentoshe.schatbot

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import space.jetbrains.api.runtime.SpaceHttpClient
import space.jetbrains.api.runtime.resources.chats
import space.jetbrains.api.runtime.types.ApplicationPayload
import space.jetbrains.api.runtime.types.ChatMessage
import space.jetbrains.api.runtime.types.MessageRecipient
import space.jetbrains.api.runtime.types.ProfileIdentifier
import space.jetbrains.api.runtime.withServiceAccountTokenSource
import space.jetbrains.yana.verifyWithToken
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Url of your Space instance.
 * This value may used for defining Space client using Client Credentials auth flow
 *
 * Example: https://instance.jetbrains.space
 */
private const val spaceInstanceUrl = "https://makentoshe.jetbrains.space"

/**
 * Client for communication with Space. This implementation uses the Client Credentials auth flow.
 *
 * [spaceClient] is our API client – an instance of the SpaceHttpClientWithCallContext class.
 * WithCallContext stays here for a reason. Such a client allows working with the call context –
 * additional data that clarifies who made the call, how it was made, and so on.
 * We will use the context to get the ID of the user who made the request.
 *
 * SpaceHttpClient(HttpClient(CIO)).withServiceAccountTokenSource defines how the client will authenticate in Space:
 * withServiceAccountTokenSource is used for the Client Credentials Flow.
 * Other options would be withCallContext - for Refresh Token Flow and
 * withPermanentToken for Personal Token Authorization and other flows.
 *
 * HttpClient(CIO) here is a Ktor CIO HTTP client.
 */
val spaceClient = SpaceHttpClient(HttpClient(CIO)).withServiceAccountTokenSource(
    ClientCredentialsFlow.clientId, ClientCredentialsFlow.clientSecret, spaceInstanceUrl
)

/**
 * The Client Credentials Flow can be used by a server-side application that
 * accesses Space on behalf of itself, for example, a chatbot. The application
 * receives an access token from Space by sending it <i>client_id</i> and <i>client_secret</i>.
 *
 * Not all operations may be accessible using the Client Credentials Flow.
 * Many actions (for example, posting an article draft) require user consent
 * and cannot be performed with application credentials.
 *
 * For actions that should be performed on behalf of the user,
 * use other authorization flows, for example Resource Owner Password Credentials Flow.
 */
object ClientCredentialsFlow {

    const val clientId: String = "cfbee418-bac0-45fa-bd8e-191bbd295120"

    const val clientSecret: String = "b4bd9f8b8599b39f56a7c54cdd8514165913d08c34aa19da3b926eafebe8c06e"
}

/**
 * If your application (for example, a chatbot) must receive requests from Space,
 * it should be able to verify whether the incoming requests are authentic.
 */
object Endpoint {

    /**
     * The application compares a verification token in the request
     * to the verification token stored in the application. This method is easier
     * to implement but is less secure than the <i>Signing key</i>.
     */
    /*
        When Space sends a request to your application, it puts this verification
        token in the request body. For example, this is how the body of a slash
        command request looks like (a user presses / in the chatbot's channel):
        {
            "className": "ListCommandsPayload",
            "accessToken": "",
            "verificationToken": "d415ca5965b37f4f0cac59fd33de7b94e396284e897d0fb8a070d0a5e1b7f2d3",
            "userId": "2kawvQ4F6GM6"
        }
     */
    const val verificationToken: String = "eef37259ab2805854593683b9e7776de3615a942cec7892bd2bc228ea1b47d7a"

    /**
     * The application calculates a hash for request and compares the result with the hash from the request.
     */
    const val signingKey: String = "23f4245714ea018a3b73f8b2731e241d0acee4f098ed14b6c2b1cafdd5d41ee8"

    /**
     * Verification of Space instance
     *
     * Compares the token in payload with the verification token
     */
    fun verify(payload: ApplicationPayload): Boolean {
        return payload.verifyWithToken(verificationToken)
    }

    /**
     * Verification of Space instance
     *
     * Calculates a hash from the signing key and compares with the hash from the call
     */
    fun verify(timestamp: String, signature: String, body: String): Boolean {
        val hmacSha256 = Mac.getInstance("HmacSHA256")
        hmacSha256.init(SecretKeySpec(signingKey.toByteArray(), "HmacSHA256"))
        val hash = hmacSha256.doFinal("$timestamp:$body".toByteArray()).toHexString()
        return hash == signature
    }

    private fun ByteArray.toHexString() = joinToString("") { (0xFF and it.toInt()).toString(16).padStart(2, '0') }
}

/**
 * This method allows to our bot print a message for selected user which id is provided in [context].
 *
 * [spaceClient] gives us access to any Space endpoint.
 */
suspend fun printToChat(context: UserContext, message: ChatMessage) {
    val member = ProfileIdentifier.Id(context.userId)
    spaceClient.chats.messages.sendMessage(MessageRecipient.Member(member), message)
}
