package com.example.forwarder

/**
 * Outcome of a single delivery attempt.
 *
 * [isRetryable] separates "the network was down" from "the address is wrong". Only retryable
 * failures go back on the queue; a permanent failure retried forever just burns battery.
 */
data class ForwardResult(
    val success: Boolean,
    val responseDetails: String? = null,
    val errorMessage: String? = null,
    val isRetryable: Boolean = false
)
