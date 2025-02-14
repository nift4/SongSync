package pl.lambada.songsync.ui.components.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pl.lambada.songsync.R

@Composable
fun NoInternetDialog(onClick: () -> Unit) {
    AlertDialog(onDismissRequest = { /* don't dismiss */ }, confirmButton = {
        Button(
            onClick
        ) {
            Text(stringResource(R.string.close_app))
        }
    }, title = { Text(stringResource(R.string.no_internet_connection)) }, text = {
        Column {
            Text(stringResource(R.string.you_need_internet_connection))
            Text(stringResource(R.string.check_your_connection))
            Text(stringResource(R.string.if_connected_spotify_down))
        }
    })
}