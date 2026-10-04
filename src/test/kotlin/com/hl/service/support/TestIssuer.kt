package com.hl.service.support

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Date

/**
 * A throwaway OIDC issuer for Auth Seam tests (Story 5.4): serves the
 * discovery document and JWKS over loopback HTTP and mints RS256 JWTs signed
 * with its own key. No external identity provider is involved.
 */
class TestIssuer : AutoCloseable {
    private val key: RSAKey = RSAKeyGenerator(2048).keyID("test-key").generate()
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    val issuer: String

    init {
        issuer = "http://127.0.0.1:${server.address.port}"
        serve("/.well-known/openid-configuration", """{"issuer":"$issuer","jwks_uri":"$issuer/jwks"}""")
        serve("/jwks", JWKSet(key.toPublicJWK()).toString())
        server.start()
    }

    /** A token signed by this issuer's key; override [signingKey] to forge one. */
    fun token(
        audience: String,
        issuerClaim: String = issuer,
        signingKey: RSAKey = key,
    ): String {
        val claims =
            JWTClaimsSet
                .Builder()
                .issuer(issuerClaim)
                .subject("test-user")
                .audience(audience)
                .expirationTime(Date(System.currentTimeMillis() + 60_000))
                .build()
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.keyID).build(), claims)
        jwt.sign(RSASSASigner(signingKey))
        return jwt.serialize()
    }

    /** A different key under the same key id, so only the signature is wrong. */
    fun foreignKey(): RSAKey = RSAKeyGenerator(2048).keyID(key.keyID).generate()

    override fun close() = server.stop(0)

    private fun serve(
        path: String,
        body: String,
    ) {
        server.createContext(path) { exchange ->
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }
}
