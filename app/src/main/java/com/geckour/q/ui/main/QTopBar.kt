package com.geckour.q.ui.main

import androidx.compose.material.BottomSheetScaffoldState
import androidx.compose.material.DrawerValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.ScaffoldState
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.painterResource
import com.geckour.q.R
import com.geckour.q.ui.compose.QTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun QTopBar(title: String, scaffoldState: BottomSheetScaffoldState, onToggleTheme: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = {
                if (scaffoldState.drawerState.currentValue == DrawerValue.Closed) {
                    coroutineScope.launch { scaffoldState.drawerState.open() }
                } else {
                    coroutineScope.launch { scaffoldState.drawerState.close() }
                }
            }) {
                Icon(Icons.Default.Menu, contentDescription = null)
            }
        },
        title = { Text(text = title) },
        actions = {
            IconButton(onClick = { /*TODO*/ }) {
                Icon(imageVector = Icons.Default.Search, contentDescription = null)
            }
            IconButton(onClick = onToggleTheme) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_daynight),
                    contentDescription = null
                )
            }
        },
        backgroundColor = QTheme.colors.colorPrimary,
        contentColor = QTheme.colors.colorTextPrimary
    )
}