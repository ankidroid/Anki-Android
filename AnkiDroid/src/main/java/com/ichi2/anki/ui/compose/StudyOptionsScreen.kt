package com.ichi2.anki.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R

data class StudyOptionsData(
    val deckId: Long,
    val deckName: String,
    val deckDescription: String,
    val newCount: Int,
    val lrnCount: Int,
    val revCount: Int,
    val buriedNew: Int,
    val buriedLrn: Int,
    val buriedRev: Int,
    val totalNewCards: Int,
    val totalCards: Int,
    val isFiltered: Boolean,
    val haveBuried: Boolean,
)

@Composable
fun StudyOptionsScreen(
    studyOptionsData: StudyOptionsData?,
    modifier: Modifier = Modifier,
    onStartStudy: () -> Unit,
    onCustomStudy: (Long) -> Unit,
) {
    if (studyOptionsData == null) {
        // Show a loading indicator or an empty state
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    when {
        studyOptionsData.totalCards == 0 && !studyOptionsData.isFiltered -> {
            EmptyDeckView(studyOptionsData, modifier)
        }
        studyOptionsData.newCount + studyOptionsData.lrnCount + studyOptionsData.revCount == 0 -> {
            CongratsView(studyOptionsData, onCustomStudy, modifier)
        }
        else -> {
            StudyOptionsView(studyOptionsData, onStartStudy, modifier)
        }
    }
}

@Composable
fun StudyOptionsView(
    studyOptionsData: StudyOptionsData,
    onStartStudy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Text(
            text = studyOptionsData.deckName,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (studyOptionsData.deckDescription.isNotEmpty()) {
            Text(
                text = studyOptionsData.deckDescription,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            CountPill("New", studyOptionsData.newCount, MaterialTheme.colorScheme.primary) // TODO: Use string resource
            CountPill("Learning", studyOptionsData.lrnCount, MaterialTheme.colorScheme.error) // TODO: Use string resource
            CountPill("Review", studyOptionsData.revCount, Color(0xFF4CAF50)) // TODO: Use string resource
        }

        if (studyOptionsData.haveBuried) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Buried cards are not included in counts above.", // TODO: Use string resource
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                CountPill(
                    "New", // TODO: Use string resource
                    studyOptionsData.buriedNew,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                )
                CountPill("Learning", studyOptionsData.buriedLrn, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)) // TODO: Use string resource
                CountPill("Review", studyOptionsData.buriedRev, Color(0xFF4CAF50).copy(alpha = 0.5f)) // TODO: Use string resource
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Total New") // TODO: Use string resource
                Text(text = studyOptionsData.totalNewCards.toString(), style = MaterialTheme.typography.bodyLarge)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Total Cards") // TODO: Use string resource
                Text(text = studyOptionsData.totalCards.toString(), style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onStartStudy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.studyoptions_start))
        }
    }
}

@Composable
fun EmptyDeckView(
    studyOptionsData: StudyOptionsData,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = studyOptionsData.deckName,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.empty_deck),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun CongratsView(
    studyOptionsData: StudyOptionsData,
    onCustomStudy: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.studyoptions_congrats_finished),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!studyOptionsData.isFiltered) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { onCustomStudy(studyOptionsData.deckId) }) {
                Text(text = "Custom Study") // TODO: Use string resource
            }
        }
    }
}

@Composable
fun CountPill(
    label: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = count.toString(), style = MaterialTheme.typography.headlineSmall, color = color)
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

@Preview(showBackground = true)
@Composable
fun StudyOptionsScreenPreview() {
    val sampleData =
        StudyOptionsData(
            deckId = 1,
            deckName = "My Awesome Deck",
            deckDescription = "This is a great deck for learning Compose.",
            newCount = 10,
            lrnCount = 5,
            revCount = 20,
            buriedNew = 2,
            buriedLrn = 1,
            buriedRev = 3,
            totalNewCards = 50,
            totalCards = 200,
            isFiltered = false,
            haveBuried = true,
        )
    StudyOptionsScreen(studyOptionsData = sampleData, onStartStudy = {}, onCustomStudy = {})
}

@Preview(showBackground = true)
@Composable
fun CongratsViewPreview() {
    val sampleData =
        StudyOptionsData(
            deckId = 1,
            deckName = "My Awesome Deck",
            deckDescription = "",
            newCount = 0,
            lrnCount = 0,
            revCount = 0,
            buriedNew = 0,
            buriedLrn = 0,
            buriedRev = 0,
            totalNewCards = 0,
            totalCards = 100,
            isFiltered = false,
            haveBuried = false,
        )
    CongratsView(studyOptionsData = sampleData, onCustomStudy = {})
}

@Preview(showBackground = true)
@Composable
fun EmptyDeckViewPreview() {
    val sampleData =
        StudyOptionsData(
            deckId = 1,
            deckName = "My Awesome Deck",
            deckDescription = "",
            newCount = 0,
            lrnCount = 0,
            revCount = 0,
            buriedNew = 0,
            buriedLrn = 0,
            buriedRev = 0,
            totalNewCards = 0,
            totalCards = 0,
            isFiltered = false,
            haveBuried = false,
        )
    EmptyDeckView(studyOptionsData = sampleData)
}
