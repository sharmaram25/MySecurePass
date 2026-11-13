package com.mysecurepass.security

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import com.mysecurepass.core.vault.Kdf
import com.mysecurepass.core.vault.KdfParams

/** Argon2id (RFC 9106) via the native reference implementation. */
class Argon2Kdf : Kdf {
    private val argon2 by lazy { Argon2Kt() }

    override fun derive(password: ByteArray, params: KdfParams): ByteArray {
        val result = argon2.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = password,
            salt = params.salt,
            tCostInIterations = params.iterations,
            mCostInKibibyte = params.memoryKiB,
            parallelism = params.parallelism,
            hashLengthInBytes = 32,
        )
        return result.rawHashAsByteArray()
    }
}
