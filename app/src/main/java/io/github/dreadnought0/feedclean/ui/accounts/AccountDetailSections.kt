package io.github.dreadnought0.feedclean.ui.accounts

import io.github.dreadnought0.feedclean.ui.theme.Glass
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.data.model.Account
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.ui.stats.SectionNote
import io.github.dreadnought0.feedclean.ui.stats.StatsSectionCard
import io.github.dreadnought0.feedclean.ui.stats.resolve

/** Status buttons (local records only, as the note says). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountStatusCard(state: AccountDetailUiState, actions: AccountDetailActions, modifier: Modifier = Modifier) {
    StatsSectionCard(title = stringResource(R.string.screens_detail_status_title), modifier = modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.statusActions.forEach { status ->
                OutlinedButton(onClick = { actions.setStatus(status) }) { Text(stringResource(statusActionLabel(status))) }
            }
        }
        SectionNote(stringResource(R.string.screens_detail_status_note))
    }
}

private fun statusActionLabel(status: AccountStatus): Int = when (status) {
    AccountStatus.FALSE_POSITIVE -> R.string.screens_detail_mark_false_positive
    AccountStatus.SEEN -> R.string.screens_detail_mark_seen
    AccountStatus.UNBLOCKED_SUSPECTED -> R.string.screens_detail_mark_unblocked
    AccountStatus.BLOCKED -> R.string.screens_status_blocked
}

/** Detected tags (read-only), the user's manual tags (removable), an add field and one-tap suggestions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountTagsCard(tags: AccountTagGroups, actions: AccountDetailActions, modifier: Modifier = Modifier) {
    StatsSectionCard(title = stringResource(R.string.screens_detail_tags_title), modifier = modifier) {
        Subheading(stringResource(R.string.screens_detail_tags_detected))
        if (tags.autoDetected.isEmpty()) {
            SectionNote(stringResource(R.string.screens_detail_tags_detected_none))
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.autoDetected.forEach { DetectedTagLabel(tagLabel(it).resolve()) }
            }
        }

        Subheading(stringResource(R.string.screens_detail_tags_manual))
        if (tags.manual.isEmpty()) {
            SectionNote(stringResource(R.string.screens_detail_tags_manual_none))
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.manual.forEach { name ->
                    val label = tagLabel(name).resolve()
                    InputChip(
                        selected = false,
                        onClick = { actions.removeManualTag(name) },
                        label = { Text(label) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.screens_detail_tag_remove, label),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }

        AddTagField(onAdd = actions::addManualTag)

        if (tags.suggestions.isNotEmpty()) {
            Subheading(stringResource(R.string.screens_detail_tag_suggestions))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.suggestions.forEach { name ->
                    SuggestionChip(
                        onClick = { actions.addManualTag(name) },
                        label = { Text(tagLabel(name).resolve()) },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddTagField(onAdd: (String) -> Boolean) {
    var text by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val submit = {
        if (onAdd(text)) {
            text = ""
            invalid = false
        } else {
            invalid = true
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                invalid = false
            },
            singleLine = true,
            label = { Text(stringResource(R.string.screens_detail_tag_add_label)) },
            isError = invalid,
            supportingText = if (invalid) {
                { Text(stringResource(R.string.screens_detail_tag_invalid, MAX_TAG_LENGTH)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        Button(onClick = submit, enabled = text.isNotBlank()) { Text(stringResource(R.string.screens_detail_tag_add)) }
    }
}

/** Private notes editor; Save is enabled once the text differs from what is stored. */
@Composable
fun AccountNotesCard(account: Account, onSave: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable(account.id) { mutableStateOf(account.notes.orEmpty()) }
    StatsSectionCard(title = stringResource(R.string.screens_detail_notes_title), modifier = modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.screens_detail_notes_label)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onSave(text) }, enabled = normalizeNotes(text) != account.notes) {
            Text(stringResource(R.string.screens_detail_notes_save))
        }
    }
}

/** The user-set "suspected impersonator of" link, with pick / change / clear. */
@Composable
fun SuspectedOriginalCard(original: AccountRef?, actions: AccountDetailActions, modifier: Modifier = Modifier) {
    StatsSectionCard(title = stringResource(R.string.screens_detail_original_title), modifier = modifier) {
        if (original == null) {
            SectionNote(stringResource(R.string.screens_detail_original_none))
            OutlinedButton(onClick = actions::openPicker) { Text(stringResource(R.string.screens_detail_original_pick)) }
        } else {
            val who = accountTitle(original.displayName, original.handle) + stringResource(R.string.screens_separator) +
                identityLine(original.identity, original.handle)
            Text(stringResource(R.string.screens_detail_original_of, who), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = actions::openPicker) { Text(stringResource(R.string.screens_detail_original_change)) }
                TextButton(onClick = actions::clearOriginal) { Text(stringResource(R.string.screens_detail_original_clear)) }
            }
        }
        SectionNote(stringResource(R.string.screens_detail_original_note))
    }
}

@Composable
private fun Subheading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { heading() },
    )
}

/** A read-only outlined label for an auto-detected tag. */
@Composable
private fun DetectedTagLabel(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = Glass.Nest,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
