package com.darney.bubblewatch.cowork.input

import android.app.RemoteInput
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.wear.input.RemoteInputIntentHelper

private const val KEY_REPLY = "clawatch_reply"

/**
 * Returns a lambda that launches the Wear system input screen (voice dictation as
 * the primary option, plus keyboard/handwriting). The dictated/typed text is
 * delivered to [onResult]. Used for both "Reply" and "Add" (append) flows.
 */
@Composable
fun rememberVoiceInput(
    label: String,
    onResult: (String) -> Unit,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val bundle: Bundle? = RemoteInput.getResultsFromIntent(result.data)
        val text = bundle?.getCharSequence(KEY_REPLY)?.toString()
        if (!text.isNullOrBlank()) onResult(text)
    }

    return {
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        val remoteInputs = listOf(
            RemoteInput.Builder(KEY_REPLY).setLabel(label).build()
        )
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)
        launcher.launch(intent)
    }
}
