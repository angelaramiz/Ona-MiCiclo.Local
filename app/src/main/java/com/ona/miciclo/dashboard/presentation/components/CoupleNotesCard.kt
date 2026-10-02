package com.ona.miciclo.dashboard.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ona.miciclo.calendar.domain.model.CoupleNote
import com.ona.miciclo.calendar.domain.model.CoupleNotes

private val PostItYellow = Color(0xFFFFF9C4)
private val PostItInk = Color(0xFF4E342E)
private val PostItMuted = Color(0xFF8D6E63)

/**
 * Tarjeta post-it de notitas en pareja: ver, enviar y borrar las propias.
 * Amarillo fijo + cursiva para que parezca papel, legible en ambos temas.
 */
@Composable
fun CoupleNotesCard(
    notes: List<CoupleNote>,
    draft: String,
    sending: Boolean,
    notesError: String?,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onDelete: (CoupleNote) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(rotationZ = -0.6f),
        colors = CardDefaults.cardColors(containerColor = PostItYellow),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📌 Notitas",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Cursive,
                color = PostItInk
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (notes.isEmpty()) {
                Text(
                    text = "Aún no hay notitas 💛 Escríbele algo bonito a tu pareja.",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Cursive,
                    color = PostItMuted
                )
            } else {
                notes.take(CoupleNotes.SHOWN).forEach { note ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = note.text,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Cursive,
                                color = PostItInk
                            )
                            Text(
                                text = "${CoupleNotes.senderLabel(note.isMine)} · " +
                                    CoupleNotes.timeAgoEs(
                                        System.currentTimeMillis(),
                                        note.createdAtMillis
                                    ),
                                style = MaterialTheme.typography.labelSmall,
                                color = PostItMuted
                            )
                        }
                        if (note.isMine) {
                            TextButton(onClick = { onDelete(note) }) {
                                Text(text = "✕", color = PostItMuted)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = "Escríbele algo bonito…",
                            fontFamily = FontFamily.Cursive,
                            color = PostItMuted
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PostItInk,
                        unfocusedTextColor = PostItInk,
                        cursorColor = PostItInk,
                        focusedBorderColor = PostItMuted,
                        unfocusedBorderColor = PostItMuted
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onSend,
                    enabled = !sending && CoupleNotes.isValid(draft)
                ) {
                    Text(
                        text = if (sending) "…" else "Enviar 💌",
                        fontFamily = FontFamily.Cursive,
                        color = PostItInk
                    )
                }
            }
            Text(
                text = "${draft.trim().length}/${CoupleNotes.MAX_LEN}",
                style = MaterialTheme.typography.labelSmall,
                color = PostItMuted
            )

            notesError?.let { err ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
