package com.geckour.q.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.domain.model.Genre
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.getTimeString

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun Genres(navController: NavController, genres: List<Genre>) {
    val db = DB.getInstance(LocalContext.current)

    LazyColumn {
        items(genres) { genre ->
            val tracks by db.trackDao().getAllByGenreNameAsync(genre.name)
                .collectAsState(emptyList())
            Card(
                shape = RectangleShape,
                backgroundColor = QTheme.colors.colorBackground,
                elevation = 0.dp,
                onClick = { navController.navigate(route = "tracks?genreName=${genre.name}") }
            ) {
                Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                    AsyncImage(
                        model = genre.thumb,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .fillMaxWidth()
                            .aspectRatio(1f / 4)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = genre.name,
                            fontSize = 20.sp,
                            color = QTheme.colors.colorTextPrimary
                        )
                        Text(
                            text = genre.totalDuration.getTimeString(),
                            fontSize = 12.sp,
                            color = QTheme.colors.colorTextPrimary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        IconButton(
                            onClick = { /*TODO*/ },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_option),
                                contentDescription = null,
                                tint = QTheme.colors.colorTextPrimary
                            )
                        }
                    }
                    Divider()
                }
            }
        }
    }
}