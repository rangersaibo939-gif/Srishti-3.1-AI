package com.opendroid.app.core.security

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/**
 * Real RFC 4122 UUID Version 5 implementation (SHA-1 hashing of Namespace + Name)
 * Generates deterministic, globally unique, idempotent operation keys.
 */
object UUIDv5 {

    // Predefined ISO OID namespace UUID for OpenDroid
    val OPENDROID_NAMESPACE: UUID = UUID.fromString("6ba7b812-9dad-11d1-80b4-00c04fd430c8")

    /**
     * Generates a UUIDv5 from a namespace UUID and a name string.
     */
    fun generate(namespace: UUID, name: String): UUID {
        val digest = MessageDigest.getInstance("SHA-1")

        // 1. Hash the 16 bytes of the namespace UUID
        val namespaceBuffer = ByteBuffer.wrap(ByteArray(16))
        namespaceBuffer.putLong(namespace.mostSignificantBits)
        namespaceBuffer.putLong(namespace.leastSignificantBits)
        digest.update(namespaceBuffer.array())

        // 2. Hash the UTF-8 bytes of the name
        digest.update(name.toByteArray(StandardCharsets.UTF_8))
        val sha1Bytes = digest.digest()

        // 3. Set UUID version to 5 (bits 4-7 of time_hi_and_version)
        sha1Bytes[6] = (sha1Bytes[6].toInt() and 0x0f or 0x50).toByte()

        // 4. Set variant to RFC 4122 (bits 6-7 of clock_seq_hi_and_reserved)
        sha1Bytes[8] = (sha1Bytes[8].toInt() and 0x3f or 0x80.toInt()).toByte()

        // 5. Construct UUID from the first 16 bytes of SHA-1
        val resultBuffer = ByteBuffer.wrap(sha1Bytes)
        val msb = resultBuffer.long
        val lsb = resultBuffer.long

        return UUID(msb, lsb)
    }

    /**
     * Generates an Idempotency Key for an agent step
     */
    fun forStep(taskId: String, stepIndex: Int, toolName: String, argsJson: String): String {
        val composite = "$taskId:$stepIndex:$toolName:$argsJson"
        return generate(OPENDROID_NAMESPACE, composite).toString()
    }
}
