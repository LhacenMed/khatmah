package com.lhacenmed.khatmah.feature.quran.ui.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.ui.theme.HafsFamily
import com.lhacenmed.khatmah.core.ui.theme.WarshFamily
import com.lhacenmed.khatmah.feature.quran.data.Riwaya
import com.lhacenmed.khatmah.feature.quran.ui.home.QuranHomeViewModel

/** Max words of the resume verse shown as a taste of the position. */
private const val AYA_WORDS = 5

/**
 * The tab's hero: where reading stopped, and the single action that matters — reopening the
 * mushaf there. Everything on it is a label; the whole card leads to one button.
 */
@Composable
internal fun ResumeCard(resume: QuranHomeViewModel.Resume, onContinue: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        colors   = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text  = stringResource(R.string.quran_resume_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text       = stringResource(R.string.quran_sura_title, resume.suraName),
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text  = stringResource(R.string.quran_resume_meta, resume.page, resume.juz),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (resume.ayaText.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text      = truncateAya(resume.ayaText),
                    style     = TextStyle(
                        fontFamily    = when (resume.riwaya) {
                            Riwaya.WARSH -> WarshFamily
                            Riwaya.HAFS  -> HafsFamily
                        },
                        fontSize      = 22.sp,
                        lineHeight    = 38.sp,
                        textDirection = TextDirection.Rtl,
                    ),
                    textAlign = TextAlign.Center,
                    color     = MaterialTheme.colorScheme.primary,
                    maxLines  = 2,
                    overflow  = TextOverflow.Ellipsis,
                    modifier  = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick  = onContinue,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text       = stringResource(R.string.today_continue_reading),
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Returns at most [AYA_WORDS] space-separated words from [text], appending "…" if truncated. */
private fun truncateAya(text: String): String {
    val words = text.trim().split(' ')
    return if (words.size <= AYA_WORDS) text else words.take(AYA_WORDS).joinToString(" ") + "…"
}
